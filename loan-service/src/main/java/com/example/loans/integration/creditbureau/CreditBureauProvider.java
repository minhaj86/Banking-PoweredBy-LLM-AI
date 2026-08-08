package com.example.loans.integration.creditbureau;

import com.example.loans.dto.request.ApplicationInput;
import com.example.loans.integration.kyc.KycProvider.Verification;

public interface CreditBureauProvider {
    Verification verifyObligations(ApplicationInput input);
}

