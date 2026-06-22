package com.example.ledger.service;

import com.example.ledger.dto.request.*;
import com.example.ledger.dto.response.TransactionResponse;
import java.util.UUID;

public interface JournalService {
    TransactionResponse post(UUID ledgerId, String key, PostTransactionRequest request);
    TransactionResponse get(UUID ledgerId, UUID transactionId);
    TransactionResponse reverse(UUID ledgerId, UUID transactionId, String key, ReversalRequest request);
}
