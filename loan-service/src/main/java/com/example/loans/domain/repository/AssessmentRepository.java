package com.example.loans.domain.repository;
import com.example.loans.domain.entity.AssessmentRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface AssessmentRepository extends JpaRepository<AssessmentRecord,UUID> {
    Optional<AssessmentRecord> findByApplicationIdAndRevision(UUID applicationId,int revision);
}

