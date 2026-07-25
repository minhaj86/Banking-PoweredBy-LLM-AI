package com.example.loans.dto.request;

import jakarta.validation.constraints.*;
import java.util.*;

public record DecisionRequest(@NotNull Outcome outcome,@NotEmpty @Size(max=10) List<@Pattern(regexp="[A-Z_]{1,60}") String> reasonCodes,
        @NotBlank @Size(max=2000) String explanation,@NotBlank @Size(max=1000) String applicantExplanation,
        @NotNull @Size(max=100) Set<UUID> acknowledgedDocuments,
        @NotNull @Size(max=100) Set<@Size(max=100) String> acknowledgedFindings,
        boolean financialFactsConfirmed,boolean declaredExpensesAndDebtsReviewed,boolean authenticityReviewed) {
    public enum Outcome { APPROVE, REJECT, REQUEST_INFORMATION }
}

