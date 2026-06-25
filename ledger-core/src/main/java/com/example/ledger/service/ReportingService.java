package com.example.ledger.service;

import com.example.ledger.dto.response.*;
import java.time.Instant;
import java.util.UUID;

public interface ReportingService {
    BalanceResponse balance(UUID ledgerId, UUID accountId);
    HistoryResponse history(UUID ledgerId, UUID accountId, Instant from, Instant to, int page, int size);
    TrialBalanceResponse trialBalance(UUID ledgerId);
}
