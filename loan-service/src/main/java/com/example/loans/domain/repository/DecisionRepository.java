package com.example.loans.domain.repository;
import com.example.loans.domain.entity.CreditDecision;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface DecisionRepository extends JpaRepository<CreditDecision,UUID> {
    List<CreditDecision> findByApplicationIdOrderByCreatedAtAsc(UUID applicationId);
}

