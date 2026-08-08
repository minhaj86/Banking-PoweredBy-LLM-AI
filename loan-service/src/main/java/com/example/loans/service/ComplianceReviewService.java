package com.example.loans.service;

import com.example.loans.domain.enums.ApplicationState;
import com.example.loans.domain.repository.AssessmentRepository;
import com.example.loans.dto.request.ComplianceReviewRequest;
import com.example.loans.dto.response.AssessmentView;
import com.example.loans.exception.LoanException;
import com.example.loans.integration.kyc.KycProvider;
import com.example.loans.mapper.Json;
import com.example.loans.security.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class ComplianceReviewService {
    private final ResourceAccess access;
    private final AssessmentRepository assessments;
    private final IdempotencyService idempotency;
    private final JdbcTemplate jdbc;
    private final SensitiveTextConverter encryption;
    private final Json json;
    private final AuditService audit;
    @Transactional public UUID review(Actor actor,UUID application,long expected,String key,ComplianceReviewRequest request) {
        actor.require("COMPLIANCE_REVIEWER");
        return idempotency.execute(actor,"COMPLIANCE_REVIEW",application.toString(),key,Map.of("request",request,"version",expected),()->{
            var app=access.application(actor,application,true); access.expected(app.getVersion(),expected);
            access.independentReviewer(actor,app);
            if(app.getState().terminal()) throw LoanException.conflict("INVALID_TRANSITION","Closed applications cannot receive verification reviews");
            var a=json.read(assessments.findByApplicationIdAndRevision(application,app.getCurrentRevision()).orElseThrow(LoanException::missing).getPayload(),AssessmentView.class);
            if(a.verifications().stream().noneMatch(v->v.gate().equals("COMPLIANCE") && v.status().equals("REVIEW_REQUIRED")))
                throw LoanException.conflict("COMPLIANCE_REVIEW_NOT_REQUIRED","Manual compliance review resolves only a provider's REVIEW_REQUIRED result");
            UUID id=UUID.randomUUID();
            jdbc.update("INSERT INTO origination.verification_review(id,application_id,revision,gate,outcome,reviewer,evidence_reference,explanation,valid_until) VALUES(?,?,?,'COMPLIANCE',?,?,?,?,?)",
                id,application,app.getCurrentRevision(),request.outcome().name(),actor.subject(),request.evidenceReference(),encryption.convertToDatabaseColumn(request.restrictedExplanation()),
                java.sql.Timestamp.from(Instant.now().plus(1,java.time.temporal.ChronoUnit.DAYS)));
            app.reviewed(); audit.record(actor,"COMPLIANCE_REVIEWED",application,app.getCurrentRevision(),id); return id;
        });
    }
    public List<KycProvider.Verification> effective(UUID application,int revision,List<KycProvider.Verification> original) {
        var reviews=jdbc.query("""
            SELECT outcome,evidence_reference,created_at,valid_until FROM origination.verification_review WHERE application_id=? AND revision=?
            ORDER BY created_at DESC,id DESC LIMIT 1
            """,(rs,n)->new KycProvider.Verification("COMPLIANCE",rs.getString(1),"HUMAN_COMPLIANCE_REVIEW",rs.getString(2),rs.getTimestamp(3).toInstant(),rs.getTimestamp(4).toInstant(),false),application,revision);
        if(reviews.isEmpty()) return original;
        return original.stream().map(v->v.gate().equals("COMPLIANCE")?reviews.getFirst():v).toList();
    }
    @Transactional(readOnly=true) public List<KycProvider.Verification> get(Actor actor,UUID application) {
        actor.require("COMPLIANCE_REVIEWER","LOAN_OFFICER"); var app=access.application(actor,application,false);
        var a=json.read(assessments.findByApplicationIdAndRevision(application,app.getCurrentRevision()).orElseThrow(LoanException::missing).getPayload(),AssessmentView.class);
        return effective(application,app.getCurrentRevision(),a.verifications());
    }
}

