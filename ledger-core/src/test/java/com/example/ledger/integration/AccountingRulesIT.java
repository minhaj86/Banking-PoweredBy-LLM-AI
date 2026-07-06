package com.example.ledger.integration;

import com.example.ledger.domain.enums.*;
import com.example.ledger.dto.request.*;
import com.example.ledger.exception.LedgerException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class AccountingRulesIT extends PostgresIntegrationTest {
    @Test
    void emptyAccountsReturnZerosAndAnEmptyHistory() {
        var ledger = ledger();
        var account = account(ledger.id(), AccountType.EXPENSE, false);
        var balance = reports.balance(ledger.id(), account.id());
        assertThat(balance.balance()).isEqualTo("0.00");
        assertThat(balance.totalCredits()).isEqualTo("0.00");
        assertThat(balance.totalDebits()).isEqualTo("0.00");
        assertThat(reports.history(ledger.id(), account.id(), null, null, 0, 50).entries()).isEmpty();
        assertThat(reports.trialBalance(ledger.id()).accounts()).hasSize(1);
        assertThat(reports.trialBalance(ledger().id()).accounts()).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(AccountType.class)
    void returnsCorrectNormalSignForEveryType(AccountType type) {
        var ledger = ledger();
        var a = account(ledger.id(), type, false);
        var b = account(ledger.id(), AccountType.ASSET, false);
        post(ledger.id(), transfer(a.id(), b.id(), "10"));
        balance(ledger.id(), a.id(), type.normalSide() == Side.DEBIT ? "10.00" : "-10.00");
        assertThat(reports.balance(ledger.id(), a.id()).normalBalanceSide()).isEqualTo(type.normalSide());
    }

    @Test
    void aggregatesRepeatedAccountLinesBeforeEnforcingNonNegativeBalance() {
        var ledger = ledger();
        var a = account(ledger.id(), AccountType.LIABILITY, true);
        var b = account(ledger.id(), AccountType.ASSET, true);
        post(ledger.id(), new PostTransactionRequest(null, "Net zero compound", List.of(
                entry(a.id(), Side.DEBIT, "10"), entry(a.id(), Side.CREDIT, "10"),
                entry(b.id(), Side.DEBIT, "10"), entry(b.id(), Side.CREDIT, "10"))));
        balance(ledger.id(), a.id(), "0.00");
        balance(ledger.id(), b.id(), "0.00");
    }

    @Test
    void rejectsUnknownWrongLedgerClosedAndUnbalancedAccountsWithoutPartialRecords() {
        var ledger = ledger();
        var a = account(ledger.id(), AccountType.ASSET, false);
        var b = account(ledger.id(), AccountType.LIABILITY, false);
        var other = account(ledger().id(), AccountType.LIABILITY, false);
        assertThatThrownBy(() -> post(ledger.id(), transfer(a.id(), UUID.randomUUID(), "1")))
                .isInstanceOf(LedgerException.class).hasMessageContaining("not found");
        assertThatThrownBy(() -> post(ledger.id(), transfer(a.id(), other.id(), "1")))
                .isInstanceOf(LedgerException.class).hasMessageContaining("not found");
        assertThatThrownBy(() -> post(ledger.id(), new PostTransactionRequest(null, "Unbalanced", List.of(
                entry(a.id(), Side.DEBIT, "2"), entry(b.id(), Side.CREDIT, "1")))))
                .isInstanceOf(LedgerException.class).hasMessageContaining("Total debits");
        accounts.close(ledger.id(), b.id());
        assertThatThrownBy(() -> post(ledger.id(), transfer(a.id(), b.id(), "1")))
                .isInstanceOf(LedgerException.class).hasMessageContaining("Closed accounts");
        assertThat(transactions(ledger.id())).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ledger.idempotency_record WHERE ledger_id = ?", Long.class, ledger.id())).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "0.001", "1000000000000", "NaN", "Infinity", "1e3", "word"})
    void invalidMoneyNeverReachesJournal(String value) {
        var ledger = ledger();
        var a = account(ledger.id(), AccountType.ASSET, false);
        var b = account(ledger.id(), AccountType.LIABILITY, false);
        assertThatThrownBy(() -> post(ledger.id(), transfer(a.id(), b.id(), value))).isInstanceOf(LedgerException.class);
        assertThat(transactions(ledger.id())).isZero();
    }

    @Test
    void acceptsMaximumAmountAndKeepsHistoryBoundsAndOrdering() {
        var ledger = ledger();
        var a = account(ledger.id(), AccountType.ASSET, false);
        var b = account(ledger.id(), AccountType.LIABILITY, false);
        var tx = post(ledger.id(), transfer(a.id(), b.id(), "999999999999.99"));
        var next = post(ledger.id(), transfer(a.id(), b.id(), "0.01"));
        balance(ledger.id(), a.id(), "1000000000000.00");
        var first = reports.history(ledger.id(), a.id(), tx.recordedAt(), null, 0, 1);
        assertThat(first.hasNext()).isTrue();
        assertThat(first.entries().getFirst().transactionId()).isEqualTo(tx.id());
        assertThat(reports.history(ledger.id(), a.id(), tx.recordedAt(), next.recordedAt(), 0, 20).entries()).hasSize(1);
        assertThat(reports.history(ledger.id(), a.id(), null, tx.recordedAt(), 0, 20).entries()).isEmpty();
        assertThat(reports.history(ledger.id(), a.id(), null, null, 1, 1).entries().getFirst().transactionId()).isEqualTo(next.id());
        assertThatThrownBy(() -> reports.history(ledger.id(), a.id(), next.recordedAt(), tx.recordedAt(), 0, 20))
                .isInstanceOf(LedgerException.class);
        assertThatThrownBy(() -> reports.history(ledger.id(), a.id(), null, null, 0, 201)).isInstanceOf(LedgerException.class);
    }

    @Test
    void closureRequiresZeroAndRetainsHistory() {
        var ledger = ledger();
        var a = account(ledger.id(), AccountType.ASSET, true);
        var b = account(ledger.id(), AccountType.LIABILITY, true);
        var tx = post(ledger.id(), transfer(a.id(), b.id(), "10"));
        assertThatThrownBy(() -> accounts.close(ledger.id(), a.id())).isInstanceOf(LedgerException.class).hasMessageContaining("zero");
        journals.reverse(ledger.id(), tx.id(), "reverse", new ReversalRequest("Return deposit"));
        assertThat(accounts.close(ledger.id(), a.id()).status()).isEqualTo(AccountStatus.CLOSED);
        assertThat(accounts.close(ledger.id(), a.id()).status()).isEqualTo(AccountStatus.CLOSED);
        assertThat(reports.history(ledger.id(), a.id(), null, null, 0, 20).entries()).hasSize(2);
    }
}
