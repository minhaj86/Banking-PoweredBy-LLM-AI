package com.example.ledger.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public record PostTransactionRequest(
        @Size(max = 200) String externalReference,
        @NotBlank @Size(max = 500) String description,
        @NotNull @Size(min = 2, max = 100) List<@NotNull @Valid EntryRequest> entries) {}
