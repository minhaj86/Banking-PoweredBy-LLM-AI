package com.example.ledger.service.impl;

import com.example.ledger.domain.entity.Ledger;
import com.example.ledger.domain.enums.OperationType;
import com.example.ledger.domain.repository.*;
import com.example.ledger.domain.valueobject.Posting;
import com.example.ledger.dto.request.*;
import com.example.ledger.dto.response.TransactionResponse;
import com.example.ledger.exception.LedgerException;
import com.example.ledger.mapper.LedgerMapper;
import com.example.ledger.service.JournalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class JournalServiceImpl implements JournalService {
    private final TransactionExecutor transactions;
    private final LedgerLookup lookup;
    private final LedgerMapper mapper;
    private final RequestValidation validation;
    private final RequestFingerprint fingerprints;
    private final IdempotencyRepository idempotency;
    private final PostingRules rules;
    private final JournalWriter writer;

    @Override
    public TransactionResponse post(UUID ledgerId, String key, PostTransactionRequest request) {
        validation.validate(request);
        validateKey(key);
        var posting = mapper.posting(request);
        var fingerprint = fingerprints.posting(posting);
        var result = transactions.execute(() -> {
            var ledger = lookup.ledger(ledgerId);
            var id = UUID.randomUUID();
            if (!idempotency.claim(ledgerId, OperationType.POST, key, fingerprint, id)) {
                return replay(ledger, OperationType.POST, key, fingerprint);
            }
            return write(ledger, id, posting, null, null);
        });
        log.info("event=posting_committed ledgerId={} transactionId={}", ledgerId, result.id());
        return result;
    }

    @Override
    public TransactionResponse reverse(UUID ledgerId, UUID transactionId, String key, ReversalRequest request) {
        validation.validate(request);
        validateKey(key);
        var fingerprint = fingerprints.reversal(transactionId, request.reason());
        var result = transactions.execute(() -> {
            var ledger = lookup.ledger(ledgerId);
            var id = UUID.randomUUID();
            if (!idempotency.claim(ledgerId, OperationType.REVERSE, key, fingerprint, id)) {
                return replay(ledger, OperationType.REVERSE, key, fingerprint);
            }
            var original = lookup.transaction(ledgerId, transactionId);
            if (original.getReversalOfTransactionId() != null) {
                throw LedgerException.invalid("REVERSAL_OF_REVERSAL", "A reversal transaction cannot itself be reversed");
            }
            return write(ledger, id, mapper.posting(original).reversed(request.reason()), transactionId, request.reason());
        });
        log.info("event=reversal_committed ledgerId={} transactionId={} originalId={}", ledgerId, result.id(), transactionId);
        return result;
    }

    private TransactionResponse write(Ledger ledger, UUID id, Posting posting, UUID reversalOf, String reason) {
        var accounts = rules.lockAccounts(ledger.getId(), posting);
        if (reversalOf != null && writer.hasReversal(ledger.getId(), reversalOf)) {
            throw LedgerException.conflict("ALREADY_REVERSED", "This transaction already has a full reversal");
        }
        rules.validateFinalBalances(ledger.getId(), posting, accounts);
        writer.write(id, ledger.getId(), posting, reversalOf, reason);
        return mapper.transaction(lookup.transaction(ledger.getId(), id), ledger);
    }

    private TransactionResponse replay(Ledger ledger, OperationType operation, String key, String fingerprint) {
        var original = idempotency.find(ledger.getId(), operation, key);
        if (!original.fingerprint().equals(fingerprint)) {
            throw LedgerException.conflict("IDEMPOTENCY_CONFLICT", "The idempotency key was used with a different request");
        }
        return mapper.transaction(lookup.transaction(ledger.getId(), original.transactionId()), ledger);
    }

    private void validateKey(String key) {
        if (key == null || !key.matches("[A-Za-z0-9._:-]{1,128}")) {
            throw new LedgerException("INVALID_IDEMPOTENCY_KEY", HttpStatus.BAD_REQUEST,
                    "Idempotency-Key must contain 1 to 128 letters, digits, dots, underscores, colons or hyphens");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public TransactionResponse get(UUID ledgerId, UUID transactionId) {
        return mapper.transaction(lookup.transaction(ledgerId, transactionId), lookup.ledger(ledgerId));
    }
}
