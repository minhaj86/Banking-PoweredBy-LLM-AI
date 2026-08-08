package com.example.loans.integration.kyc;

import com.example.loans.dto.request.ApplicationInput;
import java.time.Instant;

public interface KycProvider {
    record Verification(String gate,String status,String provider,String evidenceReference,Instant checkedAt,Instant validUntil,boolean synthetic) {}
    java.util.List<Verification> verify(ApplicationInput input);
}

