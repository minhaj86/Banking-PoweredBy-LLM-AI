package com.example.ledger.controller;

import com.example.ledger.dto.request.CreateLedgerRequest;
import com.example.ledger.dto.response.*;
import com.example.ledger.service.*;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ledgers")
@RequiredArgsConstructor
public class LedgerController {
    private final LedgerService ledgers;
    private final ReportingService reports;

    @PostMapping
    @Operation(summary = "Create a single-currency ledger")
    public ResponseEntity<LedgerResponse> create(@Valid @RequestBody CreateLedgerRequest request) {
        var result = ledgers.create(request);
        return ResponseEntity.created(URI.create("/api/v1/ledgers/" + result.id())).body(result);
    }

    @GetMapping("/{ledgerId}")
    public LedgerResponse get(@PathVariable UUID ledgerId) { return ledgers.get(ledgerId); }

    @GetMapping("/{ledgerId}/trial-balance")
    @Operation(summary = "Read the trial balance from a single database snapshot")
    public TrialBalanceResponse trialBalance(@PathVariable UUID ledgerId) { return reports.trialBalance(ledgerId); }
}
