package com.example.loans.controller;

import com.example.loans.dto.request.ComplianceReviewRequest;
import com.example.loans.integration.kyc.KycProvider;
import com.example.loans.security.Actor;
import com.example.loans.service.ComplianceReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/api/v1/loan-applications/{id}") @RequiredArgsConstructor
public class VerificationController {
    private final ComplianceReviewService compliance;
    @GetMapping("/verifications") public List<KycProvider.Verification> get(@PathVariable UUID id) { return compliance.get(Actor.current(),id); }
    @PostMapping("/verification-reviews") public ResponseEntity<Map<String,UUID>> review(@PathVariable UUID id,@RequestHeader("If-Match") long version,@RequestHeader("Idempotency-Key") String key,
        @Valid @RequestBody ComplianceReviewRequest request) { return ResponseEntity.ok(Map.of("verificationReviewId",compliance.review(Actor.current(),id,version,key,request))); }
}

