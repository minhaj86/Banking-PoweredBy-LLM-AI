package com.example.ledger.controller;

import com.example.ledger.dto.request.*;
import com.example.ledger.dto.response.TransactionResponse;
import com.example.ledger.service.JournalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ledgers/{ledgerId}/transactions")
@RequiredArgsConstructor
public class JournalController {
    private final JournalService journals;

    @PostMapping
    @Operation(summary = "Post an immutable balanced transaction",
            description = "2..100 ordered entries. Amounts are decimal strings in the ledger currency. First success and idempotent replays return 201 and the same response.")
    public ResponseEntity<TransactionResponse> post(@PathVariable UUID ledgerId,
            @Parameter(description = "1..128 characters: letters, digits, dot, underscore, colon, hyphen", required = true)
            @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody PostTransactionRequest request) {
        return created(journals.post(ledgerId, key, request));
    }

    @GetMapping("/{transactionId}")
    public TransactionResponse get(@PathVariable UUID ledgerId, @PathVariable UUID transactionId) {
        return journals.get(ledgerId, transactionId);
    }

    @PostMapping("/{transactionId}/reversals")
    @Operation(summary = "Fully reverse every entry of an original transaction",
            description = "Only one reversal. Active-account and protected-balance rules apply. Reason is required. Replays return the original 201 result.")
    public ResponseEntity<TransactionResponse> reverse(@PathVariable UUID ledgerId, @PathVariable UUID transactionId,
            @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody ReversalRequest request) {
        return created(journals.reverse(ledgerId, transactionId, key, request));
    }

    private ResponseEntity<TransactionResponse> created(TransactionResponse result) {
        return ResponseEntity.created(URI.create("/api/v1/ledgers/" + result.ledgerId() + "/transactions/" + result.id())).body(result);
    }
}
