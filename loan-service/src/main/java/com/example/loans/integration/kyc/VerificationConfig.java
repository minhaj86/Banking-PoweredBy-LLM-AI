package com.example.loans.integration.kyc;

import com.example.loans.config.LoanProperties;
import com.example.loans.integration.creditbureau.CreditBureauProvider;
import org.springframework.context.annotation.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Configuration
public class VerificationConfig {
    @Bean @org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean(KycProvider.class)
    KycProvider kyc(LoanProperties p) {
        return input -> {
            Instant now=Instant.now(),expiry=now.plus(1,ChronoUnit.DAYS);
            boolean mock=p.isSyntheticVerification();
            String provider=mock?"SYNTHETIC_DEMO_KYC":"UNAVAILABLE_KYC";
            return List.of(new KycProvider.Verification("IDENTITY",mock?"SATISFIED":"UNAVAILABLE",provider,"verification:"+UUID.randomUUID(),now,expiry,mock));
        };
    }
    @Bean @org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean(ComplianceScreeningProvider.class)
    ComplianceScreeningProvider screening(LoanProperties p) {
        return input -> {
            Instant now=Instant.now(); boolean mock=p.isSyntheticVerification();
            return new KycProvider.Verification("COMPLIANCE",mock&&!input.kyc().pepDeclared()?"SATISFIED":mock?"REVIEW_REQUIRED":"UNAVAILABLE",
                mock?"SYNTHETIC_DEMO_SCREENING":"UNAVAILABLE_SCREENING","verification:"+UUID.randomUUID(),now,now.plus(1,ChronoUnit.DAYS),mock);
        };
    }
    @Bean @org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean(CreditBureauProvider.class)
    CreditBureauProvider bureau(LoanProperties p) {
        return input -> {
            Instant now=Instant.now(); boolean mock=p.isSyntheticVerification();
            return new KycProvider.Verification("OBLIGATIONS",mock?"SATISFIED":"UNAVAILABLE",mock?"SYNTHETIC_EMPTY_BUREAU":"UNAVAILABLE_BUREAU",
                    "verification:"+UUID.randomUUID(),now,now.plus(1,ChronoUnit.DAYS),mock);
        };
    }
}

