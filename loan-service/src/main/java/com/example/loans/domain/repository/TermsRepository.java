package com.example.loans.domain.repository;
import com.example.loans.domain.entity.LoanTerms;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface TermsRepository extends JpaRepository<LoanTerms,UUID> {}

