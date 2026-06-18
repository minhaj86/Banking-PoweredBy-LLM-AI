package com.example.ledger.service.impl;

import com.example.ledger.exception.LedgerException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.sql.SQLException;
import java.util.Set;
import java.util.function.Supplier;

@Component
@RequiredArgsConstructor
@Slf4j
public class TransactionExecutor {
    private static final int MAX_ATTEMPTS = 3;
    private static final Set<String> RETRYABLE = Set.of("40001", "40P01", "55P03");
    private final PlatformTransactionManager transactionManager;
    private final JdbcTemplate jdbc;
    private final RetryBackoff backoff;

    public <T> T execute(Supplier<T> operation) {
        var template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        template.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        template.setTimeout(15);
        for (int attempt = 1; ; attempt++) {
            try {
                // commit-time failures are caught OUTSIDE execute; every retry gets a fresh transaction.
                return template.execute(status -> {
                    jdbc.execute("SET LOCAL lock_timeout = '3s'");
                    jdbc.execute("SET LOCAL statement_timeout = '10s'");
                    return operation.get();
                });
            } catch (RuntimeException ex) {
                if (!isRetryable(ex)) {
                    throw ex;
                }
                if (attempt == MAX_ATTEMPTS) {
                    throw new LedgerException("DATABASE_RETRY_EXHAUSTED", HttpStatus.SERVICE_UNAVAILABLE,
                            "Database contention persisted; retry with the same idempotency key");
                }
                log.warn("event=database_retry attempt={} cause={}", attempt, ex.getClass().getSimpleName());
                backoff.pause(attempt); // Transaction and all locks have already been released.
            }
        }
    }

    static boolean isRetryable(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && RETRYABLE.contains(sql.getSQLState())) {
                return true;
            }
        }
        return false;
    }
}
