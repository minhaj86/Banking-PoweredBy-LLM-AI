package com.example.ledger.service.impl;

import com.example.ledger.domain.enums.*;
import com.example.ledger.domain.repository.*;
import com.example.ledger.domain.valueobject.*;
import com.example.ledger.exception.LedgerException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class PostingRules {
    private final AccountLockRepository locks;
    private final BalanceRepository balances;

    public List<AccountLockRepository.LockedAccount> lockAccounts(UUID ledgerId, Posting posting) {
        var ids = posting.entries().stream().map(PostingLine::accountId).distinct().toList();
        var locked = locks.lockAll(ledgerId, ids);
        if (locked.size() != ids.size()) {
            throw LedgerException.missing("One or more accounts");
        }
        return locked;
    }

    public void validateFinalBalances(UUID ledgerId, Posting posting, List<AccountLockRepository.LockedAccount> accounts) {
        if (accounts.stream().anyMatch(a -> a.status() != AccountStatus.ACTIVE)) {
            throw LedgerException.invalid("ACCOUNT_CLOSED", "Closed accounts cannot receive postings, including reversals");
        }
        // Separate SQL statement AFTER all account locks. Never combine the lock and balance read.
        var totals = balances.forAccounts(ledgerId, accounts.stream().map(AccountLockRepository.LockedAccount::id).toList())
                .stream().collect(Collectors.toMap(BalanceRepository.Totals::accountId, Function.identity()));
        var debitDeltas = new HashMap<UUID, BigDecimal>();
        posting.entries().forEach(e -> debitDeltas.merge(e.accountId(),
                e.side() == Side.DEBIT ? e.amount().value() : e.amount().value().negate(), BigDecimal::add));
        for (var account : accounts) {
            var delta = debitDeltas.get(account.id());
            if (account.type().normalSide() == Side.CREDIT) { delta = delta.negate(); }
            if (account.nonNegative() && totals.get(account.id()).normalBalance().add(delta).signum() < 0) {
                throw LedgerException.invalid("NEGATIVE_BALANCE", "Posting would make a protected account's normal balance negative");
            }
        }
    }
}
