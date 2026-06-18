package com.example.ledger.service.impl;

import com.example.ledger.exception.LedgerException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.*;
import org.springframework.transaction.support.SimpleTransactionStatus;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TransactionExecutorTest {
    private final PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final RetryBackoff backoff = mock(RetryBackoff.class);
    private final TransactionExecutor executor = new TransactionExecutor(manager, jdbc, backoff);

    TransactionExecutorTest() {
        when(manager.getTransaction(any())).thenAnswer(invocation -> new SimpleTransactionStatus());
    }

    @Test
    void retriesWholeOperationAfterRollbackInFreshReadCommittedTransaction() {
        var calls = new AtomicInteger();
        var result = executor.execute(() -> {
            if (calls.incrementAndGet() == 1) { throw deadlock(); }
            return "committed";
        });
        assertThat(result).isEqualTo("committed");
        verify(manager, times(2)).getTransaction(argThat(d -> d.getIsolationLevel() == TransactionDefinition.ISOLATION_READ_COMMITTED
                && d.getPropagationBehavior() == TransactionDefinition.PROPAGATION_REQUIRES_NEW));
        var order = inOrder(manager, backoff);
        order.verify(manager).getTransaction(any());
        order.verify(manager).rollback(any());
        order.verify(backoff).pause(1);
        order.verify(manager).getTransaction(any());
        order.verify(manager).commit(any());
    }

    @Test
    void alsoRetriesCommitTimeFailuresAndBoundsAttempts() {
        doThrow(deadlock()).doNothing().when(manager).commit(any());
        assertThat(executor.execute(() -> "done")).isEqualTo("done");
        verify(manager, times(2)).getTransaction(any());
        verify(backoff).pause(1);
        clearInvocations(manager, backoff);
        assertThatThrownBy(() -> executor.execute(() -> { throw deadlock(); }))
                .isInstanceOfSatisfying(LedgerException.class, e -> assertThat(e.getCode()).isEqualTo("DATABASE_RETRY_EXHAUSTED"));
        verify(manager, times(3)).getTransaction(any());
        verify(backoff).pause(1);
        verify(backoff).pause(2);
    }

    @Test
    void doesNotRetryBusinessOrConstraintFailures() {
        assertThatThrownBy(() -> executor.execute(() -> { throw LedgerException.invalid("UNBALANCED", "unbalanced"); }))
                .isInstanceOf(LedgerException.class);
        assertThatThrownBy(() -> executor.execute(() -> {
            throw new DataIntegrityViolationException("bad", new SQLException("constraint", "23514"));
        })).isInstanceOf(DataIntegrityViolationException.class);
        verifyNoInteractions(backoff);
    }

    private CannotAcquireLockException deadlock() {
        return new CannotAcquireLockException("deadlock", new SQLException("deadlock", "40P01"));
    }
}
