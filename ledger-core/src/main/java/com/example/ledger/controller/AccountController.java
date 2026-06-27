package com.example.ledger.controller;

import com.example.ledger.dto.request.CreateAccountRequest;
import com.example.ledger.dto.request.ProvisionAccountRequest;
import com.example.ledger.dto.response.*;
import com.example.ledger.service.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ledgers/{ledgerId}/accounts")
@RequiredArgsConstructor
public class AccountController {
    private final AccountService accounts;
    private final ReportingService reports;

    @PostMapping("/provision")
    @Operation(summary = "Idempotently provision by immutable external reference; never posts a journal")
    public ResponseEntity<AccountResponse> provision(@PathVariable UUID ledgerId,
            @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody ProvisionAccountRequest request) {
        var result = accounts.provision(ledgerId, key, request);
        return ResponseEntity.created(URI.create("/api/v1/ledgers/" + ledgerId + "/accounts/" + result.id())).body(result);
    }

    @GetMapping("/by-external-reference")
    public AccountResponse byReference(@PathVariable UUID ledgerId, @RequestParam String externalReference) {
        return accounts.byExternalReference(ledgerId, externalReference);
    }

    @PostMapping
    public ResponseEntity<AccountResponse> create(@PathVariable UUID ledgerId, @Valid @RequestBody CreateAccountRequest request) {
        var result = accounts.create(ledgerId, request);
        return ResponseEntity.created(URI.create("/api/v1/ledgers/" + ledgerId + "/accounts/" + result.id())).body(result);
    }

    @GetMapping("/{accountId}")
    public AccountResponse get(@PathVariable UUID ledgerId, @PathVariable UUID accountId) { return accounts.get(ledgerId, accountId); }

    @PostMapping("/{accountId}/close")
    @Operation(summary = "Close a zero-balance account; repeated closure returns the closed account")
    public AccountResponse close(@PathVariable UUID ledgerId, @PathVariable UUID accountId) { return accounts.close(ledgerId, accountId); }

    @GetMapping("/{accountId}/balance")
    public BalanceResponse balance(@PathVariable UUID ledgerId, @PathVariable UUID accountId) { return reports.balance(ledgerId, accountId); }

    @GetMapping("/{accountId}/entries")
    @Operation(summary = "Read account entries ordered by recordedAt, transaction UUID, then line number",
            description = "UTC instants; inclusive from, exclusive to. Ordering is deterministic, not database commit order. Offset pages may shift during concurrent writes.")
    public HistoryResponse history(@PathVariable UUID ledgerId, @PathVariable UUID accountId,
            @Parameter(description = "Inclusive UTC instant", example = "2026-01-01T00:00:00Z") @RequestParam(required = false) Instant from,
            @Parameter(description = "Exclusive UTC instant", example = "2027-01-01T00:00:00Z") @RequestParam(required = false) Instant to,
            @Parameter(description = "Zero-based page, 0..100000") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size, 1..200") @RequestParam(defaultValue = "50") int size) {
        return reports.history(ledgerId, accountId, from, to, page, size);
    }
}
