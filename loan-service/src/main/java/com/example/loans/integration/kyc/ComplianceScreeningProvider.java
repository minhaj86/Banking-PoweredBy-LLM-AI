package com.example.loans.integration.kyc;

import com.example.loans.dto.request.ApplicationInput;

/** Compliance eligibility is independent of credit affordability and AI advisory outcomes. */
public interface ComplianceScreeningProvider {
    KycProvider.Verification screen(ApplicationInput input);
}
