package com.example.loans.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import java.util.*;

@Component @RequiredArgsConstructor
public class ProductionGuard {
    private final LoanProperties properties;
    private final Environment environment;
    @PostConstruct public void validate() { validate(properties,Arrays.asList(environment.getActiveProfiles()).contains("local")); }
    public static void validate(LoanProperties p, boolean local) {
        if (!local && p.isSyntheticVerification()) throw new IllegalStateException("Synthetic verification is restricted to the explicit local profile");
        if (!local && "mock".equals(p.getAi().getProvider())) throw new IllegalStateException("Mock AI is restricted to the explicit local profile");
        if (!"mock".equals(p.getAi().getProvider()) && (!p.getAi().isDataHandlingApproved() || p.getAi().getModel()==null || p.getAi().getModel().isBlank() || p.getAi().getEndpoint()==null)) throw new IllegalStateException("AI deployment needs explicit data-handling approval, model and endpoint");
        if (p.getProduct().getNominalAnnualRate().signum()<0 || p.getProduct().getStressAnnualRate().compareTo(p.getProduct().getNominalAnnualRate())<0) throw new IllegalStateException("Invalid rate policy");
        var known = Set.of("monthlyNetIncome","monthlyNonDebtExpenses","existingMonthlyDebtRepayments","proposedMonthlyInstalment","residualIncome","debtServiceToIncome");
        if (!known.containsAll(p.getProduct().getUnderwritingInputs())) throw new IllegalStateException("Unsupported underwriting input");
        if (p.getLedger().getId()==null) throw new IllegalStateException("Configured lender ledger ID is required");
        if (p.getLease().compareTo(p.getProviderTimeout().multipliedBy(3))<0) throw new IllegalStateException("Lease must cover bounded provider processing");
    }
}

