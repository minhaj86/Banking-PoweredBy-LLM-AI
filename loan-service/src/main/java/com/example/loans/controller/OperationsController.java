package com.example.loans.controller;

import com.example.loans.dto.request.ReviewRequest;
import com.example.loans.security.Actor;
import com.example.loans.service.OperationsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/api/v1/operations") @RequiredArgsConstructor
public class OperationsController {
    private final OperationsService operations;
    private final com.example.loans.service.OperationalMetrics metrics;
    @GetMapping("/jobs") public Map<String,Object> jobs(@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="25")int size,@RequestParam(defaultValue="FAILED")String status){return operations.queue(Actor.current(),page,size,status);}
    @GetMapping("/metrics") public Map<String,Object> metrics() { return metrics.snapshot(Actor.current()); }
    @PostMapping("/provisioning/reconcile") public ResponseEntity<Map<String,UUID>> reconcile(@RequestHeader("Idempotency-Key") String key) { return ResponseEntity.accepted().body(Map.of("reconciliationId",operations.reconcile(Actor.current(),key))); }
    @PostMapping("/applications/{id}/jobs/{job}/retry") public ResponseEntity<Map<String,UUID>> retry(@PathVariable UUID id,@PathVariable UUID job,@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody ReviewRequest request) { return ResponseEntity.accepted().body(Map.of("jobId",operations.retryJob(Actor.current(),id,job,key,request))); }
    @PostMapping("/applications/{id}/legal-hold") public ResponseEntity<Void> hold(@PathVariable UUID id,@RequestHeader("If-Match") long version,@RequestParam boolean enabled) { operations.legalHold(Actor.current(),id,version,enabled); return ResponseEntity.noContent().build(); }
    @GetMapping("/applications/{id}/audit") public List<Map<String,Object>> audit(@PathVariable UUID id) { return operations.audit(Actor.current(),id); }
}

