package com.example.ledger.dto.response;

import com.example.ledger.domain.enums.Side;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

public record EntryResponse(UUID id, UUID accountId, int lineNumber, Side side,
        @Schema(type = "string", example = "1000.00") String amount, String description) {}
