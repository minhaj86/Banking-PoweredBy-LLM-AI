package com.example.ledger.domain.repository;

import com.example.ledger.domain.entity.Ledger;
import org.springframework.data.repository.Repository;
import java.util.Optional;
import java.util.UUID;

public interface LedgerRepository extends Repository<Ledger, UUID> {
    Optional<Ledger> findById(UUID id);
    Ledger saveAndFlush(Ledger ledger);
}
