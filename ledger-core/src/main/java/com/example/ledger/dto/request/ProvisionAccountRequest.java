package com.example.ledger.dto.request;

import com.example.ledger.domain.enums.AccountType;
import jakarta.validation.constraints.*;

public record ProvisionAccountRequest(
        @NotBlank @Size(max = 200) String externalReference,
        @NotBlank @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9_-]{0,49}") String code,
        @NotBlank @Size(max = 200) String name,
        @NotNull AccountType type,
        boolean enforceNonNegativeBalance) {}
