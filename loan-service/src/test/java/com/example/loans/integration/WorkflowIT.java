package com.example.loans.integration;

import com.example.loans.*;
import com.example.loans.document.MalwareScanner;
import com.example.loans.domain.enums.ApplicationState;
import com.example.loans.dto.request.*;
import com.example.loans.exception.LoanException;
import com.example.loans.integration.ai.CreditAssessmentAiService;
import com.example.loans.integration.ledger.LedgerClient;
import com.example.loans.domain.repository.MappingRepository;
import com.example.loans.service.*;
import com.example.loans.security.Actor;
import org.junit.jupiter.api.*;
import org.springframework.test.context.bean.override.mockito.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.client.ResourceAccessException;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class WorkflowIT extends LoanPostgresTest {
    @MockitoBean MalwareScanner scanner;
    @MockitoBean LedgerClient ledger;
    @MockitoSpyBean CreditAssessmentAiService ai;
    @MockitoSpyBean MappingRepository mappings;
    @MockitoSpyBean ResourceAccess accessSpy;
    private final Map<UUID,LedgerClient.Account> remote=new ConcurrentHashMap<>();
    @org.springframework.beans.factory.annotation.Autowired ManualExtractionService manual;
    @org.springframework.beans.factory.annotation.Autowired ComplianceReviewService compliance;
    @BeforeEach void providers() {
        jdbc.update("UPDATE origination.durable_job SET state='DONE',lease_until=NULL WHERE state IN ('READY','RUNNING')");
        when(scanner.scan(any())).thenReturn(MalwareScanner.Result.CLEAN);
        when(ledger.provision(any())).thenAnswer(call -> account(call.getArgument(0)));
    }
    private LedgerClient.Account account(UUID loan) {
        return remote.computeIfAbsent(loan,l->new LedgerClient.Account(UUID.randomUUID(),config.getLedger().getId(),"loan:"+l+":principal","ASSET","DEBIT","ACTIVE","SEK"));
    }
    private void due() { jdbc.update("UPDATE origination.durable_job SET available_at=now() WHERE state='READY'"); }
    @Test void completeFlowIsHumanReviewedAndProvisioningCreatesNoActiveLoan() {
        var app=ready(); assertThat(app.state()).isEqualTo(ApplicationState.MANUAL_REVIEW);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM origination.loan WHERE application_id=?",Integer.class,app.id())).isZero();
        UUID loan=approve(app); assertThat(loans.get(TestFixtures.APPLICANT,loan).lifecycle()).isEqualTo("PENDING_LEDGER_ACCOUNT");
        assertThat(worker.runOne("PROVISION")).isTrue();
        assertThat(loans.get(TestFixtures.APPLICANT,loan).lifecycle()).isEqualTo("AWAITING_AGREEMENT");
        assertThat(loans.get(TestFixtures.APPLICANT,loan).agreementStatus()).isEqualTo("NOT_ACCEPTED");
        assertThat(loans.terms(TestFixtures.APPLICANT,loan).principal()).isEqualTo("100000.00");
        assertThat(loans.mapping(TestFixtures.APPLICANT,loan).purpose()).isEqualTo("PRINCIPAL_RECEIVABLE");
        assertThat(jdbc.queryForObject("SELECT draft FROM origination.application WHERE id=?",String.class,app.id())).startsWith("v1:").doesNotContain("SYNTHETIC-ID");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM origination.audit_event WHERE application_id=? AND action='LOAN_APPROVED'",Integer.class,app.id())).isOne();
        assertThatThrownBy(()->jdbc.update("UPDATE origination.loan_terms SET principal=1 WHERE loan_id=?",loan)).isInstanceOf(org.springframework.dao.DataAccessException.class);
    }
    @Test void repeatedConcurrentApprovalCreatesExactlyOneLoanAndReplaysDecision() throws Exception {
        var app=ready(); var request=TestFixtures.approval(applications.assessment(TestFixtures.OFFICER,app.id())); String key=key();
        var result=race(()->decisions.decide(TestFixtures.OFFICER,app.id(),app.version(),key,request),
            ()->decisions.decide(TestFixtures.OFFICER,app.id(),app.version(),key,request));
        assertThat(result).allSatisfy(o->assertThat(o.error()).isNull());
        assertThat(result.getFirst().value()).isEqualTo(result.getLast().value());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM origination.loan WHERE application_id=?",Integer.class,app.id())).isOne();
        assertThatThrownBy(()->decisions.decide(TestFixtures.OFFICER,app.id(),app.version(),key,new DecisionRequest(DecisionRequest.Outcome.REJECT,List.of("OTHER"),"Other","Other",Set.of(),Set.of(),false,false,false))).isInstanceOf(LoanException.class).hasMessageContaining("different");
    }
    @Test void approvalRacesWithdrawalOnSeparateConnectionsWithoutConflictingOutcomes() throws Exception {
        var app=ready(); var request=TestFixtures.approval(applications.assessment(TestFixtures.OFFICER,app.id()));
        var barrier=new CyclicBarrier(2); var backends=ConcurrentHashMap.<Integer>newKeySet();
        doAnswer(call->{
            backends.add(jdbc.queryForObject("SELECT pg_backend_pid()",Integer.class));
            barrier.await(10,TimeUnit.SECONDS); return call.callRealMethod();
        }).when(accessSpy).application(any(),eq(app.id()),eq(true));
        var result=race(()->decisions.decide(TestFixtures.OFFICER,app.id(),app.version(),key(),request),
            ()->applications.withdraw(TestFixtures.APPLICANT,app.id(),app.version(),key()));
        assertThat(backends).hasSize(2);
        assertThat(result.stream().filter(o->o.error()==null)).hasSize(1);
        assertThat(applications.get(TestFixtures.APPLICANT,app.id()).state()).isIn(ApplicationState.APPROVED,ApplicationState.WITHDRAWN);
    }
    @Test void concurrentDifferentDecisionKeysStillCreateOnlyOneLoan() throws Exception {
        var app=ready(); var request=TestFixtures.approval(applications.assessment(TestFixtures.OFFICER,app.id()));
        var result=race(()->decisions.decide(TestFixtures.OFFICER,app.id(),app.version(),key(),request),
            ()->decisions.decide(TestFixtures.OFFICER,app.id(),app.version(),key(),request));
        assertThat(result.stream().filter(o->o.error()==null)).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM origination.loan WHERE application_id=?",Integer.class,app.id())).isOne();
    }
    @Test void outageLostResponseAndLocalMappingFailureRecoverTheSameApprovedLoan() {
        var app=ready(); UUID loan=approve(app);
        doThrow(new ResourceAccessException("synthetic outage")).when(ledger).provision(loan);
        worker.runOne("PROVISION");
        assertThat(loans.get(TestFixtures.APPLICANT,loan).lifecycle()).isEqualTo("PENDING_LEDGER_ACCOUNT");
        AtomicBoolean first=new AtomicBoolean(true);
        doAnswer(call->{var result=account(loan); if(first.getAndSet(false)) throw new ResourceAccessException("synthetic lost response"); return result;}).when(ledger).provision(loan);
        due(); worker.runOne("PROVISION");
        doThrow(new org.springframework.dao.DataAccessResourceFailureException("synthetic local mapping failure")).when(mappings).saveAndFlush(any());
        due(); worker.runOne("PROVISION");
        assertThat(mappingRepository.findById(loan)).isEmpty(); assertThat(remote).containsKey(loan);
        reset(mappings);
        due(); worker.runOne("PROVISION");
        assertThat(loans.mapping(TestFixtures.APPLICANT,loan).accountId()).isEqualTo(remote.get(loan).id());
        assertThat(loans.get(TestFixtures.APPLICANT,loan).lifecycle()).isEqualTo("AWAITING_AGREEMENT");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM origination.loan WHERE application_id=?",Integer.class,app.id())).isOne();
    }
    @Test void expiredLeaseFencesStaleWorkerAndReconciliationRecoversStuckProvisioning() {
        var app=ready(); UUID loan=approve(app);
        var old=jobs.claim("PROVISION").orElseThrow();
        jdbc.update("UPDATE origination.durable_job SET lease_until=now()-interval '1 second' WHERE id=?",old.id());
        var fresh=jobs.claim("PROVISION").orElseThrow();
        assertThat(fresh.fence()).isGreaterThan(old.fence());
        assertThat(jobs.finish(old,()->{throw new AssertionError("stale worker must not commit");})).isFalse();
        worker.process(fresh);
        assertThat(mappingRepository.findById(loan)).isPresent();
        var app2=ready(); UUID loan2=approve(app2);
        jdbc.update("UPDATE origination.durable_job SET state='FAILED' WHERE target_id=?",loan2);
        operations.reconcile(TestFixtures.OPERATIONS,key()); while(worker.runOne("PROVISION")) {}
        assertThat(mappingRepository.findById(loan2)).isPresent();
    }
    @Test void missingDocumentsAndAffordabilityConcernsCannotBeApproved() {
        var missing=create(); applications.submit(TestFixtures.APPLICANT,missing.id(),missing.version(),key()); worker.runOne("ASSESS");
        var status=applications.get(TestFixtures.APPLICANT,missing.id());
        assertThat(status.state()).isEqualTo(ApplicationState.DOCUMENTS_REQUIRED);
        assertThatThrownBy(()->approve(status)).isInstanceOf(LoanException.class);
        var concern=ready("10000.00","4000.00"); var assessment=applications.assessment(TestFixtures.OFFICER,concern.id());
        assertThat(assessment.ai().advisory().outcome()).isEqualTo(CreditAssessmentAiService.Outcome.AFFORDABILITY_CONCERNS);
        assertThat(concern.state()).isEqualTo(ApplicationState.MANUAL_REVIEW);
        assertThatThrownBy(()->approve(concern)).isInstanceOf(LoanException.class).hasMessageContaining("concerns");
    }
    @Test void verificationAndReviewAcknowledgementsAreMandatory() {
        var app=ready(); var a=applications.assessment(TestFixtures.OFFICER,app.id()); var full=TestFixtures.approval(a);
        var missing=new DecisionRequest(full.outcome(),full.reasonCodes(),full.explanation(),full.applicantExplanation(),Set.of(),Set.of(),false,false,false);
        assertThatThrownBy(()->decisions.decide(TestFixtures.OFFICER,app.id(),app.version(),key(),missing)).hasMessageContaining("Reviewer");
        config.getProduct().setEligible(false);
        try { assertThatThrownBy(()->approve(app)).hasMessageContaining("no longer eligible"); }
        finally { config.getProduct().setEligible(true); }
    }
    @Test void providerFailureIsAssessmentFailureNeverCreditRejection() {
        doThrow(new ResourceAccessException("synthetic AI timeout")).when(ai).assess(any());
        var app=ready();
        for(int i=1;i<config.getMaxAttempts();i++) { due(); worker.runOne("ASSESS"); }
        assertThat(applications.get(TestFixtures.APPLICANT,app.id()).state()).isEqualTo(ApplicationState.ASSESSMENT_FAILED);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM origination.credit_decision WHERE application_id=?",Integer.class,app.id())).isZero();
    }
    @Test void staleResultAfterWithdrawalCannotRestoreReviewOrEnableApproval() throws Exception {
        var app=create(); applications.submit(TestFixtures.APPLICANT,app.id(),app.version(),key());
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        doAnswer(call->{entered.countDown(); if(!release.await(10,TimeUnit.SECONDS)) throw new AssertionError(); return call.callRealMethod();}).when(ai).assess(any());
        try(var pool=Executors.newSingleThreadExecutor()) {
            var processing=pool.submit(()->worker.runOne("ASSESS")); assertThat(entered.await(10,TimeUnit.SECONDS)).isTrue();
            var current=applications.get(TestFixtures.APPLICANT,app.id());
            applications.withdraw(TestFixtures.APPLICANT,app.id(),current.version(),key());
            release.countDown(); processing.get(20,TimeUnit.SECONDS);
        }
        assertThat(applications.get(TestFixtures.APPLICANT,app.id()).state()).isEqualTo(ApplicationState.WITHDRAWN);
        assertThat(assessmentRepository.findByApplicationIdAndRevision(app.id(),1)).isEmpty();
    }
    @Test void additionalEvidenceCreatesNewRevisionAndOldAssessmentRemainsImmutable() {
        var app=ready(); var old=assessmentRepository.findByApplicationIdAndRevision(app.id(),1).orElseThrow().getPayload();
        applications.edit(TestFixtures.APPLICANT,app.id(),app.version(),TestFixtures.input("31000.00","4000.00"));
        var draft=applications.get(TestFixtures.APPLICANT,app.id());
        assertThat(draft.state()).isEqualTo(ApplicationState.DRAFT);
        assertThatThrownBy(()->approve(draft)).isInstanceOf(LoanException.class);
        applications.submit(TestFixtures.APPLICANT,draft.id(),draft.version(),key()); worker.runOne("ASSESS");
        assertThat(applications.get(TestFixtures.APPLICANT,app.id()).revision()).isEqualTo(2);
        assertThat(assessmentRepository.findByApplicationIdAndRevision(app.id(),1).orElseThrow().getPayload()).isEqualTo(old);
        assertThat(applications.assessment(TestFixtures.OFFICER,app.id()).affordability().blockers()).contains("DECLARED_INCOME_MISMATCH");
    }
    @Test void quarantinedAndUnscannedEvidenceCannotBeDownloadedOrApproved() {
        when(scanner.scan(any())).thenReturn(MalwareScanner.Result.UNAVAILABLE);
        var app=ready();
        var doc=documents.list(TestFixtures.APPLICANT,app.id()).getFirst();
        assertThat(doc.scanStatus()).isEqualTo("UNAVAILABLE");
        assertThatThrownBy(()->documents.content(TestFixtures.APPLICANT,app.id(),doc.id())).hasMessageContaining("security scan");
        assertThat(app.state()).isEqualTo(ApplicationState.DOCUMENTS_REQUIRED);
    }
    @Test void ownershipRolesCrossTenantAndSafeProjectionsAreEnforcedOverHttp() throws Exception {
        var app=ready();
        mvc.perform(get("/api/v1/loan-applications/"+app.id())).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/loan-applications/"+app.id()).with(jwt().jwt(j->j.issuer("loan-local").subject("other").claim("tenant","demo")).authorities(new SimpleGrantedAuthority("ROLE_APPLICANT")))).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/loan-applications/"+app.id()).with(jwt().jwt(j->j.issuer("loan-local").subject("synthetic-officer").claim("tenant","other")).authorities(new SimpleGrantedAuthority("ROLE_LOAN_OFFICER")))).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/loan-applications/"+app.id()+"/assessment").with(jwt().jwt(j->j.issuer("loan-local").subject("synthetic-applicant").claim("tenant","demo")).authorities(new SimpleGrantedAuthority("ROLE_APPLICANT")))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/loan-applications/"+app.id()).with(jwt().jwt(j->j.issuer("loan-local").subject("synthetic-applicant").claim("tenant","demo")).authorities(new SimpleGrantedAuthority("ROLE_APPLICANT"))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.verifications").doesNotExist()).andExpect(jsonPath("$.assessment").doesNotExist());
    }
    @Test void creationSubmissionIdempotencyAndReconsiderationAudit() {
        var input=TestFixtures.input(); String key=key(); var a=applications.create(TestFixtures.APPLICANT,key,input);
        assertThat(applications.create(TestFixtures.APPLICANT,key,input).id()).isEqualTo(a.id());
        assertThatThrownBy(()->applications.create(TestFixtures.APPLICANT,key,TestFixtures.input("31000.00","4000.00"))).hasMessageContaining("different");
        String submit=key(); applications.submit(TestFixtures.APPLICANT,a.id(),a.version(),submit);
        assertThat(applications.submit(TestFixtures.APPLICANT,a.id(),a.version(),submit).revision()).isEqualTo(1);
        UUID review=applications.reviewRequest(TestFixtures.APPLICANT,a.id(),key(),new ReviewRequest("CORRECTION","Synthetic request to correct information"));
        assertThat(review).isNotNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM origination.audit_event WHERE application_id=? AND action='HUMAN_RECONSIDERATION_REQUESTED'",Integer.class,a.id())).isOne();
    }
    @Test void manualExtractionCreatesReplacementAndANewAssessmentRevision() {
        var app=create();
        var uploaded=documents.upload(TestFixtures.APPLICANT,app.id(),app.version(),key(),
            new DocumentService.UploadMetadata("PAYSLIP",LocalDate.now().minusDays(30),LocalDate.now().minusDays(1),null),TestFixtures.pdf());
        worker.runOne("DOCUMENT");
        assertThat(documents.get(TestFixtures.OFFICER,app.id(),uploaded.id()).processingStatus()).isEqualTo("MANUAL_REVIEW");
        var current=applications.get(TestFixtures.APPLICANT,app.id());
        UUID replacement=manual.extract(TestFixtures.OFFICER,app.id(),uploaded.id(),current.version(),key(),
            new ManualExtractionRequest("Human transcribed synthetic image",List.of(new ManualExtractionRequest.Fact("net_income","30000.00","SEK","MONTHLY",1,1))));
        var reviewed=documents.get(TestFixtures.OFFICER,app.id(),replacement);
        assertThat(reviewed.supersedes()).isEqualTo(uploaded.id());
        assertThat(reviewed.evidence().facts().getFirst().provenance()).isEqualTo(com.example.loans.document.FinancialEvidence.Provenance.REVIEWER_CONFIRMED);
        assertThat(documents.get(TestFixtures.OFFICER,app.id(),uploaded.id()).processingStatus()).isEqualTo("MANUAL_REVIEW");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM origination.manual_extraction_review WHERE output_document=?",Integer.class,replacement)).isOne();
    }
    @Test void pepDeclarationRequiresSeparateComplianceReviewAndDoesNotChangeAffordability() {
        var original=TestFixtures.input("30000.00","4000.00");
        var input=new ApplicationInput(original.productId(),original.productVersion(),original.requestedAmount(),original.currency(),original.termMonths(),original.purpose(),
            original.identity(),original.employment(),original.income(),original.expenses(),original.debts(),
            new ApplicationInput.Kyc("demo-1",Instant.now().minusSeconds(1),"Personal loan","Employment",List.of("SE"),true),original.privacy());
        var app=applications.create(TestFixtures.APPLICANT,key(),input);
        for(String category:List.of("PAYSLIP","TAX_ASSESSMENT")) {
            app=applications.get(TestFixtures.APPLICANT,app.id());
            documents.upload(TestFixtures.APPLICANT,app.id(),app.version(),key(),new DocumentService.UploadMetadata(category,LocalDate.now().minusDays(30),LocalDate.now().minusDays(1),null),TestFixtures.pdf("net_income=30000.00 SEK MONTHLY"));
        }
        while(worker.runOne("DOCUMENT")) {}
        app=applications.get(TestFixtures.APPLICANT,app.id());applications.submit(TestFixtures.APPLICANT,app.id(),app.version(),key());worker.runOne("ASSESS");
        app=applications.get(TestFixtures.APPLICANT,app.id());
        var assessment=applications.assessment(TestFixtures.OFFICER,app.id());
        assertThat(assessment.affordability().blockers()).isEmpty();
        var before=app;
        assertThatThrownBy(()->approve(before)).hasMessageContaining("identity, compliance");
        var reviewer=new Actor("demo","synthetic-compliance",Set.of("COMPLIANCE_REVIEWER"));
        compliance.review(reviewer,app.id(),app.version(),key(),new ComplianceReviewRequest(ComplianceReviewRequest.Outcome.SATISFIED,"synthetic-compliance-review","Separate human compliance review cleared the demo declaration"));
        var refreshed=applications.get(TestFixtures.APPLICANT,app.id());
        assertThat(approve(refreshed)).isNotNull();
    }
    @Test void legalHoldPreventsErasureAndPrivacyPurgeRetainsMinimalAuditMetadata() {
        var app=create();applications.submit(TestFixtures.APPLICANT,app.id(),app.version(),key());worker.runOne("ASSESS");
        app=applications.get(TestFixtures.APPLICANT,app.id());applications.withdraw(TestFixtures.APPLICANT,app.id(),app.version(),key());
        app=applications.get(TestFixtures.APPLICANT,app.id());
        var reviewer=new Actor("demo","synthetic-compliance",Set.of("COMPLIANCE_REVIEWER"));
        operations.legalHold(reviewer,app.id(),app.version(),true);
        assertThat(jdbc.queryForObject("SELECT origination.purge_application_payload(?,now()+interval '1 day')",Boolean.class,app.id())).isFalse();
        app=applications.get(TestFixtures.APPLICANT,app.id());operations.legalHold(reviewer,app.id(),app.version(),false);
        assertThat(jdbc.queryForObject("SELECT origination.purge_application_payload(?,now()+interval '1 day')",Boolean.class,app.id())).isTrue();
        assertThat(jdbc.queryForObject("SELECT payload FROM origination.application_revision WHERE application_id=?",String.class,app.id())).isNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM origination.audit_event WHERE application_id=?",Integer.class,app.id())).isGreaterThan(0);
    }
    @Test void malformedOrUnacknowledgedInputAndInvalidMoneyAreRejectedOverHttp() throws Exception {
        var node=new com.fasterxml.jackson.databind.ObjectMapper().readTree(json.write(TestFixtures.input()));
        ((com.fasterxml.jackson.databind.node.ObjectNode)node).put("requestedAmount",100000);
        mvc.perform(post("/api/v1/loan-applications").with(jwt().jwt(j->j.issuer("loan-local").subject("synthetic-applicant").claim("tenant","demo")).authorities(new SimpleGrantedAuthority("ROLE_APPLICANT")))
            .header("Idempotency-Key",key()).contentType("application/json").content(node.toString())).andExpect(status().isBadRequest());
        ((com.fasterxml.jackson.databind.node.ObjectNode)node).put("requestedAmount","100000.001");
        mvc.perform(post("/api/v1/loan-applications").with(jwt().jwt(j->j.issuer("loan-local").subject("synthetic-applicant").claim("tenant","demo")).authorities(new SimpleGrantedAuthority("ROLE_APPLICANT")))
            .header("Idempotency-Key",key()).contentType("application/json").content(node.toString())).andExpect(status().isUnprocessableEntity());
    }
    @Test void configuredAllowlistAlsoRestrictsTheFactsSentToAi() {
        var original=new LinkedHashSet<>(config.getProduct().getUnderwritingInputs());
        config.getProduct().setUnderwritingInputs(Set.of("residualIncome"));
        try {
            ready();
            var supplied=org.mockito.ArgumentCaptor.forClass(CreditAssessmentAiService.Input.class);
            verify(ai).assess(supplied.capture());
            assertThat(supplied.getValue().financialInputs()).containsOnlyKeys("residualIncome");
            assertThat(supplied.getValue().facts()).isEmpty();
        } finally { config.getProduct().setUnderwritingInputs(original); }
    }
    @Test void staleAndMalformedEvidenceRequireReview() {
        var app=create();
        for(String category:List.of("PAYSLIP","TAX_ASSESSMENT")) {
            app=applications.get(TestFixtures.APPLICANT,app.id());
            LocalDate end=LocalDate.now().minusDays(category.equals("PAYSLIP")?200:1);
            documents.upload(TestFixtures.APPLICANT,app.id(),app.version(),key(),new DocumentService.UploadMetadata(category,end.minusDays(30),end,null),TestFixtures.pdf("net_income=30000.00 SEK MONTHLY"));
        }
        while(worker.runOne("DOCUMENT")) {}
        app=applications.get(TestFixtures.APPLICANT,app.id());applications.submit(TestFixtures.APPLICANT,app.id(),app.version(),key());worker.runOne("ASSESS");
        assertThat(applications.assessment(TestFixtures.OFFICER,app.id()).affordability().blockers()).contains("STALE_DOCUMENT","REQUIRED_PAYSLIP_EVIDENCE");
        var malformed=create();
        var d=documents.upload(TestFixtures.APPLICANT,malformed.id(),malformed.version(),key(),new DocumentService.UploadMetadata("PAYSLIP",LocalDate.now().minusDays(30),LocalDate.now().minusDays(1),null),"%PDF-1.4\\nmalformed".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        worker.runOne("DOCUMENT");
        assertThat(documents.get(TestFixtures.OFFICER,malformed.id(),d.id()).evidence().limitations()).contains("MALFORMED_OR_RESOURCE_LIMITED_DOCUMENT","MANUAL_EXTRACTION_REQUIRED");
    }
    @Test void infectedEvidenceCannotBeDownloadedOrUsedForApproval() {
        when(scanner.scan(any())).thenReturn(MalwareScanner.Result.INFECTED);
        var app=ready(); var doc=documents.list(TestFixtures.APPLICANT,app.id()).getFirst();
        assertThat(doc.scanStatus()).isEqualTo("INFECTED");
        assertThat(app.state()).isEqualTo(ApplicationState.DOCUMENTS_REQUIRED);
        assertThatThrownBy(()->documents.content(TestFixtures.APPLICANT,app.id(),doc.id())).hasMessageContaining("security scan");
    }
    @Test void globalBackpressureAlsoAppliesToManualRevivalWithoutBreakingDuplicateEnqueue() {
        int original=config.getMaxQueuedJobs();config.setMaxQueuedJobs(1);
        UUID first=UUID.randomUUID(),second=UUID.randomUUID();
        try {
            jobs.transaction(()->{jobs.enqueue("PROVISION",first,1);return null;});
            jobs.transaction(()->{jobs.enqueue("PROVISION",first,1);return null;});
            assertThatThrownBy(()->jobs.transaction(()->{jobs.enqueue("PROVISION",second,1);return null;})).isInstanceOf(LoanException.class);
            jdbc.update("UPDATE origination.durable_job SET state='FAILED' WHERE target_id=?",first);
            jobs.transaction(()->{jobs.enqueue("PROVISION",second,1);return null;});
            assertThatThrownBy(()->jobs.transaction(()->{jobs.retry("PROVISION",first,1);return null;})).isInstanceOf(LoanException.class);
        } finally {config.setMaxQueuedJobs(original);}
    }
}

