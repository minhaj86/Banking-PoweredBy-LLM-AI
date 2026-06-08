package com.example.ledger.domain.repository;

import com.example.ledger.domain.entity.Account;
import org.springframework.data.repository.Repository;
import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends Repository<Account, UUID> {
    Optional<Account> findByIdAndLedgerId(UUID id, UUID ledgerId);
    Optional<Account> findByLedgerIdAndExternalReference(UUID ledgerId, String externalReference);
    Account saveAndFlush(Account account);
}
