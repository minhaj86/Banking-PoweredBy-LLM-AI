package com.example.loans.controller;

import com.example.loans.dto.request.ReviewRequest;
import com.example.loans.security.Actor;
import com.example.loans.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController @RequestMapping("/api/v1/loans/{id}") @RequiredArgsConstructor
@org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('APPLICANT','LOAN_OFFICER','OPERATIONS','COMPLIANCE_REVIEWER')")
public class LoanController {
    private final LoanService loans;
    @GetMapping("/balance") public com.example.loans.integration.ledger.LedgerClient.Balance balance(@PathVariable UUID id){return loans.balance(Actor.current(),id);}
    @GetMapping public LoanService.LoanView get(@PathVariable UUID id) { return loans.get(Actor.current(),id); }
    @GetMapping("/terms") public LoanService.TermsView terms(@PathVariable UUID id) { return loans.terms(Actor.current(),id); }
    @GetMapping("/repayment-schedule") public AnnuityCalculator.Schedule schedule(@PathVariable UUID id) { return loans.schedule(Actor.current(),id); }
    @GetMapping("/ledger-account") public LoanService.MappingView mapping(@PathVariable UUID id) { return loans.mapping(Actor.current(),id); }
    @PostMapping("/provisioning/retry") public ResponseEntity<LoanService.LoanView> retry(@PathVariable UUID id,@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody ReviewRequest request) { return ResponseEntity.accepted().body(loans.retry(Actor.current(),id,key,request)); }
}

