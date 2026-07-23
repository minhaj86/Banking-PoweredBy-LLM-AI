package com.example.loans.domain.repository;
import com.example.loans.domain.entity.DocumentRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface DocumentRepository extends JpaRepository<DocumentRecord,UUID> {
    List<DocumentRecord> findByApplicationIdOrderByUploadedAtAsc(UUID applicationId);
    Optional<DocumentRecord> findByIdAndApplicationId(UUID id,UUID applicationId);
}

