package com.example.loans.workflow;

import com.example.loans.config.LoanProperties;
import com.example.loans.exception.LoanException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

@Component @RequiredArgsConstructor
public class JobQueue {
    private final JdbcTemplate jdbc;
    private final PlatformTransactionManager manager;
    private final LoanProperties config;
    public record Claim(UUID id,String kind,UUID target,int revision,int attempts,long fence) {}
    public record View(UUID id,String kind,String state,int attempts,String lastError,Instant availableAt,String correlationId) {}
    public <T> T transaction(Supplier<T> work) {
        var tx=new TransactionTemplate(manager); tx.setTimeout(15);
        return tx.execute(status -> work.get());
    }
    public void enqueue(String kind,UUID target,int revision) {
        // Serialise admissions; the queue bound is global, not a per-instance estimate.
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(7128461)",Object.class);
        if(Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM origination.durable_job WHERE kind=? AND target_id=? AND revision=?)",Boolean.class,kind,target,revision))) return;
        checkCapacity();
        String correlation=Optional.ofNullable(org.slf4j.MDC.get("correlationId")).orElseGet(()->UUID.randomUUID().toString());
        jdbc.update("INSERT INTO origination.durable_job(id,kind,target_id,revision,state,correlation_id) VALUES(?,?,?,?,'READY',?) ON CONFLICT(kind,target_id,revision) DO NOTHING",UUID.randomUUID(),kind,target,revision,correlation);
    }
    private void checkCapacity() {
        Integer backlog=jdbc.queryForObject("SELECT count(*) FROM origination.durable_job WHERE state IN ('READY','RUNNING')",Integer.class);
        if(backlog>=config.getMaxQueuedJobs()) throw LoanException.unavailable("QUEUE_CAPACITY");
    }
    public Optional<Claim> claim(String kind) {
        return transaction(() -> jdbc.query("""
            WITH candidate AS (
              SELECT id FROM origination.durable_job WHERE kind=?
                AND ((state='READY' AND available_at<=clock_timestamp()) OR (state='RUNNING' AND lease_until<clock_timestamp()))
              ORDER BY available_at,created_at,id FOR UPDATE SKIP LOCKED LIMIT 1
            )
            UPDATE origination.durable_job j SET state='RUNNING',attempts=attempts+1,fence=fence+1,
              lease_until=clock_timestamp()+(? * interval '1 millisecond'),updated_at=clock_timestamp()
            FROM candidate c WHERE j.id=c.id
            RETURNING j.id,j.kind,j.target_id,j.revision,j.attempts,j.fence
            """,(rs,n) -> new Claim(rs.getObject(1,UUID.class),rs.getString(2),rs.getObject(3,UUID.class),rs.getInt(4),rs.getInt(5),rs.getLong(6)),kind,config.getLease().toMillis()).stream().findFirst());
    }
    public boolean finish(Claim claim,Runnable result) {
        return transaction(() -> {
            if(!lock(claim)) return false;
            result.run();
            jdbc.update("UPDATE origination.durable_job SET state='DONE',lease_until=NULL,last_error=NULL,updated_at=now() WHERE id=?",claim.id());
            return true;
        });
    }
    public boolean lock(Claim c) {
        return !jdbc.query("SELECT id FROM origination.durable_job WHERE id=? AND fence=? AND state='RUNNING' AND lease_until>clock_timestamp() FOR UPDATE",
                (rs,n)->rs.getObject(1,UUID.class),c.id(),c.fence()).isEmpty();
    }
    public void defer(Claim c) {
        transaction(() -> {
            if(lock(c)) jdbc.update("UPDATE origination.durable_job SET state='READY',attempts=greatest(attempts-1,0),available_at=now()+interval '2 seconds',lease_until=NULL,updated_at=now() WHERE id=?",c.id());
            return null;
        });
    }
    public boolean fail(Claim c,String code,boolean permanent,Runnable terminalAction) {
        return transaction(() -> {
            if(!lock(c)) return false;
            boolean exhausted=permanent || c.attempts()>=config.getMaxAttempts();
            long delay=Math.min(300000L,1000L*(1L<<Math.min(c.attempts(),8)))+ThreadLocalRandom.current().nextLong(1000);
            jdbc.update("UPDATE origination.durable_job SET state=?,last_error=?,lease_until=NULL,available_at=now()+(? * interval '1 millisecond'),updated_at=now() WHERE id=?",
                    exhausted?"FAILED":"READY",code,delay,c.id());
            if(exhausted) terminalAction.run();
            return exhausted;
        });
    }
    public void retry(String kind,UUID target,int revision) {
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(7128461)",Object.class);
        var states=jdbc.queryForList("SELECT state FROM origination.durable_job WHERE kind=? AND target_id=? AND revision=?",String.class,kind,target,revision);
        if(states.isEmpty()) { enqueue(kind,target,revision); return; }
        if(Set.of("FAILED","DONE").contains(states.getFirst())) checkCapacity();
        int updated=jdbc.update("""
            UPDATE origination.durable_job SET state='READY',attempts=0,available_at=now(),lease_until=NULL,fence=fence+1,last_error=NULL
            WHERE kind=? AND target_id=? AND revision=? AND (state IN ('FAILED','DONE') OR (state='RUNNING' AND lease_until<now()))
            """,kind,target,revision);
    }
    public List<View> views(UUID application) {
        return jdbc.query("""
            SELECT id,kind,state,attempts,last_error,available_at,correlation_id FROM origination.durable_job WHERE
            target_id=? OR target_id IN (SELECT id FROM origination.document WHERE application_id=?)
            OR target_id IN (SELECT id FROM origination.loan WHERE application_id=?) ORDER BY created_at DESC LIMIT 100
            """,(rs,n)->new View(rs.getObject(1,UUID.class),rs.getString(2),rs.getString(3),rs.getInt(4),rs.getString(5),rs.getTimestamp(6).toInstant(),rs.getString(7)),application,application,application);
    }
}

