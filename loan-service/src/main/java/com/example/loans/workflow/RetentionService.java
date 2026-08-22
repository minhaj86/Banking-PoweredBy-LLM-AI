package com.example.loans.workflow;

import com.example.loans.config.LoanProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.UUID;

@Component @RequiredArgsConstructor
@ConditionalOnProperty(name="loan.retention-enabled",havingValue="true")
public class RetentionService {
    private final JobQueue jobs;
    private final JdbcTemplate jdbc;
    private final LoanProperties config;
    @Scheduled(fixedDelayString="${loan.retention-poll-ms:60000}") public void schedule() {
        jobs.transaction(()->{
            var ids=jdbc.query("""
                SELECT d.id FROM origination.document d JOIN origination.application a ON a.id=d.application_id
                WHERE d.purged_at IS NULL AND NOT a.legal_hold AND
                 ((d.upload_status='PENDING' AND d.uploaded_at<now()-interval '1 day')
                  OR (d.expires_at<now() AND a.state IN ('REJECTED','WITHDRAWN','APPROVED')))
                ORDER BY d.uploaded_at LIMIT 100
                """,(rs,n)->rs.getObject(1,UUID.class));
            ids.forEach(id->jobs.enqueue("PURGE",id,0)); return null;
        });
        jobs.transaction(()->{
            var applications=jdbc.query("SELECT id FROM origination.application WHERE state IN ('REJECTED','WITHDRAWN') AND NOT legal_hold AND purged_at IS NULL AND updated_at<now()-(? * interval '1 day') LIMIT 100",
                (rs,n)->rs.getObject(1,UUID.class),config.getRetentionDays());
            for(var app:applications) jdbc.queryForObject("SELECT origination.purge_application_payload(?,now()-(? * interval '1 day'))",Boolean.class,app,config.getRetentionDays());
            return null;
        });
    }
}

