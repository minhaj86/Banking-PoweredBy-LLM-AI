package com.example.loans.controller;

import com.example.loans.integration.ai.*;
import com.example.loans.security.Actor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;

@RestController @Profile("local") @RequestMapping("/api/v1/operations/demo")
public class LocalDemoController {
    private final CreditAssessmentAiService ai;
    public LocalDemoController(CreditAssessmentAiService ai) { this.ai=ai; }
    @PostMapping("/ai-unavailable") public ResponseEntity<Void> fault(@RequestParam boolean enabled) {
        Actor.current().require("OPERATIONS");
        if(!(ai instanceof MockCreditAssessmentAiService mock)) throw com.example.loans.exception.LoanException.conflict("NOT_MOCK_PROVIDER","Synthetic fault injection requires the local mock provider");
        mock.setUnavailable(enabled); return ResponseEntity.noContent().build();
    }
}
