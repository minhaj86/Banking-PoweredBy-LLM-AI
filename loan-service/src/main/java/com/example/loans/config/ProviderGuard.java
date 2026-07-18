package com.example.loans.config;

import io.github.resilience4j.circuitbreaker.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.time.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

@Component @RequiredArgsConstructor
public class ProviderGuard {
    private final LoanProperties config;
    private final CircuitBreaker breaker=CircuitBreaker.of("assessment-provider",CircuitBreakerConfig.custom().slidingWindowSize(10).minimumNumberOfCalls(4)
        .failureRateThreshold(50).waitDurationInOpenState(Duration.ofSeconds(30)).build());
    private long minute=-1; private int used;
    public <T> T ai(Supplier<T> work) {
        synchronized(this) {
            long now=System.currentTimeMillis()/60000;
            if(now!=minute) { minute=now; used=0; }
            if(used++>=config.getAi().getRequestsPerMinute()) throw com.example.loans.exception.LoanException.unavailable("AI_RATE_LIMIT");
        }
        return breaker.executeSupplier(work);
    }
}

