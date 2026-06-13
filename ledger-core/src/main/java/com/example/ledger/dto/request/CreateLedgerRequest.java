package com.example.ledger.dto.request;

import com.example.ledger.domain.enums.LedgerCurrency;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

public record CreateLedgerRequest(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9_-]{0,49}") @Schema(example = "BANK-SE") String code,
        @NotBlank @Size(max = 200) @Schema(example = "Swedish bank ledger") String name,
        @NotNull @Schema(example = "SEK") LedgerCurrency currency) {}
