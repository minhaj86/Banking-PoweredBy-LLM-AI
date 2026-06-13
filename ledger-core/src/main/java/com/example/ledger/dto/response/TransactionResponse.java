package com.example.ledger.dto.response;

import com.example.ledger.domain.enums.LedgerCurrency;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TransactionResponse(UUID id, UUID ledgerId, LedgerCurrency currency, String externalReference,
        String description, Instant recordedAt, UUID reversalOfTransactionId, String reversalReason,
        List<EntryResponse> entries) {
    public TransactionResponse { entries = List.copyOf(entries); }
}
