package com.example.ledger.domain.repository;

import com.example.ledger.domain.entity.IdempotencyRecord;
import com.example.ledger.domain.enums.OperationType;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class IdempotencyRepository {
    private final JdbcTemplate jdbc;

    public boolean claim(UUID ledgerId, OperationType operation, String key, String fingerprint, UUID transactionId) {
        return jdbc.update("""
                INSERT INTO ledger.idempotency_record (ledger_id, operation, idempotency_key, fingerprint, transaction_id)
                VALUES (?, ?, ?, ?, ?) ON CONFLICT (ledger_id, operation, idempotency_key) DO NOTHING
                """, ledgerId, operation.name(), key, fingerprint, transactionId) == 1;
    }

    public IdempotencyRecord find(UUID ledgerId, OperationType operation, String key) {
        // Must be a separate statement: INSERT ON CONFLICT may have waited for another commit.
        return jdbc.queryForObject("""
                SELECT fingerprint, transaction_id, response_status, created_at FROM ledger.idempotency_record
                WHERE ledger_id = ? AND operation = ? AND idempotency_key = ?
                """, (rs, row) -> new IdempotencyRecord(rs.getString("fingerprint"),
                        rs.getObject("transaction_id", UUID.class), rs.getInt("response_status"),
                        rs.getTimestamp("created_at").toInstant()), ledgerId, operation.name(), key);
    }
}
