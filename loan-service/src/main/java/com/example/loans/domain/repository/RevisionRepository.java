package com.example.loans.domain.repository;
import com.example.loans.domain.entity.ApplicationRevision;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface RevisionRepository extends JpaRepository<ApplicationRevision,UUID> {
    Optional<ApplicationRevision> findByApplicationIdAndRevision(UUID applicationId,int revision);
}

