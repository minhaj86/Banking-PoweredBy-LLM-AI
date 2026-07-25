package com.example.loans.dto.response;

import com.example.loans.integration.ai.CreditAssessmentAiService;
import com.example.loans.integration.kyc.KycProvider;
import com.example.loans.service.AffordabilityService;
import java.time.Instant;
import java.util.*;

public record AssessmentView(int revision,String productId,int productVersion,String policyVersion,List<UUID> documentIds,
        AffordabilityService.Result affordability,CreditAssessmentAiService.Execution ai,List<KycProvider.Verification> verifications,
        List<String> evidenceLimitations,Instant assessedAt) {}

