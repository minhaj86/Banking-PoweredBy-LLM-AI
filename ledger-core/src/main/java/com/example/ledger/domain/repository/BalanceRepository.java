package com.example.ledger.domain.repository;

import com.example.ledger.domain.enums.AccountType;
import com.example.ledger.domain.valueobject.Money;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.util.*;

@Repository
@RequiredArgsConstructor
public class BalanceRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public record Totals(UUID accountId, String code, String name, AccountType type,
                         BigDecimal debits, BigDecimal credits) {
        public BigDecimal normalBalance() { return type.balance(debits, credits); }
        public BigDecimal debitBalance() { return debits.subtract(credits).max(Money.ZERO); }
        public BigDecimal creditBalance() { return credits.subtract(debits).max(Money.ZERO); }
    }

    private static final String TOTALS = """
            SELECT a.id, a.code, a.name, a.type,
                   coalesce(sum(e.amount) FILTER (WHERE e.side = 'DEBIT'), 0) AS debits,
                   coalesce(sum(e.amount) FILTER (WHERE e.side = 'CREDIT'), 0) AS credits
            FROM ledger.account a LEFT JOIN ledger.journal_entry e ON e.account_id = a.id
            WHERE a.ledger_id = :ledger
            """;
    private static final String GROUP = " GROUP BY a.id, a.code, a.name, a.type ORDER BY a.code, a.id";

    public List<Totals> forAccounts(UUID ledgerId, Collection<UUID> ids) {
        return jdbc.query(TOTALS + " AND a.id IN (:ids)" + GROUP, Map.of("ledger", ledgerId, "ids", ids), this::map);
    }

    public List<Totals> forLedger(UUID ledgerId) {
        // A single statement snapshot includes zero-balance accounts and all report figures.
        return jdbc.query(TOTALS + GROUP, Map.of("ledger", ledgerId), this::map);
    }

    private Totals map(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new Totals(rs.getObject("id", UUID.class), rs.getString("code"), rs.getString("name"),
                AccountType.valueOf(rs.getString("type")), rs.getBigDecimal("debits"), rs.getBigDecimal("credits"));
    }
}
