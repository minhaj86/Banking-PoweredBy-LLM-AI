package com.example.ledger.mapper;

import com.example.ledger.domain.entity.*;
import com.example.ledger.domain.valueobject.*;
import com.example.ledger.dto.request.PostTransactionRequest;
import com.example.ledger.dto.response.*;
import org.springframework.stereotype.Component;

@Component
public class LedgerMapper {
    public LedgerResponse ledger(Ledger ledger) {
        return new LedgerResponse(ledger.getId(), ledger.getCode(), ledger.getName(), ledger.getCurrency(), ledger.getCreatedAt());
    }

    public AccountResponse account(Account account) {
        return new AccountResponse(account.getId(), account.getLedgerId(), account.getCode(), account.getName(),
                account.getType(), account.getStatus(), account.getType().normalSide(),
                account.isEnforceNonNegativeBalance(), account.getCreatedAt(), account.getExternalReference());
    }

    public Posting posting(PostTransactionRequest request) {
        return new Posting(request.externalReference(), request.description(), request.entries().stream()
                .map(e -> new PostingLine(e.accountId(), e.side(), Money.parse(e.amount()), e.description())).toList());
    }

    public TransactionResponse transaction(JournalTransaction transaction, Ledger ledger) {
        return new TransactionResponse(transaction.getId(), transaction.getLedgerId(), ledger.getCurrency(),
                transaction.getExternalReference(), transaction.getDescription(), transaction.getRecordedAt(),
                transaction.getReversalOfTransactionId(), transaction.getReversalReason(),
                transaction.getEntries().stream().map(e -> new EntryResponse(e.getId(), e.getAccountId(),
                        e.getLineNumber(), e.getSide(), Money.format(e.getAmount()), e.getDescription())).toList());
    }

    public Posting posting(JournalTransaction transaction) {
        return new Posting(transaction.getExternalReference(), transaction.getDescription(), transaction.getEntries().stream()
                .map(e -> new PostingLine(e.getAccountId(), e.getSide(), new Money(e.getAmount()), e.getDescription())).toList());
    }
}
