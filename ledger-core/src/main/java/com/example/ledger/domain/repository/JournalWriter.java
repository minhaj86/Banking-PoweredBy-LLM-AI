package com.example.ledger.domain.repository;

import com.example.ledger.domain.valueobject.Posting;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JournalWriter {
    private final JdbcTemplate jdbc;

    public void write(UUID id, UUID ledgerId, Posting posting, UUID reversalOf, String reason) {
        jdbc.update("""
                INSERT INTO ledger.journal_transaction
                    (id, ledger_id, external_reference, description, reversal_of_transaction_id, reversal_reason)
                VALUES (?, ?, ?, ?, ?, ?)
                """, id, ledgerId, posting.externalReference(), posting.description(), reversalOf, reason);
        for (int index = 0; index < posting.entries().size(); index++) {
            var entry = posting.entries().get(index);
            jdbc.update("""
                    INSERT INTO ledger.journal_entry
                        (id, ledger_id, transaction_id, account_id, line_number, side, amount, description)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """, UUID.randomUUID(), ledgerId, id, entry.accountId(), index + 1, entry.side().name(),
                    entry.amount().value(), entry.description());
        }
        jdbc.update("UPDATE ledger.journal_transaction SET state = 'POSTED' WHERE id = ?", id);
    }

    public boolean hasReversal(UUID ledgerId, UUID originalId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM ledger.journal_transaction
                    WHERE ledger_id = ? AND reversal_of_transaction_id = ?)
                """, Boolean.class, ledgerId, originalId));
    }
}
