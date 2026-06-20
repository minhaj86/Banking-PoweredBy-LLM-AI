package com.example.ledger.service.impl;

import com.example.ledger.domain.entity.Account;
import com.example.ledger.domain.enums.AccountStatus;
import com.example.ledger.domain.repository.*;
import com.example.ledger.dto.request.CreateAccountRequest;
import com.example.ledger.dto.request.ProvisionAccountRequest;
import com.example.ledger.dto.response.AccountResponse;
import com.example.ledger.exception.LedgerException;
import com.example.ledger.mapper.LedgerMapper;
import com.example.ledger.service.AccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountServiceImpl implements AccountService {
    private final AccountRepository repository;
    private final AccountLockRepository locks;
    private final BalanceRepository balances;
    private final LedgerLookup lookup;
    private final LedgerMapper mapper;
    private final RequestValidation validation;
    private final TransactionExecutor transactions;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Override
    public AccountResponse provision(UUID ledgerId, String key, ProvisionAccountRequest request) {
        validation.validate(request);
        if (key == null || !key.matches("[A-Za-z0-9._:-]{1,128}")) {
            throw new LedgerException("INVALID_IDEMPOTENCY_KEY", org.springframework.http.HttpStatus.BAD_REQUEST,
                    "A valid Idempotency-Key is required");
        }
        return transactions.execute(() -> {
            lookup.ledger(ledgerId);
            jdbc.update("""
                INSERT INTO ledger.account_provisioning_request
                  (ledger_id,idempotency_key,external_reference,code,name,type,enforce_non_negative_balance)
                VALUES (?,?,?,?,?,?,?) ON CONFLICT (ledger_id,idempotency_key) DO NOTHING
                """, ledgerId, key, request.externalReference(), request.code(), request.name(), request.type().name(), request.enforceNonNegativeBalance());
            // A separate statement sees the winner after ON CONFLICT waits for its commit.
            Boolean same = jdbc.queryForObject("""
                SELECT external_reference=? AND code=? AND name=? AND type=? AND enforce_non_negative_balance=?
                FROM ledger.account_provisioning_request WHERE ledger_id=? AND idempotency_key=?
                """, Boolean.class, request.externalReference(), request.code(), request.name(), request.type().name(), request.enforceNonNegativeBalance(), ledgerId, key);
            if (!Boolean.TRUE.equals(same)) {
                throw LedgerException.conflict("IDEMPOTENCY_CONFLICT", "Key was used with different account terms");
            }
            jdbc.update("""
                INSERT INTO ledger.account (id,ledger_id,code,name,type,enforce_non_negative_balance,external_reference)
                VALUES (?,?,?,?,?,?,?) ON CONFLICT (ledger_id,external_reference) DO NOTHING
                """, UUID.randomUUID(), ledgerId, request.code(), request.name(), request.type().name(), request.enforceNonNegativeBalance(), request.externalReference());
            var account = repository.findByLedgerIdAndExternalReference(ledgerId, request.externalReference()).orElseThrow();
            if (!account.getCode().equals(request.code()) || !account.getName().equals(request.name()) || account.getType() != request.type()
                    || account.isEnforceNonNegativeBalance() != request.enforceNonNegativeBalance()) {
                throw LedgerException.conflict("EXTERNAL_REFERENCE_CONFLICT", "External reference belongs to different account terms");
            }
            return mapper.account(account);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public AccountResponse byExternalReference(UUID ledgerId, String reference) {
        return mapper.account(repository.findByLedgerIdAndExternalReference(ledgerId, reference)
                .orElseThrow(() -> LedgerException.missing("Account")));
    }

    @Override
    @Transactional
    public AccountResponse create(UUID ledgerId, CreateAccountRequest request) {
        validation.validate(request);
        lookup.ledger(ledgerId);
        return mapper.account(repository.saveAndFlush(Account.create(ledgerId, request.code(), request.name(),
                request.type(), request.enforceNonNegativeBalance())));
    }

    @Override
    @Transactional(readOnly = true)
    public AccountResponse get(UUID ledgerId, UUID accountId) { return mapper.account(lookup.account(ledgerId, accountId)); }

    @Override
    public AccountResponse close(UUID ledgerId, UUID accountId) {
        return transactions.execute(() -> {
            var locked = locks.lockAll(ledgerId, List.of(accountId));
            if (locked.isEmpty()) { throw LedgerException.missing("Account"); }
            if (locked.getFirst().status() == AccountStatus.ACTIVE) {
                if (balances.forAccounts(ledgerId, List.of(accountId)).getFirst().normalBalance().signum() != 0) {
                    throw LedgerException.conflict("ACCOUNT_NOT_ZERO", "An account must have zero balance to close");
                }
                locks.close(ledgerId, accountId);
            }
            return mapper.account(lookup.account(ledgerId, accountId));
        });
    }
}
