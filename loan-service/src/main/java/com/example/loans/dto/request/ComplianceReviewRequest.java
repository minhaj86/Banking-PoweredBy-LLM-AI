package com.example.loans.dto.request;
import jakarta.validation.constraints.*;
public record ComplianceReviewRequest(@NotNull Outcome outcome,@NotBlank @Size(max=200) String evidenceReference,
    @NotBlank @Size(max=2000) String restrictedExplanation) {
    public enum Outcome { SATISFIED, BLOCKED }
}

