package com.example.ledger.dto.response;

import com.example.ledger.domain.enums.LedgerCurrency;
import java.util.List;
import java.util.UUID;

public record HistoryResponse(UUID accountId, LedgerCurrency currency, int page, int size, boolean hasNext,
        List<HistoryEntryResponse> entries) {
    public HistoryResponse { entries = List.copyOf(entries); }
}
