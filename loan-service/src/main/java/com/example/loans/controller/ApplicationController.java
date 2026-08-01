package com.example.loans.controller;

import com.example.loans.dto.request.*;
import com.example.loans.dto.response.*;
import com.example.loans.security.Actor;
import com.example.loans.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.*;

@RestController @RequestMapping("/api/v1/loan-applications") @RequiredArgsConstructor
@PreAuthorize("hasAnyRole('APPLICANT','LOAN_OFFICER','COMPLIANCE_REVIEWER','OPERATIONS')")
public class ApplicationController {
    private final ApplicationService applications;
    private final DecisionService decisions;
    @PostMapping public ResponseEntity<ApplicationView> create(@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody ApplicationInput input) {
        var result=applications.create(Actor.current(),key,input); return ResponseEntity.created(URI.create("/api/v1/loan-applications/"+result.id())).body(result);
    }
    @GetMapping public Map<String,Object> list(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="25") int size,
        @RequestParam(required=false) String status,@RequestParam(required=false) String productId,@RequestParam(required=false) java.time.LocalDate from,@RequestParam(required=false) java.time.LocalDate to,
        @RequestParam(defaultValue="newest") String sort,@RequestParam(required=false) String q,@RequestParam(defaultValue="auto") String view) { return applications.queue(Actor.current(),page,size,status,productId,from,to,sort,q,view); }
    @GetMapping("/{id}/timeline") public List<Map<String,Object>> timeline(@PathVariable UUID id){return applications.timeline(Actor.current(),id);}
    @GetMapping("/{id}/information-requests") public List<Map<String,Object>> informationRequests(@PathVariable UUID id){return applications.informationRequests(Actor.current(),id);}
    @GetMapping("/{id}/review-requests") public List<Map<String,Object>> reviewRequests(@PathVariable UUID id){return applications.reviewRequests(Actor.current(),id);}
    @GetMapping("/{id}") public ApplicationView get(@PathVariable UUID id) { return applications.get(Actor.current(),id); }
    @PutMapping("/{id}") public ApplicationView update(@PathVariable UUID id,@RequestHeader("If-Match") long version,@Valid @RequestBody ApplicationInput input) { return applications.edit(Actor.current(),id,version,input); }
    @PostMapping("/{id}/submit") public ResponseEntity<ApplicationView> submit(@PathVariable UUID id,@RequestHeader("If-Match") long version,@RequestHeader("Idempotency-Key") String key) { return ResponseEntity.accepted().body(applications.submit(Actor.current(),id,version,key)); }
    @PostMapping("/{id}/withdraw") public ApplicationView withdraw(@PathVariable UUID id,@RequestHeader("If-Match") long version,@RequestHeader("Idempotency-Key") String key) { return applications.withdraw(Actor.current(),id,version,key); }
    @GetMapping("/{id}/processing-status") public Map<String,Object> status(@PathVariable UUID id) { return applications.processing(Actor.current(),id); }
    @GetMapping("/{id}/assessment") public AssessmentView assessment(@PathVariable UUID id) { return applications.assessment(Actor.current(),id); }
    @PostMapping("/{id}/decisions") public ResponseEntity<DecisionView> decision(@PathVariable UUID id,@RequestHeader("If-Match") long version,@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody DecisionRequest request) {
        return ResponseEntity.accepted().body(decisions.decide(Actor.current(),id,version,key,request));
    }
    @PostMapping("/{id}/information-requests") public DecisionView information(@PathVariable UUID id,@RequestHeader("If-Match") long version,@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody DecisionRequest request) {
        if(request.outcome()!=DecisionRequest.Outcome.REQUEST_INFORMATION) throw com.example.loans.exception.LoanException.invalid("INVALID_INFORMATION_REQUEST","Outcome must be REQUEST_INFORMATION");
        return decisions.decide(Actor.current(),id,version,key,request);
    }
    @PostMapping("/{id}/review-requests") public ResponseEntity<Map<String,UUID>> reconsider(@PathVariable UUID id,@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody ReviewRequest request) { return ResponseEntity.accepted().body(Map.of("reviewRequestId",applications.reviewRequest(Actor.current(),id,key,request))); }
}

