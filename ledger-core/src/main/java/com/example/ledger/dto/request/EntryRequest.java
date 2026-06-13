package com.example.ledger.dto.request;

import com.example.ledger.domain.enums.Side;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.util.UUID;

public record EntryRequest(
        @NotNull UUID accountId,
        @NotNull @Schema(example = "DEBIT") Side side,
        @NotNull @Size(max = 64) @JsonDeserialize(using = DecimalStringDeserializer.class)
        @Schema(type = "string", example = "1000.00", description = "Positive decimal string; max 999999999999.99; no rounding")
        String amount,
        @Size(max = 500) String description) {}
