package com.example.ledger.domain.repository;

import com.example.ledger.domain.enums.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.*;

@Repository
@RequiredArgsConstructor
public class AccountLockRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public record LockedAccount(UUID id, AccountType type, AccountStatus status, boolean nonNegative) {}

    public List<LockedAccount> lockAll(UUID ledgerId, Collection<UUID> ids) {
        // PostgreSQL UUID order is used consistently on every path; no Java signed-UUID ordering.
        return jdbc.query("""
                SELECT id, type, status, enforce_non_negative_balance FROM ledger.account
                WHERE ledger_id = :ledger AND id IN (:ids) ORDER BY id FOR UPDATE
                """, Map.of("ledger", ledgerId, "ids", ids),
                (rs, row) -> new LockedAccount(rs.getObject("id", UUID.class),
                        AccountType.valueOf(rs.getString("type")), AccountStatus.valueOf(rs.getString("status")),
                        rs.getBoolean("enforce_non_negative_balance")));
    }

    public void close(UUID ledgerId, UUID id) {
        jdbc.update("UPDATE ledger.account SET status = 'CLOSED' WHERE ledger_id = :ledger AND id = :id",
                Map.of("ledger", ledgerId, "id", id));
    }
}
