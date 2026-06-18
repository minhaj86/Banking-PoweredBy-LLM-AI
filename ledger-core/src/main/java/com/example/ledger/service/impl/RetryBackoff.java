package com.example.ledger.service.impl;

import com.example.ledger.exception.LedgerException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class RetryBackoff {
    public void pause(int attempt) {
        try {
            Thread.sleep(25L * (1L << (attempt - 1)) + ThreadLocalRandom.current().nextLong(25));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new LedgerException("DATABASE_UNAVAILABLE", HttpStatus.SERVICE_UNAVAILABLE, "Operation interrupted; retry with the same idempotency key");
        }
    }
}
