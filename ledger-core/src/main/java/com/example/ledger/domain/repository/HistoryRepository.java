package com.example.ledger.domain.repository;

import com.example.ledger.domain.enums.Side;
import com.example.ledger.domain.valueobject.Money;
import com.example.ledger.dto.response.HistoryEntryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

@Repository
@RequiredArgsConstructor
public class HistoryRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public List<HistoryEntryResponse> find(UUID ledgerId, UUID accountId, Instant from, Instant to, int page, int size) {
        var params = new MapSqlParameterSource("ledger", ledgerId).addValue("account", accountId)
                .addValue("limit", size + 1).addValue("offset", (long) page * size);
        var filter = new StringBuilder();
        if (from != null) {
            filter.append(" AND t.recorded_at >= :from");
            params.addValue("from", Timestamp.from(from));
        }
        if (to != null) {
            filter.append(" AND t.recorded_at < :to");
            params.addValue("to", Timestamp.from(to));
        }
        return jdbc.query("""
                SELECT e.id, e.transaction_id, e.line_number, e.side, e.amount, e.description,
                       t.recorded_at, t.reversal_of_transaction_id
                FROM ledger.journal_entry e JOIN ledger.journal_transaction t ON t.id = e.transaction_id
                WHERE e.ledger_id = :ledger AND e.account_id = :account
                """ + filter + " ORDER BY t.recorded_at, t.id, e.line_number LIMIT :limit OFFSET :offset", params,
                (rs, row) -> new HistoryEntryResponse(rs.getObject("id", UUID.class),
                        rs.getObject("transaction_id", UUID.class), rs.getInt("line_number"),
                        Side.valueOf(rs.getString("side")), Money.format(rs.getBigDecimal("amount")),
                        rs.getString("description"), rs.getTimestamp("recorded_at").toInstant(),
                        rs.getObject("reversal_of_transaction_id", UUID.class)));
    }
}
