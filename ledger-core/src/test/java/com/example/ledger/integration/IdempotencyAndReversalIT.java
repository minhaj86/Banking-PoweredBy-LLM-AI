package com.example.ledger.integration;

import com.example.ledger.domain.enums.*;
import com.example.ledger.dto.request.*;
import com.example.ledger.dto.response.TransactionResponse;
import com.example.ledger.exception.LedgerException;
import org.junit.jupiter.api.Test;
import java.net.SocketTimeoutException;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class IdempotencyAndReversalIT extends PostgresIntegrationTest {
    @Test
    void sequentialReplayNormalizesMoneyButRejectsDifferentFieldsAndOrdering() {
        var ledger = ledger();
        var a = account(ledger.id(), AccountType.ASSET, false);
        var b = account(ledger.id(), AccountType.LIABILITY, true);
        var request = transfer(a.id(), b.id(), "100");
        var first = journals.post(ledger.id(), "deposit", request);
        assertThat(journals.post(ledger.id(), "deposit", transfer(a.id(), b.id(), "0100.000"))).isEqualTo(first);
        assertThatThrownBy(() -> journals.post(ledger.id(), "deposit", transfer(a.id(), b.id(), "101")))
                .isInstanceOfSatisfying(LedgerException.class, e -> assertThat(e.getCode()).isEqualTo("IDEMPOTENCY_CONFLICT"));
        assertThatThrownBy(() -> journals.post(ledger.id(), "deposit", new PostTransactionRequest(null, "Transfer",
                request.entries().reversed()))).isInstanceOf(LedgerException.class);
        assertThat(transactions(ledger.id())).isEqualTo(1);
    }

    @Test
    void retriesLostResponseAfterCommit() {
        var ledger = ledger();
        var a = account(ledger.id(), AccountType.ASSET, false);
        var b = account(ledger.id(), AccountType.LIABILITY, false);
        var request = transfer(a.id(), b.id(), "10");
        assertThatThrownBy(() -> postThenLoseResponse(ledger.id(), request)).isInstanceOf(SocketTimeoutException.class);
        var recovered = journals.post(ledger.id(), "lost-response", request);
        assertThat(transactions(ledger.id())).isEqualTo(1);
        assertThat(recovered).isEqualTo(journals.get(ledger.id(), recovered.id()));
        balance(ledger.id(), b.id(), "10.00");
    }

    private TransactionResponse postThenLoseResponse(UUID ledgerId, PostTransactionRequest request) throws SocketTimeoutException {
        journals.post(ledgerId, "lost-response", request); // Fully committed before the simulated transport failure.
        throw new SocketTimeoutException("Simulated lost HTTP response");
    }

    @Test
    void reversesFeeKeepsBothHistoriesAndScopesKeyByOperation() {
        var ledger = ledger();
        var cash = account(ledger.id(), AccountType.ASSET, false);
        var customer = account(ledger.id(), AccountType.LIABILITY, true);
        var revenue = account(ledger.id(), AccountType.REVENUE, false);
        post(ledger.id(), transfer(cash.id(), customer.id(), "1000"));
        var fee = journals.post(ledger.id(), "fee", transfer(customer.id(), revenue.id(), "10"));
        balance(ledger.id(), customer.id(), "990.00");
        var reversal = journals.reverse(ledger.id(), fee.id(), "fee", new ReversalRequest("Incorrect fee"));
        assertThat(reversal.reversalOfTransactionId()).isEqualTo(fee.id());
        assertThat(reversal.reversalReason()).isEqualTo("Incorrect fee");
        assertThat(journals.get(ledger.id(), fee.id())).isEqualTo(fee);
        assertThat(reversal.entries()).extracting(e -> e.side()).containsExactly(Side.CREDIT, Side.DEBIT);
        balance(ledger.id(), customer.id(), "1000.00");
        balance(ledger.id(), revenue.id(), "0.00");
        assertThat(reports.history(ledger.id(), revenue.id(), null, null, 0, 20).entries()).hasSize(2);
        assertThat(journals.reverse(ledger.id(), fee.id(), "fee", new ReversalRequest("Incorrect fee"))).isEqualTo(reversal);
        assertThatThrownBy(() -> journals.reverse(ledger.id(), fee.id(), "new-key", new ReversalRequest("Again")))
                .isInstanceOfSatisfying(LedgerException.class, e -> assertThat(e.getCode()).isEqualTo("ALREADY_REVERSED"));
        assertThatThrownBy(() -> journals.reverse(ledger.id(), reversal.id(), "other", new ReversalRequest("Undo correction")))
                .isInstanceOfSatisfying(LedgerException.class, e -> assertThat(e.getCode()).isEqualTo("REVERSAL_OF_REVERSAL"));
        assertThatThrownBy(() -> journals.reverse(ledger.id(), fee.id(), "fee", new ReversalRequest("Different reason")))
                .isInstanceOfSatisfying(LedgerException.class, e -> assertThat(e.getCode()).isEqualTo("IDEMPOTENCY_CONFLICT"));
    }

    @Test
    void reversalFingerprintIncludesTarget() {
        var ledger = ledger();
        var a = account(ledger.id(), AccountType.ASSET, false);
        var b = account(ledger.id(), AccountType.LIABILITY, false);
        var one = post(ledger.id(), transfer(a.id(), b.id(), "1"));
        var two = post(ledger.id(), transfer(a.id(), b.id(), "1"));
        journals.reverse(ledger.id(), one.id(), "same-key", new ReversalRequest("Correction"));
        assertThatThrownBy(() -> journals.reverse(ledger.id(), two.id(), "same-key", new ReversalRequest("Correction")))
                .isInstanceOfSatisfying(LedgerException.class, e -> assertThat(e.getCode()).isEqualTo("IDEMPOTENCY_CONFLICT"));
    }

    @Test
    void reversalCannotOverdrawOrUseClosedAccounts() {
        var ledger = ledger();
        var cash = account(ledger.id(), AccountType.ASSET, false);
        var source = account(ledger.id(), AccountType.LIABILITY, true);
        var target = account(ledger.id(), AccountType.LIABILITY, true);
        var deposit = post(ledger.id(), transfer(cash.id(), source.id(), "100"));
        post(ledger.id(), transfer(source.id(), target.id(), "80"));
        assertThatThrownBy(() -> journals.reverse(ledger.id(), deposit.id(), "overdraw", new ReversalRequest("Refund")))
                .isInstanceOfSatisfying(LedgerException.class, e -> assertThat(e.getCode()).isEqualTo("NEGATIVE_BALANCE"));
        var neutral = post(ledger.id(), new PostTransactionRequest(null, "Neutral", List.of(
                entry(cash.id(), Side.DEBIT, "1"), entry(cash.id(), Side.CREDIT, "1"),
                entry(source.id(), Side.DEBIT, "1"), entry(source.id(), Side.CREDIT, "1"))));
        // Return the deposit through source, then close cash; neutral transaction is still an unreversed original.
        post(ledger.id(), transfer(target.id(), source.id(), "80"));
        journals.reverse(ledger.id(), deposit.id(), "refund", new ReversalRequest("Refund after funds returned"));
        accounts.close(ledger.id(), cash.id());
        assertThatThrownBy(() -> journals.reverse(ledger.id(), neutral.id(), "closed", new ReversalRequest("Neutral correction")))
                .isInstanceOfSatisfying(LedgerException.class, e -> assertThat(e.getCode()).isEqualTo("ACCOUNT_CLOSED"));
    }
}
