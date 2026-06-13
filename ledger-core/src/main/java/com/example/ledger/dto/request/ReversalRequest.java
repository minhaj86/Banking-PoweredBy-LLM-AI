package com.example.ledger.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

public record ReversalRequest(
        @NotBlank @Size(max = 500) @Schema(example = "Fee charged in error") String reason) {}
