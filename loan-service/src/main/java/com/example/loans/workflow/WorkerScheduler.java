package com.example.loans.workflow;

import com.example.loans.config.LoanProperties;
import jakarta.annotation.PreDestroy;
import org.springframework.context.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.*;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.concurrent.*;

@Component @EnableScheduling
@ConditionalOnProperty(name="loan.workers-enabled",havingValue="true",matchIfMissing=true)
public class WorkerScheduler {
    private record Lane(ExecutorService pool,Semaphore permits) {}
    private final Map<String,Lane> lanes=new LinkedHashMap<>();
    private final OriginationWorker worker;
    public WorkerScheduler(LoanProperties config,OriginationWorker worker) {
        this.worker=worker;
        add("DOCUMENT",config.getDocumentConcurrency()); add("ASSESS",config.getAiConcurrency()); add("PROVISION",config.getProvisioningConcurrency()); add("PURGE",1);
    }
    private void add(String kind,int concurrency) {
        lanes.put(kind,new Lane(Executors.newFixedThreadPool(concurrency,Thread.ofPlatform().daemon().name("loan-"+kind+"-",0).factory()),new Semaphore(concurrency)));
    }
    @Scheduled(fixedDelayString="${loan.poll-ms:500}") public void tick() {
        lanes.forEach((kind,lane)->{
            if(lane.permits().tryAcquire()) lane.pool().execute(()->{
                try { worker.runOne(kind); }
                catch(RuntimeException ex) { /* Safe metadata only; failed claims remain recoverable via lease. */ }
                finally { lane.permits().release(); }
            });
        });
    }
    @PreDestroy public void close() { lanes.values().forEach(l->l.pool().shutdownNow()); }
}

