package com.example.loans.service;

import com.example.loans.config.LoanProperties;
import com.example.loans.domain.entity.*;
import com.example.loans.domain.enums.ApplicationState;
import com.example.loans.domain.repository.*;
import com.example.loans.dto.request.*;
import com.example.loans.dto.response.*;
import com.example.loans.exception.LoanException;
import com.example.loans.mapper.Json;
import com.example.loans.security.Actor;
import com.example.loans.workflow.JobQueue;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;

@Service @RequiredArgsConstructor
public class DecisionService {
    private final ApplicationRepository applications;
    private final DecisionRepository decisions;
    private final AssessmentRepository assessments;
    private final RevisionRepository revisions;
    private final LoanRepository loans;
    private final TermsRepository terms;
    private final ResourceAccess access;
    private final IdempotencyService idempotency;
    private final JobQueue jobs;
    private final AuditService audit;
    private final LoanProperties config;
    private final Json json;
    private final ComplianceReviewService compliance;
    private final DocumentRepository documents;
    @Transactional public DecisionView decide(Actor actor,UUID id,long expected,String key,DecisionRequest request) {
        actor.require("LOAN_OFFICER");
        UUID decision=idempotency.execute(actor,"DECIDE",id.toString(),key,Map.of("request",request,"expectedVersion",expected),() -> {
            var app=access.application(actor,id,true); access.expected(app.getVersion(),expected);
            access.independentReviewer(actor,app);
            if(app.getState()!=ApplicationState.MANUAL_REVIEW && !(request.outcome()==DecisionRequest.Outcome.REQUEST_INFORMATION && app.getState()==ApplicationState.DOCUMENTS_REQUIRED))
                throw LoanException.conflict("INVALID_TRANSITION","A final decision requires the current completed assessment");
            var assessment=assessments.findByApplicationIdAndRevision(id,app.getCurrentRevision()).orElseThrow(()->LoanException.conflict("ASSESSMENT_REQUIRED","Current assessment is required"));
            var data=json.read(assessment.getPayload(),AssessmentView.class);
            if(request.outcome()==DecisionRequest.Outcome.APPROVE) gates(app,data,request);
            var decisionRecord=decisions.saveAndFlush(CreditDecision.create(id,app.getCurrentRevision(),assessment.getId(),actor.subject(),request.outcome().name(),json.write(request),request.applicantExplanation()));
            if(request.outcome()==DecisionRequest.Outcome.APPROVE) {
                app.transition(ApplicationState.APPROVED);
                var loan=loans.saveAndFlush(LoanRecord.approved(app,decisionRecord.getId()));
                var revision=revisions.findByApplicationIdAndRevision(id,app.getCurrentRevision()).orElseThrow();
                var p=json.read(revision.getPolicySnapshot(),LoanProperties.Product.class);
                var input=json.read(revision.getPayload(),ApplicationInput.class);
                terms.saveAndFlush(LoanTerms.approved(loan.getId(),data.productId(),data.productVersion(),data.policyVersion(),
                    new java.math.BigDecimal(input.requestedAmount()),input.termMonths(),p.getNominalAnnualRate(),json.write(data.affordability().schedule())));
                jobs.enqueue("PROVISION",loan.getId(),app.getCurrentRevision());
                audit.record(actor,"LOAN_APPROVED",id,app.getCurrentRevision(),loan.getId());
            } else if(request.outcome()==DecisionRequest.Outcome.REJECT) app.transition(ApplicationState.REJECTED);
            else if(app.getState()!=ApplicationState.DOCUMENTS_REQUIRED) app.transition(ApplicationState.DOCUMENTS_REQUIRED);
            audit.record(actor,"HUMAN_"+request.outcome().name(),id,app.getCurrentRevision(),decisionRecord.getId());
            return decisionRecord.getId();
        });
        var d=decisions.findById(decision).orElseThrow();
        return new DecisionView(d.getId(),d.getOutcome(),loans.findByApplicationId(id).map(LoanRecord::getId).orElse(null),d.getApplicantExplanation());
    }
    private void gates(ApplicationRecord app,AssessmentView a,DecisionRequest r) {
        var p=config.getProduct();
        if(a.revision()!=app.getCurrentRevision() || !p.isEligible() || !p.getId().equals(a.productId()) || p.getVersion()!=a.productVersion() || !p.getPolicyVersion().equals(a.policyVersion()))
            throw LoanException.conflict("STALE_ASSESSMENT","Assessment product or policy is no longer eligible");
        var revision=revisions.findByApplicationIdAndRevision(app.getId(),a.revision()).orElseThrow();
        // Prevent same-version configuration edits from silently changing the assessed terms.
        if(!revision.getPolicySnapshot().equals(json.write(p))) throw LoanException.conflict("POLICY_CHANGED","Policy content changed; resubmission is required");
        if(a.assessedAt().plus(p.getAssessmentValidityDays(),java.time.temporal.ChronoUnit.DAYS).isBefore(Instant.now())) throw LoanException.conflict("ASSESSMENT_EXPIRED","Assessment must be refreshed");
        var currentDocuments=documents.findAllById(a.documentIds());
        var earliest=java.time.LocalDate.now(java.time.ZoneOffset.UTC).minusDays(p.getEvidenceFreshnessDays());
        if(currentDocuments.size()!=a.documentIds().size() || currentDocuments.stream().anyMatch(d->d.getPurgedAt()!=null||!d.getUploadStatus().equals("COMPLETE")||!d.getScanStatus().equals("CLEAN")||!d.getProcessingStatus().equals("EXTRACTED")||d.getPeriodEnd().isBefore(earliest)))
            throw LoanException.conflict("EVIDENCE_NO_LONGER_CURRENT","Mandatory assessment evidence must still be complete, clean and fresh at approval");
        if(!a.affordability().blockers().isEmpty() || !a.affordability().findings().isEmpty()) throw LoanException.conflict("UNDERWRITING_GATES","Policy concerns or missing evidence require resolution before approval");
        var gates=compliance.effective(app.getId(),app.getCurrentRevision(),a.verifications());
        if(gates.size()!=3 || !gates.stream().map(v->v.gate()).collect(java.util.stream.Collectors.toSet()).equals(Set.of("IDENTITY","COMPLIANCE","OBLIGATIONS"))
            || gates.stream().anyMatch(v->!v.status().equals("SATISFIED")||v.validUntil().isBefore(Instant.now())||(!config.isSyntheticVerification()&&v.synthetic())))
            throw LoanException.conflict("VERIFICATION_GATES","Required identity, compliance and obligation gates must be current and satisfied");
        var material=new HashSet<>(a.evidenceLimitations()); material.addAll(a.affordability().findings()); material.addAll(a.affordability().blockers());
        a.ai().advisory().findings().forEach(f->material.add(f.code().name())); a.ai().advisory().inconsistencies().forEach(f->material.add(f.code().name()));
        a.ai().advisory().limitations().forEach(l->material.add(l.name()));
        if(!r.financialFactsConfirmed() || !r.declaredExpensesAndDebtsReviewed() || !r.authenticityReviewed()
            || !r.acknowledgedDocuments().containsAll(a.documentIds()) || !r.acknowledgedFindings().containsAll(material))
            throw LoanException.conflict("MEANINGFUL_REVIEW_REQUIRED","Reviewer must confirm financial facts, obligations, authenticity review and acknowledge every document and material finding");
    }
}

