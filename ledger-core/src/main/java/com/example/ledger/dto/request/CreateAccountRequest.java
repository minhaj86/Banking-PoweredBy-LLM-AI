package com.example.ledger.dto.request;

import com.example.ledger.domain.enums.AccountType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

public record CreateAccountRequest(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9_-]{0,49}") @Schema(example = "CUSTOMER-A") String code,
        @NotBlank @Size(max = 200) @Schema(example = "Customer A Deposits") String name,
        @NotNull @Schema(example = "LIABILITY") AccountType type,
        @Schema(defaultValue = "false") boolean enforceNonNegativeBalance) {}
