package com.example.ledger.dto.response;

import com.example.ledger.domain.enums.*;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

public record BalanceResponse(UUID accountId, LedgerCurrency currency, Side normalBalanceSide,
        @Schema(example = "1000.00") String totalDebits,
        @Schema(example = "0.00") String totalCredits,
        @Schema(example = "1000.00") String balance) {}
