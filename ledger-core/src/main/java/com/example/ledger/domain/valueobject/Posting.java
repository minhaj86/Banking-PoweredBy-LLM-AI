package com.example.ledger.domain.valueobject;

import com.example.ledger.domain.enums.Side;
import com.example.ledger.exception.LedgerException;
import java.math.BigDecimal;
import java.util.List;

public record Posting(String externalReference, String description, List<PostingLine> entries) {
    public static final int MAX_ENTRIES = 100;

    public Posting {
        if (description == null || description.isBlank() || description.length() > 500
                || externalReference != null && externalReference.length() > 200) {
            throw LedgerException.invalid("INVALID_POSTING", "Description is required (max 500); external reference max 200");
        }
        if (entries == null || entries.size() < 2 || entries.size() > MAX_ENTRIES) {
            throw LedgerException.invalid("INVALID_ENTRIES", "A transaction must contain 2 to 100 entries");
        }
        if (entries.stream().anyMatch(e -> e == null || e.accountId() == null || e.side() == null || e.amount() == null
                || e.description() != null && e.description().length() > 500)) {
            throw LedgerException.invalid("INVALID_ENTRIES", "Every entry needs an account, side and amount; description max 500");
        }
        entries = List.copyOf(entries);
        if (entries.stream().map(PostingLine::accountId).distinct().count() < 2) {
            throw LedgerException.invalid("DISTINCT_ACCOUNTS_REQUIRED", "At least two distinct accounts are required");
        }
        BigDecimal debits = total(entries, Side.DEBIT);
        BigDecimal credits = total(entries, Side.CREDIT);
        if (debits.signum() == 0 || credits.signum() == 0 || debits.compareTo(credits) != 0) {
            throw LedgerException.invalid("UNBALANCED_TRANSACTION", "Total debits must equal total credits and both sides are required");
        }
        if (debits.compareTo(Money.MAX_TRANSACTION_TOTAL) > 0) {
            throw LedgerException.invalid("TRANSACTION_TOTAL_EXCEEDED", "Each side must total at most 9999999999999.99");
        }
    }

    private static BigDecimal total(List<PostingLine> entries, Side side) {
        return entries.stream().filter(e -> e.side() == side)
                .map(e -> e.amount().value()).reduce(Money.ZERO, BigDecimal::add);
    }

    public Posting reversed(String reason) {
        return new Posting(null, reason, entries.stream().map(PostingLine::reversed).toList());
    }
}
