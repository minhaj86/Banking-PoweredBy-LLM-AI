package com.example.loans.service;

import com.example.loans.domain.repository.*;
import com.example.loans.dto.request.ReviewRequest;
import com.example.loans.exception.LoanException;
import com.example.loans.security.Actor;
import com.example.loans.workflow.JobQueue;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service @RequiredArgsConstructor
public class OperationsService {
    private final JdbcTemplate jdbc;
    private final JobQueue jobs;
    private final ApplicationRepository applications;
    private final ResourceAccess access;
    private final IdempotencyService idempotency;
    private final AuditService audit;
    @Transactional(readOnly=true) public Map<String,Object> queue(Actor actor,int page,int size,String state) {
        actor.require("OPERATIONS");
        if(page<0||page>10000||size<1||size>100||!Set.of("READY","RUNNING","DONE","FAILED").contains(state))throw LoanException.invalid("INVALID_PAGE","Invalid job filter or page");
        String joins=" FROM origination.durable_job j LEFT JOIN origination.document d ON j.kind IN ('DOCUMENT','PURGE') AND d.id=j.target_id LEFT JOIN origination.loan l ON j.kind='PROVISION' AND l.id=j.target_id JOIN origination.application a ON a.id=CASE WHEN j.kind='ASSESS' THEN j.target_id ELSE coalesce(d.application_id,l.application_id) END WHERE a.tenant=? AND j.state=?";
        var rows=jdbc.queryForList("SELECT j.id,j.kind,j.state,j.attempts,j.correlation_id AS \"correlationId\",j.last_error AS \"lastError\",j.available_at AS \"availableAt\",a.id AS \"applicationId\",l.id AS \"loanId\""+joins+" ORDER BY j.updated_at DESC,j.id LIMIT ? OFFSET ?",actor.tenant(),state,size,(long)page*size);
        return Map.of("items",rows,"total",jdbc.queryForObject("SELECT count(*)"+joins,Long.class,actor.tenant(),state),"page",page,"size",size);
    }
    @Transactional public UUID reconcile(Actor actor,String key) {
        actor.require("OPERATIONS");
        return idempotency.execute(actor,"RECONCILE","loans",key,Map.of("tenant",actor.tenant()),()->{
            var rows=jdbc.query("""
                SELECT l.id,l.approved_revision FROM origination.loan l LEFT JOIN origination.ledger_account_mapping m ON m.loan_id=l.id
                WHERE l.tenant=? AND (m.loan_id IS NULL OR l.provisioning_status<>'COMPLETE') ORDER BY l.approved_at LIMIT 100
                """,(rs,n)->Map.entry(rs.getObject(1,UUID.class),rs.getInt(2)),actor.tenant());
            for(var row:rows) jobs.retry("PROVISION",row.getKey(),row.getValue());
            UUID id=UUID.randomUUID(); audit.record(actor,"PROVISIONING_RECONCILIATION",null,0,id); return id;
        });
    }
    @Transactional public UUID retryJob(Actor actor,UUID application,UUID jobId,String key,ReviewRequest request) {
        actor.require("OPERATIONS");
        return idempotency.execute(actor,"RETRY_JOB",jobId.toString(),key,request,()->{
            var app=access.application(actor,application,true);
            var view=jobs.views(application).stream().filter(v->v.id().equals(jobId)).findFirst().orElseThrow(LoanException::missing);
            if(app.getState().terminal() && !view.kind().equals("PROVISION")) throw LoanException.conflict("APPLICATION_FINAL","Final application evidence cannot be reprocessed");
            if(!view.state().equals("FAILED")) throw LoanException.conflict("JOB_NOT_FAILED","Only failed jobs may be manually retried");
            var target=jdbc.queryForMap("SELECT target_id,revision FROM origination.durable_job WHERE id=?",jobId);
            int revision=(Integer)target.get("revision");
            if(view.kind().equals("ASSESS") && revision!=app.getCurrentRevision()) throw LoanException.conflict("STALE_JOB","Cannot retry assessment for an older revision");
            jobs.retry(view.kind(),(UUID)target.get("target_id"),revision);
            if(view.kind().equals("DOCUMENT")) app.edit(app.getDraft());
            if(view.kind().equals("ASSESS")) jdbc.update("UPDATE origination.application SET state='SUBMITTED',version=version+1,updated_at=now() WHERE id=? AND state='ASSESSMENT_FAILED'",application);
            audit.record(actor,"JOB_RETRY_REQUESTED",application,revision,jobId); return jobId;
        });
    }
    @Transactional public void legalHold(Actor actor,UUID application,long expected,boolean hold) {
        actor.require("COMPLIANCE_REVIEWER"); var app=access.application(actor,application,true); access.expected(app.getVersion(),expected);
        if(hold && Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM origination.document WHERE application_id=? AND processing_status='PURGING' AND purged_at IS NULL)",Boolean.class,application))) throw LoanException.conflict("PURGE_IN_PROGRESS","Erasure has already started; legal hold cannot be applied to an in-flight deletion");
        app.hold(hold); audit.record(actor,hold?"LEGAL_HOLD_APPLIED":"LEGAL_HOLD_RELEASED",application,app.getCurrentRevision(),application);
    }
    @Transactional(readOnly=true) public List<Map<String,Object>> audit(Actor actor,UUID application) {
        actor.require("COMPLIANCE_REVIEWER","OPERATIONS","LOAN_OFFICER"); access.application(actor,application,false);
        return jdbc.queryForList("SELECT id,actor,action,revision,resource_id,occurred_at FROM origination.audit_event WHERE tenant=? AND application_id=? ORDER BY occurred_at DESC LIMIT 200",actor.tenant(),application);
    }
}

