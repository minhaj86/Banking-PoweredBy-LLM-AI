package com.example.loans.workflow;

import com.example.loans.config.LoanProperties;
import com.example.loans.document.FinancialEvidence;
import com.example.loans.exception.LoanException;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;
import java.util.concurrent.*;
import java.util.function.Supplier;

@Component
public class ParserExecutor {
    private final ThreadPoolExecutor pool;
    private final LoanProperties config;
    public ParserExecutor(LoanProperties config) {
        this.config=config;
        pool=new ThreadPoolExecutor(config.getDocumentConcurrency(),config.getDocumentConcurrency(),0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(config.getDocumentConcurrency()),
            Thread.ofPlatform().daemon().name("bounded-document-parser-",0).factory(),new ThreadPoolExecutor.AbortPolicy());
    }
    public FinancialEvidence extract(Supplier<FinancialEvidence> work) {
        Future<FinancialEvidence> future=pool.submit(work::get);
        try { return future.get(config.getProviderTimeout().toMillis(),TimeUnit.MILLISECONDS); }
        catch(TimeoutException ex) { future.cancel(true); throw LoanException.invalid("DOCUMENT_TIMEOUT","Document requires isolated manual processing"); }
        catch(InterruptedException ex) { Thread.currentThread().interrupt(); throw LoanException.unavailable("PROCESSING_INTERRUPTED"); }
        catch(ExecutionException ex) { if(ex.getCause() instanceof RuntimeException r) throw r; throw new IllegalStateException("Parser failure"); }
    }
    @PreDestroy public void close() { pool.shutdownNow(); }
}

