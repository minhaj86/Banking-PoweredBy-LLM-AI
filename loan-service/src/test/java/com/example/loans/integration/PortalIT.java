package com.example.loans.integration;

import com.example.loans.TestFixtures;
import com.example.loans.document.MalwareScanner;
import com.example.loans.dto.request.*;
import com.example.loans.exception.LoanException;
import com.example.loans.integration.ledger.LedgerClient;
import com.example.loans.security.Actor;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PortalIT extends LoanPostgresTest {
    @MockitoBean MalwareScanner scanner;
    @MockitoBean LedgerClient ledger;
    @Test void incompleteDraftIsEncryptedVersionedAndIdempotent() {
        var partial=new ApplicationInput(config.getProduct().getId(),1,"", "SEK",12,"",null,null,null,null,List.of(),null,null);
        var draft=applications.createDraft(TestFixtures.APPLICANT,key(),partial);
        assertThat(draft.version()).isEqualTo(applicationRepository.findById(draft.id()).orElseThrow().getVersion());
        assertThat(draft.application().identity()).isNull();
        assertThat(jdbc.queryForObject("SELECT draft FROM origination.application WHERE id=?",String.class,draft.id())).startsWith("v1:").doesNotContain("personal-sek");
        String intent=key();var input=TestFixtures.input("30000.00","4000.00");var saved=applications.saveDraft(TestFixtures.APPLICANT,draft.id(),draft.version(),intent,input);
        var replay=applications.saveDraft(TestFixtures.APPLICANT,draft.id(),draft.version(),intent,input);
        assertThat(replay.version()).isEqualTo(saved.version());
        assertThatThrownBy(()->applications.saveDraft(TestFixtures.APPLICANT,draft.id(),draft.version(),key(),partial)).isInstanceOf(LoanException.class).hasMessageContaining("changed");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM origination.audit_event WHERE application_id=? AND action='APPLICATION_EDITED'",Integer.class,draft.id())).isOne();
    }
    @Test void queueIsTenantScopedFilteredAndSupportsOwnViewForStaff() {
        var actor=new Actor("queue-"+key(),"reviewer-owner",Set.of("APPLICANT","LOAN_OFFICER"));
        var other=new Actor(actor.tenant(),"other-owner",Set.of("APPLICANT"));
        applications.create(actor,key(),TestFixtures.input("30000.00","4000.00"));applications.create(other,key(),TestFixtures.input("30000.00","4000.00"));
        assertThat(applications.queue(actor,0,10,"DRAFT","personal-sek",null,null,"oldest",null,"mine").get("total")).isEqualTo(1L);
        assertThat(applications.queue(actor,0,10,"DRAFT","personal-sek",null,null,"newest",null,"staff").get("total")).isEqualTo(2L);
        assertThat(applications.queue(new Actor("foreign","x",Set.of("LOAN_OFFICER")),0,10,null,null,null,null,"updated",null,"staff").get("total")).isEqualTo(0L);
        assertThatThrownBy(()->applications.queue(actor,0,10,null,null,null,null,"draft",null,"staff")).isInstanceOf(LoanException.class);
        assertThatThrownBy(()->applications.queue(actor,0,10,null,null,null,null,"oldest","name search","staff")).isInstanceOf(LoanException.class);
    }
    @Test void staffCannotAssessOrDecideTheirOwnApplication() {
        var actor=new Actor("self-review","officer",Set.of("APPLICANT","LOAN_OFFICER"));
        var app=applications.create(actor,key(),TestFixtures.input("30000.00","4000.00"));
        assertThatThrownBy(()->applications.assessment(actor,app.id())).isInstanceOf(AccessDeniedException.class);
        var decision=new DecisionRequest(DecisionRequest.Outcome.REQUEST_INFORMATION,List.of("EVIDENCE_REQUIRED"),"Review","Review",Set.of(),Set.of(),false,false,false);
        assertThatThrownBy(()->decisions.decide(actor,app.id(),app.version(),key(),decision)).isInstanceOf(AccessDeniedException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM origination.credit_decision WHERE application_id=?",Integer.class,app.id())).isZero();
    }
    @Test void staffApplicantMembershipDoesNotPermitApplicantMutationsOnSomeoneElsesApplication(){
        var owner=new Actor("multi-role","owner",Set.of("APPLICANT"));var staff=new Actor("multi-role","officer",Set.of("APPLICANT","LOAN_OFFICER"));
        var app=applications.create(owner,key(),TestFixtures.input("30000.00","4000.00"));
        assertThat(applications.get(staff,app.id()).id()).isEqualTo(app.id());
        assertThatThrownBy(()->applications.submit(staff,app.id(),app.version(),key())).isInstanceOf(LoanException.class);
        assertThatThrownBy(()->applications.withdraw(staff,app.id(),app.version(),key())).isInstanceOf(LoanException.class);
        assertThatThrownBy(()->applications.reviewRequest(staff,app.id(),key(),new ReviewRequest("CORRECTION","Applicant must request this themselves"))).isInstanceOf(LoanException.class);
        assertThat(applications.get(owner,app.id()).state()).isEqualTo(com.example.loans.domain.enums.ApplicationState.DRAFT);
    }
    @Test void realIdentityClaimsRequireEmailAndStaffAssuranceAndPreserveTenantOwnership() throws Exception {
        String issuer="https://issuer.example/realms/loans";String subject="subject-"+key();var actor=new Actor("iam-demo",Actor.identityKey(issuer,subject),Set.of("APPLICANT"));
        var app=applications.create(actor,key(),TestFixtures.input("30000.00","4000.00"));
        mvc.perform(post("/api/v1/loan-applications/"+app.id()+"/submit").with(jwt().jwt(j->j.issuer(issuer).subject(subject).claim("tenant","iam-demo").claim("email_verified",false)).authorities(new SimpleGrantedAuthority("ROLE_APPLICANT"))).header("If-Match",app.version()).header("Idempotency-Key",key())).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/loan-applications/"+app.id()).with(jwt().jwt(j->j.issuer(issuer).subject("staff").claim("tenant","iam-demo").claim("acr","loan-basic")).authorities(new SimpleGrantedAuthority("ROLE_LOAN_OFFICER")))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/loan-applications/"+app.id()).with(jwt().jwt(j->j.issuer(issuer).subject("staff").claim("tenant","iam-demo").claim("acr","loan-staff")).authorities(new SimpleGrantedAuthority("ROLE_LOAN_OFFICER")))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/loan-applications/"+app.id()).with(jwt().jwt(j->j.issuer(issuer).subject("admin").claim("tenant","iam-demo").claim("acr","loan-staff")).authorities(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN")))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/loan-applications/"+app.id()).with(jwt().jwt(j->j.issuer(issuer).subject("other").claim("tenant","iam-demo")).authorities(new SimpleGrantedAuthority("ROLE_APPLICANT")))).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/loan-applications/"+app.id()).with(jwt().jwt(j->j.issuer(issuer).subject(subject).claim("tenant","other-tenant")).authorities(new SimpleGrantedAuthority("ROLE_APPLICANT")))).andExpect(status().isNotFound());
        assertThat(Actor.identityKey(issuer,subject)).isNotEqualTo(Actor.identityKey(issuer+"-other",subject));
    }
}
