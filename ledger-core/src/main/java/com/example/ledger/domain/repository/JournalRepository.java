package com.example.ledger.domain.repository;

import com.example.ledger.domain.entity.JournalTransaction;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import java.util.Optional;
import java.util.UUID;

public interface JournalRepository extends Repository<JournalTransaction, UUID> {
    @EntityGraph(attributePaths = "entries")
    @Query("select t from JournalTransaction t where t.id = :id and t.ledgerId = :ledgerId and t.state = 'POSTED'")
    Optional<JournalTransaction> findPosted(UUID ledgerId, UUID id);
}
