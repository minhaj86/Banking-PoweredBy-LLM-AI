package com.example.ledger.dto.response;

import com.example.ledger.domain.enums.*;
import java.time.Instant;
import java.util.UUID;

public record AccountResponse(UUID id, UUID ledgerId, String code, String name, AccountType type,
        AccountStatus status, Side normalBalanceSide, boolean enforceNonNegativeBalance, Instant createdAt,
        String externalReference) {}
