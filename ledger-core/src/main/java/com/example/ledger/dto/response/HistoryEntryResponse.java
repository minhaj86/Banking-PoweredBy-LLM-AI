package com.example.ledger.dto.response;

import com.example.ledger.domain.enums.Side;
import java.time.Instant;
import java.util.UUID;

public record HistoryEntryResponse(UUID id, UUID transactionId, int lineNumber, Side side,
        String amount, String description, Instant recordedAt, UUID reversalOfTransactionId) {}
