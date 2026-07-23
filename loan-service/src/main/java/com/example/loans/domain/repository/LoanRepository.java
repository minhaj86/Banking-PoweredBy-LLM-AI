package com.example.loans.domain.repository;
import com.example.loans.domain.entity.LoanRecord;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
import java.util.*;
public interface LoanRepository extends JpaRepository<LoanRecord,UUID> {
    java.util.List<LoanRecord> findByApplicationIdIn(java.util.Collection<UUID> ids);
    Optional<LoanRecord> findByApplicationId(UUID applicationId);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select l from LoanRecord l where l.id=:id") Optional<LoanRecord> lock(UUID id);
}

