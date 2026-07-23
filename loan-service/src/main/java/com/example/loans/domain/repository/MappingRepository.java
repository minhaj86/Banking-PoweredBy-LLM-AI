package com.example.loans.domain.repository;
import com.example.loans.domain.entity.LedgerAccountMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface MappingRepository extends JpaRepository<LedgerAccountMapping,UUID> {}

