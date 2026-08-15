package com.example.loans.workflow;

import com.example.loans.config.LoanProperties;
import com.example.loans.document.*;
import com.example.loans.domain.entity.*;
import com.example.loans.domain.enums.ApplicationState;
import com.example.loans.domain.repository.*;
import com.example.loans.dto.request.ApplicationInput;
import com.example.loans.dto.response.AssessmentView;
import com.example.loans.exception.LoanException;
import com.example.loans.integration.ai.CreditAssessmentAiService;
import com.example.loans.integration.creditbureau.CreditBureauProvider;
import com.example.loans.integration.kyc.KycProvider;
import com.example.loans.integration.ledger.LedgerClient;
import com.example.loans.integration.storage.DocumentStorage;
import com.example.loans.mapper.Json;
import com.example.loans.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

@Component @RequiredArgsConstructor @lombok.extern.slf4j.Slf4j
public class OriginationWorker {
    private final JobQueue jobs;
    private final ApplicationRepository applications;
    private final RevisionRepository revisions;
    private final DocumentRepository documents;
    private final AssessmentRepository assessments;
    private final LoanRepository loans;
    private final MappingRepository mappings;
    private final DocumentStorage storage;
    private final MalwareScanner scanner;
    private final DocumentExtractor extractor;
    private final AffordabilityService affordability;
    private final CreditAssessmentAiService ai;
    private final KycProvider kyc;
    private final com.example.loans.integration.kyc.ComplianceScreeningProvider screening;
    private final CreditBureauProvider bureau;
    private final LedgerClient ledger;
    private final LoanProperties config;
    private final Json json;
    private final AuditService audit;
    private final ParserExecutor parserExecutor;
    public boolean runOne(String kind) {
        var claim=jobs.claim(kind); if(claim.isEmpty()) return false;
        process(claim.get()); return true;
    }
    public void process(JobQueue.Claim c) {
        try {
            if(c.attempts()>config.getMaxAttempts()) throw LoanException.invalid("ATTEMPT_BUDGET_EXHAUSTED","Worker lease expired after the attempt budget");
            switch(c.kind()) {
                case "DOCUMENT" -> document(c);
                case "ASSESS" -> assess(c);
                case "PROVISION" -> provision(c);
                case "PURGE" -> purge(c);
                default -> throw new IllegalArgumentException("UNKNOWN_JOB_KIND");
            }
        } catch(RuntimeException ex) {
            boolean permanent=ex instanceof LoanException le && le.getStatus().is4xxClientError()
                || ex instanceof IllegalArgumentException || ex instanceof org.springframework.web.client.HttpClientErrorException;
            String code=ex instanceof LoanException le?le.getCode():ex instanceof org.springframework.web.client.HttpClientErrorException?"REMOTE_VALIDATION_FAILURE":
                ex instanceof IllegalArgumentException?"DOCUMENT_VALIDATION_FAILURE":"PROVIDER_OR_PROCESSING_FAILURE";
            log.warn("event=job_failed kind={} code={} exception={}",c.kind(),code,ex.getClass().getSimpleName());
            jobs.fail(c,code,permanent,() -> terminal(c,code));
        }
    }
    private void document(JobQueue.Claim c) {
        var d=jobs.transaction(()->documents.findById(c.target()).orElseThrow(LoanException::missing));
        if(!d.getUploadStatus().equals("COMPLETE") || d.getPurgedAt()!=null) { jobs.finish(c,()->{}); return; }
        byte[] bytes=storage.get(d.getObjectKey());
        if(bytes.length!=d.getSizeBytes() || !IdempotencyService.hash(bytes).equals(d.getChecksum())) throw LoanException.invalid("OBJECT_CHECKSUM_MISMATCH","Stored evidence does not match upload metadata");
        var scan=scanner.scan(bytes);
        FinancialEvidence evidence;
        String status;
        if(scan==MalwareScanner.Result.CLEAN) {
            evidence=parserExecutor.extract(()->extractor.extract(bytes,d));
            status=evidence.limitations().contains("MANUAL_EXTRACTION_REQUIRED")?"MANUAL_REVIEW":"EXTRACTED";
        } else { evidence=new FinancialEvidence(List.of(),List.of(scan==MalwareScanner.Result.INFECTED?"MALWARE_DETECTED":"SCANNER_UNAVAILABLE")); status="QUARANTINED"; }
        String resultStatus=status;
        jobs.finish(c,()->{
            var current=documents.findById(c.target()).orElseThrow();
            if(current.getPurgedAt()==null) {
                current.processed(scan.name(),resultStatus,json.write(evidence));
                var app=applications.findById(current.getApplicationId()).orElseThrow();
                audit.record(app.getTenant(),"worker","DOCUMENT_PROCESSED",app.getId(),c.revision(),current.getId());
            }
        });
    }
    private record Snapshot(ApplicationRecord application,ApplicationRevision revision,List<DocumentRecord> documents) {}
    private void assess(JobQueue.Claim c) {
        Snapshot snapshot=jobs.transaction(()->{
            var app=applications.lock(c.target()).orElseThrow(LoanException::missing);
            if(app.getCurrentRevision()!=c.revision() || app.getState().terminal() || app.getState()==ApplicationState.DRAFT) return null;
            if(app.getState()==ApplicationState.SUBMITTED) app.transition(ApplicationState.ASSESSMENT_PENDING);
            if(app.getState()!=ApplicationState.ASSESSMENT_PENDING) return null;
            var rev=revisions.findByApplicationIdAndRevision(c.target(),c.revision()).orElseThrow();
            UUID[] ids=json.read(rev.getDocumentIds(),UUID[].class);
            return new Snapshot(app,rev,documents.findAllById(List.of(ids)));
        });
        if(snapshot==null) { jobs.finish(c,()->{}); return; }
        var docs=snapshot.documents();
        if(docs.stream().anyMatch(d->Set.of("QUEUED","WAITING_UPLOAD").contains(d.getProcessingStatus()))) { jobs.defer(c); return; }
        var p=json.read(snapshot.revision().getPolicySnapshot(),LoanProperties.Product.class);
        var input=json.read(snapshot.revision().getPayload(),ApplicationInput.class);
        var facts=new ArrayList<FinancialEvidence.Fact>(); var limitations=new LinkedHashSet<String>(); var blockers=new LinkedHashSet<String>();
        LocalDate today=LocalDate.now(ZoneOffset.UTC);
        for(String category:p.getRequiredCategories()) {
            var matching=docs.stream().filter(d->d.getCategory().equals(category)&&d.getScanStatus().equals("CLEAN")&&d.getProcessingStatus().equals("EXTRACTED")
                && !d.getPeriodEnd().isBefore(today.minusDays(p.getEvidenceFreshnessDays()))&& !d.getPeriodEnd().isAfter(today)).toList();
            long periods=matching.stream().map(d->d.getPeriodStart()+":"+d.getPeriodEnd()).distinct().count();
            int required=category.equals("PAYSLIP")?p.getRequiredPayslips():1;
            if(periods<required) blockers.add("REQUIRED_"+category+"_EVIDENCE");
        }
        for(var d:docs) {
            if(!d.getScanStatus().equals("CLEAN") || !d.getUploadStatus().equals("COMPLETE")) blockers.add("DOCUMENT_SECURITY_OR_UPLOAD_GATE");
            if(!d.getProcessingStatus().equals("EXTRACTED")) blockers.add("DOCUMENT_EXTRACTION_REVIEW");
            if(d.getPeriodEnd().isBefore(today.minusDays(p.getEvidenceFreshnessDays()))) blockers.add("STALE_DOCUMENT");
            if(d.getEvidence()!=null) {
                var e=json.read(d.getEvidence(),FinancialEvidence.class); facts.addAll(e.facts()); limitations.addAll(e.limitations());
            }
        }
        if(facts.stream().anyMatch(f->!f.currency().equals("SEK"))) blockers.add("MIXED_EVIDENCE_CURRENCY");
        if(facts.stream().anyMatch(f->!f.frequency().equals("MONTHLY"))) blockers.add("NON_MONTHLY_EVIDENCE_REQUIRES_REVIEW");
        var math=affordability.assess(input,p,facts,Instant.now());
        blockers.addAll(math.blockers());
        math=new AffordabilityService.Result(math.monthlyNetIncome(),math.monthlyNonDebtExpenses(),math.existingMonthlyDebtRepayments(),math.proposedMonthlyInstalment(),
            math.residualIncome(),math.debtServiceToIncome(),math.stressedResidualIncome(),List.copyOf(blockers),math.findings(),math.facts(),math.schedule());
        var checks=new ArrayList<>(kyc.verify(input)); checks.add(screening.screen(input)); checks.add(bureau.verifyObligations(input));
        var approvedInputs=new LinkedHashMap<String,String>();
        Map<String,String> values=new LinkedHashMap<>();
        values.put("monthlyNetIncome",math.monthlyNetIncome()); values.put("monthlyNonDebtExpenses",math.monthlyNonDebtExpenses());
        values.put("existingMonthlyDebtRepayments",math.existingMonthlyDebtRepayments()); values.put("proposedMonthlyInstalment",math.proposedMonthlyInstalment());
        values.put("residualIncome",math.residualIncome()); values.put("debtServiceToIncome",math.debtServiceToIncome());
        p.getUnderwritingInputs().forEach(key->approvedInputs.put(key,values.get(key)));
        var aiFacts=facts.stream().filter(f->switch(f.field()) {
            case "net_income" -> p.getUnderwritingInputs().contains("monthlyNetIncome");
            case "debt_repayment" -> p.getUnderwritingInputs().contains("existingMonthlyDebtRepayments");
            case "non_debt_expenses" -> p.getUnderwritingInputs().contains("monthlyNonDebtExpenses");
            default -> false;
        }).toList();
        var aiResult=ai.assess(new CreditAssessmentAiService.Input(Collections.unmodifiableMap(approvedInputs),aiFacts,math.blockers(),math.findings()));
        var assessment=new AssessmentView(c.revision(),input.productId(),input.productVersion(),p.getPolicyVersion(),docs.stream().map(DocumentRecord::getId).toList(),
            math,aiResult,List.copyOf(checks),List.copyOf(limitations),Instant.now());
        jobs.finish(c,()->{
            var app=applications.lock(c.target()).orElseThrow();
            if(app.getCurrentRevision()!=c.revision() || app.getState()!=ApplicationState.ASSESSMENT_PENDING) return;
            if(assessments.findByApplicationIdAndRevision(c.target(),c.revision()).isEmpty()) assessments.saveAndFlush(AssessmentRecord.create(c.target(),c.revision(),p.getPolicyVersion(),input.productVersion(),json.write(assessment)));
            boolean evidenceMissing=assessment.affordability().blockers().stream().anyMatch(b->b.startsWith("REQUIRED_")||b.startsWith("DOCUMENT_")||b.startsWith("MISSING_")||b.equals("STALE_DOCUMENT"));
            app.transition(evidenceMissing?ApplicationState.DOCUMENTS_REQUIRED:ApplicationState.MANUAL_REVIEW);
            audit.record(app.getTenant(),"worker","ASSESSMENT_COMPLETED",app.getId(),c.revision(),app.getId());
        });
    }
    private void provision(JobQueue.Claim c) {
        var loan=jobs.transaction(()->loans.findById(c.target()).orElseThrow(LoanException::missing));
        // The same stable reference recovers remote success after lost response or local rollback.
        var account=ledger.provision(loan.getId());
        jobs.finish(c,()->{
            var current=loans.lock(loan.getId()).orElseThrow();
            var existing=mappings.findById(loan.getId());
            if(existing.isPresent() && (!existing.get().getAccountId().equals(account.id()) || !existing.get().getLedgerId().equals(account.ledgerId()))) throw LoanException.invalid("MAPPING_MISMATCH","Existing local mapping conflicts with remote account");
            if(existing.isEmpty()) mappings.saveAndFlush(LedgerAccountMapping.principal(loan.getId(),account.ledgerId(),account.id(),account.externalReference()));
            current.provisioned();
            audit.record(loan.getTenant(),"worker","LEDGER_ACCOUNT_PROVISIONED",loan.getApplicationId(),loan.getApprovedRevision(),loan.getId());
        });
    }
    private void purge(JobQueue.Claim c) {
        var doc=jobs.transaction(()-> {
            var d=documents.findById(c.target()).orElseThrow(LoanException::missing);
            var a=applications.lock(d.getApplicationId()).orElseThrow();
            if(a.isLegalHold() || (!d.getUploadStatus().equals("PENDING") && !a.getState().terminal())) return null;
            if(d.getPurgedAt()!=null) return null;
            d.purging(); return d;
        });
        if(doc==null) { jobs.defer(c); return; }
        var app=jobs.transaction(()->applications.findById(doc.getApplicationId()).orElseThrow());
        storage.delete(doc.getObjectKey());
        jobs.finish(c,()-> {
            var locked=applications.lock(app.getId()).orElseThrow();
            // Legal-hold changes reject an already committed PURGING intent.
            if(locked.isLegalHold()) throw LoanException.conflict("LEGAL_HOLD","Retention operation is on legal hold");
            documents.findById(doc.getId()).orElseThrow().purged();
            audit.record(app.getTenant(),"worker","DOCUMENT_PURGED",app.getId(),doc.getUploadedForRevision(),doc.getId());
        });
    }
    private void terminal(JobQueue.Claim c,String error) {
        switch(c.kind()) {
            case "ASSESS" -> applications.lock(c.target()).ifPresent(a->{
                if(a.getCurrentRevision()==c.revision() && a.getState()==ApplicationState.ASSESSMENT_PENDING) a.transition(ApplicationState.ASSESSMENT_FAILED);
            });
            case "PROVISION" -> loans.lock(c.target()).ifPresent(LoanRecord::failed);
            case "DOCUMENT" -> documents.findById(c.target()).ifPresent(d->d.processed(d.getScanStatus(),"FAILED",json.write(new FinancialEvidence(List.of(),List.of(error)))));
            default -> {}
        }
    }
}

