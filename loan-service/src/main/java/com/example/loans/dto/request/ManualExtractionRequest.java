package com.example.loans.dto.request;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public record ManualExtractionRequest(@NotBlank @Size(max=1000) String reason,@NotEmpty @Size(max=50) List<@Valid Fact> facts) {
    public record Fact(@NotBlank @Pattern(regexp="net_income|gross_income|debt_repayment|non_debt_expenses") String field,
        @NotNull @JsonDeserialize(using=DecimalStringDeserializer.class) String amount,@NotNull @Pattern(regexp="SEK") String currency,
        @NotNull @Pattern(regexp="MONTHLY") String frequency,@Min(1) @Max(20) int page,@Min(1) @Max(10000) int line) {}
}

