package com.example.ledger.service.impl;

import com.example.ledger.domain.repository.*;
import com.example.ledger.domain.valueobject.Money;
import com.example.ledger.dto.response.*;
import com.example.ledger.exception.LedgerException;
import com.example.ledger.service.ReportingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportingServiceImpl implements ReportingService {
    private final LedgerLookup lookup;
    private final BalanceRepository balances;
    private final HistoryRepository history;

    @Override
    public BalanceResponse balance(UUID ledgerId, UUID accountId) {
        var ledger = lookup.ledger(ledgerId);
        lookup.account(ledgerId, accountId);
        var total = balances.forAccounts(ledgerId, List.of(accountId)).getFirst();
        return new BalanceResponse(accountId, ledger.getCurrency(), total.type().normalSide(),
                Money.format(total.debits()), Money.format(total.credits()), Money.format(total.normalBalance()));
    }

    @Override
    public HistoryResponse history(UUID ledgerId, UUID accountId, Instant from, Instant to, int page, int size) {
        if (page < 0 || page > 100000 || size < 1 || size > 200 || from != null && to != null && !from.isBefore(to)) {
            throw new LedgerException("INVALID_HISTORY_RANGE", HttpStatus.BAD_REQUEST,
                    "page must be 0..100000, size 1..200, and from must be earlier than to (inclusive from, exclusive to)");
        }
        var ledger = lookup.ledger(ledgerId);
        lookup.account(ledgerId, accountId);
        var rows = history.find(ledgerId, accountId, from, to, page, size);
        return new HistoryResponse(accountId, ledger.getCurrency(), page, size, rows.size() > size,
                rows.stream().limit(size).toList());
    }

    @Override
    public TrialBalanceResponse trialBalance(UUID ledgerId) {
        var ledger = lookup.ledger(ledgerId);
        var totals = balances.forLedger(ledgerId);
        return new TrialBalanceResponse(ledgerId, ledger.getCurrency(), totals.stream().map(t ->
                new TrialBalanceResponse.Row(t.accountId(), t.code(), t.name(), t.type(), t.type().normalSide(),
                        Money.format(t.debits()), Money.format(t.credits()), Money.format(t.normalBalance()),
                        Money.format(t.debitBalance()), Money.format(t.creditBalance()))).toList(),
                sum(totals, BalanceRepository.Totals::debits), sum(totals, BalanceRepository.Totals::credits),
                sum(totals, BalanceRepository.Totals::debitBalance), sum(totals, BalanceRepository.Totals::creditBalance));
    }

    private String sum(List<BalanceRepository.Totals> rows, Function<BalanceRepository.Totals, BigDecimal> value) {
        return Money.format(rows.stream().map(value).reduce(Money.ZERO, BigDecimal::add));
    }
}
