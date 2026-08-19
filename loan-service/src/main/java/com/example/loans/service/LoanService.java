package com.example.loans.service;

import com.example.loans.domain.repository.*;
import com.example.loans.domain.entity.*;
import com.example.loans.dto.request.ReviewRequest;
import com.example.loans.exception.LoanException;
import com.example.loans.mapper.Json;
import com.example.loans.security.Actor;
import com.example.loans.workflow.JobQueue;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service @RequiredArgsConstructor
public class LoanService {
    private final ResourceAccess access;
    private final TermsRepository terms;
    private final MappingRepository mappings;
    private final IdempotencyService idempotency;
    private final JobQueue jobs;
    private final AuditService audit;
    private final Json json;
    private final com.example.loans.integration.ledger.LedgerClient ledger;
    // Repository reads complete before the remote call; no database locks span HTTP.
    public com.example.loans.integration.ledger.LedgerClient.Balance balance(Actor actor,UUID id) {
        var mapping=mapping(actor,id);return ledger.balance(mapping.ledgerId(),mapping.accountId());
    }
    public record LoanView(UUID id,String publicReference,UUID applicationId,int approvedRevision,UUID decisionId,
        java.time.Instant approvedAt,String lifecycle,String provisioningStatus,String agreementStatus,long version) {}
    public record TermsView(String productId,int productVersion,String policyVersion,String principal,String currency,int termMonths,String nominalAnnualRate,String repaymentFrequency) {}
    public record MappingView(UUID ledgerId,UUID accountId,String purpose,String externalReference,String currency) {}
    @Transactional(readOnly=true) public LoanView get(Actor actor,UUID id) { return view(access.loan(actor,id,false)); }
    @Transactional(readOnly=true) public TermsView terms(Actor actor,UUID id) {
        access.loan(actor,id,false); var t=terms.findById(id).orElseThrow(LoanException::missing);
        return new TermsView(t.getProductId(),t.getProductVersion(),t.getPolicyVersion(),t.getPrincipal().toPlainString(),t.getCurrency(),t.getTermMonths(),t.getNominalAnnualRate().toPlainString(),t.getRepaymentFrequency());
    }
    @Transactional(readOnly=true) public AnnuityCalculator.Schedule schedule(Actor actor,UUID id) {
        access.loan(actor,id,false); return json.read(terms.findById(id).orElseThrow(LoanException::missing).getIndicativeSchedule(),AnnuityCalculator.Schedule.class);
    }
    @Transactional(readOnly=true) public MappingView mapping(Actor actor,UUID id) {
        access.loan(actor,id,false); var m=mappings.findById(id).orElseThrow(LoanException::missing);
        return new MappingView(m.getLedgerId(),m.getAccountId(),m.getPurpose(),m.getExternalReference(),m.getCurrency());
    }
    @Transactional public LoanView retry(Actor actor,UUID id,String key,ReviewRequest request) {
        actor.require("OPERATIONS");
        idempotency.execute(actor,"RETRY_PROVISION",id.toString(),key,request,()->{
            var loan=access.loan(actor,id,false);
            if(mappings.findById(id).isEmpty()) { jobs.retry("PROVISION",id,loan.getApprovedRevision()); loan.retrying(); }
            audit.record(actor,"PROVISIONING_RETRY_REQUESTED",loan.getApplicationId(),loan.getApprovedRevision(),id); return id;
        });
        return view(access.loan(actor,id,false));
    }
    private LoanView view(LoanRecord l) { return new LoanView(l.getId(),l.getPublicReference(),l.getApplicationId(),l.getApprovedRevision(),l.getDecisionId(),l.getApprovedAt(),l.getLifecycle(),l.getProvisioningStatus(),l.getAgreementStatus(),l.getVersion()); }
}

