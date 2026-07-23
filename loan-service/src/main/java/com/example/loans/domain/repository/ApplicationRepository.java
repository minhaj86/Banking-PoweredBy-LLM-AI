package com.example.loans.domain.repository;
import com.example.loans.domain.entity.ApplicationRecord;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
import java.util.*;
public interface ApplicationRepository extends JpaRepository<ApplicationRecord,UUID>, JpaSpecificationExecutor<ApplicationRecord> {
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select a from ApplicationRecord a where a.id=:id")
    Optional<ApplicationRecord> lock(UUID id);
    Page<ApplicationRecord> findByTenantAndApplicantOrderByCreatedAtDescIdDesc(String tenant,String applicant,Pageable page);
    Page<ApplicationRecord> findByTenantOrderByCreatedAtDescIdDesc(String tenant,Pageable page);
}

