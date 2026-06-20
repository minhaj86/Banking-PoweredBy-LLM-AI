package com.example.ledger.service;

import com.example.ledger.dto.request.CreateLedgerRequest;
import com.example.ledger.dto.response.LedgerResponse;
import java.util.UUID;

public interface LedgerService {
    LedgerResponse create(CreateLedgerRequest request);
    LedgerResponse get(UUID ledgerId);
}
