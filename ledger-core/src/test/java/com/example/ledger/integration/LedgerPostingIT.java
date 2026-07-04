package com.example.ledger.integration;

import com.example.ledger.domain.enums.*;
import com.example.ledger.dto.request.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class LedgerPostingIT extends PostgresIntegrationTest {
    @Test
    void postsCompoundJournalAndReadsAggregatesUsingRestrictedRuntimeRole() {
        var ledger = ledger();
        var cash = account(ledger.id(), AccountType.ASSET, false);
        var a = account(ledger.id(), AccountType.LIABILITY, true);
        var b = account(ledger.id(), AccountType.LIABILITY, true);
        var tx = post(ledger.id(), new PostTransactionRequest("deposit", "Compound deposit", List.of(
                entry(cash.id(), Side.DEBIT, "100"), entry(a.id(), Side.CREDIT, "60"),
                entry(b.id(), Side.CREDIT, "40"))));
        assertThat(tx.entries()).hasSize(3);
        assertThat(tx.entries()).extracting(e -> e.lineNumber()).containsExactly(1, 2, 3);
        balance(ledger.id(), cash.id(), "100.00");
        balance(ledger.id(), a.id(), "60.00");
        balance(ledger.id(), b.id(), "40.00");
        assertThat(journals.get(ledger.id(), tx.id())).isEqualTo(tx);
        assertThat(reports.trialBalance(ledger.id()).totalDebits()).isEqualTo("100.00");
        assertThat(jdbc.queryForObject("SELECT rolsuper FROM pg_roles WHERE rolname = current_user", Boolean.class)).isFalse();
    }
}
