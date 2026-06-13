package com.example.ledger.dto.response;

import com.example.ledger.domain.enums.LedgerCurrency;
import java.time.Instant;
import java.util.UUID;

public record LedgerResponse(UUID id, String code, String name, LedgerCurrency currency, Instant createdAt) {}
