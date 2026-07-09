package com.example.ledger.integration;

import com.example.ledger.domain.enums.*;
import com.example.ledger.domain.repository.*;
import com.example.ledger.dto.request.ReversalRequest;
import com.example.ledger.exception.LedgerException;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ConcurrencyIT extends PostgresIntegrationTest {
    @MockitoSpyBean AccountLockRepository accountLocks;
    @MockitoSpyBean IdempotencyRepository idempotency;
    private final Set<Integer> backends = ConcurrentHashMap.newKeySet();

    private record Outcome(Object result, Throwable failure) {}

    private List<Outcome> race(Supplier<?> left, Supplier<?> right) throws Exception {
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var futures = new ArrayList<Future<Outcome>>();
            for (var operation : List.of(left, right)) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) { throw new AssertionError("Start latch timed out"); }
                    try { return new Outcome(operation.get(), null); }
                    catch (Throwable ex) { return new Outcome(null, ex); }
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return List.of(futures.get(0).get(30, TimeUnit.SECONDS), futures.get(1).get(30, TimeUnit.SECONDS));
        }
    }

    private void synchronizeAccountLocks(UUID ledgerId) {
        var barrier = new CyclicBarrier(2);
        doAnswer(invocation -> {
            backends.add(jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
            barrier.await(10, TimeUnit.SECONDS);
            return invocation.callRealMethod();
        }).when(accountLocks).lockAll(eq(ledgerId), anyCollection());
    }

    private void synchronizeClaims(UUID ledgerId) {
        var barrier = new CyclicBarrier(2);
        doAnswer(invocation -> {
            backends.add(jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
            barrier.await(10, TimeUnit.SECONDS);
            return invocation.callRealMethod();
        }).when(idempotency).claim(eq(ledgerId), any(), anyString(), anyString(), any());
    }

    @Test
    void exactlyOneOfTwoConcurrentEightyUnitSpendsSucceedsFromOneHundred() throws Exception {
        var ledger = ledger();
        var cash = account(ledger.id(), AccountType.ASSET, false);
        var source = account(ledger.id(), AccountType.LIABILITY, true);
        var a = account(ledger.id(), AccountType.LIABILITY, true);
        var b = account(ledger.id(), AccountType.LIABILITY, true);
        post(ledger.id(), transfer(cash.id(), source.id(), "100"));
        synchronizeAccountLocks(ledger.id());
        var outcomes = race(
                () -> journals.post(ledger.id(), "spend-a", transfer(source.id(), a.id(), "80")),
                () -> journals.post(ledger.id(), "spend-b", transfer(source.id(), b.id(), "80")));
        assertThat(backends).hasSize(2);
        assertThat(outcomes.stream().filter(o -> o.failure() == null)).hasSize(1);
        var failure = outcomes.stream().map(Outcome::failure).filter(Objects::nonNull).findFirst().orElseThrow();
        assertThat(failure).isInstanceOfSatisfying(LedgerException.class, e -> assertThat(e.getCode()).isEqualTo("NEGATIVE_BALANCE"));
        balance(ledger.id(), source.id(), "20.00");
        assertThat(new BigDecimal(reports.balance(ledger.id(), a.id()).balance())
                .add(new BigDecimal(reports.balance(ledger.id(), b.id()).balance()))).isEqualByComparingTo("80");
        assertThat(transactions(ledger.id())).isEqualTo(2); // funding + the one successful transfer
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ledger.journal_entry WHERE ledger_id=?", Long.class, ledger.id())).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ledger.idempotency_record WHERE ledger_id=?", Long.class, ledger.id())).isEqualTo(2);
    }

    @Test
    void concurrentIdenticalRequestsHaveOneTransactionAndTheSameResponse() throws Exception {
        var ledger = ledger();
        var cash = account(ledger.id(), AccountType.ASSET, false);
        var customer = account(ledger.id(), AccountType.LIABILITY, true);
        synchronizeClaims(ledger.id());
        var outcomes = race(
                () -> journals.post(ledger.id(), "one-key", transfer(cash.id(), customer.id(), "10.0")),
                () -> journals.post(ledger.id(), "one-key", transfer(cash.id(), customer.id(), "10.00")));
        assertThat(backends).hasSize(2);
        assertThat(outcomes).allSatisfy(o -> assertThat(o.failure()).isNull());
        assertThat(outcomes.get(0).result()).isEqualTo(outcomes.get(1).result());
        assertThat(transactions(ledger.id())).isEqualTo(1);
        balance(ledger.id(), customer.id(), "10.00");
    }

    @Test
    void concurrentDifferentPayloadsUnderOneKeyConflict() throws Exception {
        var ledger = ledger();
        var a = account(ledger.id(), AccountType.ASSET, false);
        var b = account(ledger.id(), AccountType.LIABILITY, false);
        synchronizeClaims(ledger.id());
        var outcomes = race(
                () -> journals.post(ledger.id(), "conflict", transfer(a.id(), b.id(), "10")),
                () -> journals.post(ledger.id(), "conflict", transfer(a.id(), b.id(), "11")));
        assertThat(outcomes.stream().filter(o -> o.failure() == null)).hasSize(1);
        assertThat(outcomes.stream().map(Outcome::failure).filter(Objects::nonNull).findFirst().orElseThrow())
                .isInstanceOfSatisfying(LedgerException.class, e -> assertThat(e.getCode()).isEqualTo("IDEMPOTENCY_CONFLICT"));
        assertThat(transactions(ledger.id())).isEqualTo(1);
    }

    @Test
    void concurrentReversalsWithDifferentKeysHaveExactlyOneWinner() throws Exception {
        var ledger = ledger();
        var a = account(ledger.id(), AccountType.ASSET, false);
        var b = account(ledger.id(), AccountType.LIABILITY, true);
        var original = post(ledger.id(), transfer(a.id(), b.id(), "100"));
        synchronizeAccountLocks(ledger.id());
        var outcomes = race(
                () -> journals.reverse(ledger.id(), original.id(), "reverse-a", new ReversalRequest("Correction")),
                () -> journals.reverse(ledger.id(), original.id(), "reverse-b", new ReversalRequest("Correction")));
        assertThat(backends).hasSize(2);
        assertThat(outcomes.stream().filter(o -> o.failure() == null)).hasSize(1);
        assertThat(outcomes.stream().map(Outcome::failure).filter(Objects::nonNull).findFirst().orElseThrow())
                .isInstanceOfSatisfying(LedgerException.class, e -> assertThat(e.getCode()).isEqualTo("ALREADY_REVERSED"));
        assertThat(transactions(ledger.id())).isEqualTo(2);
        balance(ledger.id(), b.id(), "0.00");
    }

    @Test
    void simultaneousReversalReplaysWithSameKeyReturnSameResult() throws Exception {
        var ledger = ledger();
        var a = account(ledger.id(), AccountType.ASSET, false);
        var b = account(ledger.id(), AccountType.LIABILITY, true);
        var original = post(ledger.id(), transfer(a.id(), b.id(), "100"));
        synchronizeClaims(ledger.id());
        var outcomes = race(
                () -> journals.reverse(ledger.id(), original.id(), "reverse", new ReversalRequest("Correction")),
                () -> journals.reverse(ledger.id(), original.id(), "reverse", new ReversalRequest("Correction")));
        assertThat(outcomes).allSatisfy(o -> assertThat(o.failure()).isNull());
        assertThat(outcomes.getFirst().result()).isEqualTo(outcomes.getLast().result());
        assertThat(transactions(ledger.id())).isEqualTo(2);
    }

    @Test
    void closureAndPostingSerializeWithoutAClosedAccountReceivingFunds() throws Exception {
        var ledger = ledger();
        var cash = account(ledger.id(), AccountType.ASSET, false);
        var customer = account(ledger.id(), AccountType.LIABILITY, true);
        synchronizeAccountLocks(ledger.id());
        var outcomes = race(
                () -> accounts.close(ledger.id(), customer.id()),
                () -> journals.post(ledger.id(), "deposit", transfer(cash.id(), customer.id(), "10")));
        assertThat(backends).hasSize(2);
        assertThat(outcomes.stream().filter(o -> o.failure() == null)).hasSize(1);
        var account = accounts.get(ledger.id(), customer.id());
        if (account.status() == AccountStatus.CLOSED) {
            balance(ledger.id(), customer.id(), "0.00");
            assertThat(transactions(ledger.id())).isZero();
        } else {
            balance(ledger.id(), customer.id(), "10.00");
            assertThat(transactions(ledger.id())).isEqualTo(1);
        }
        assertThat(outcomes.stream().map(Outcome::failure).filter(Objects::nonNull).findFirst().orElseThrow())
                .isInstanceOfSatisfying(LedgerException.class, e -> assertThat(e.getCode()).isIn("ACCOUNT_NOT_ZERO", "ACCOUNT_CLOSED"));
    }

    @Test
    void trialBalanceRemainsInternallyConsistentDuringConcurrentPosting() throws Exception {
        var ledger = ledger();
        var a = account(ledger.id(), AccountType.ASSET, false);
        var b = account(ledger.id(), AccountType.LIABILITY, true);
        var barrier = new CyclicBarrier(2);
        try (var pool = Executors.newSingleThreadExecutor()) {
            var writer = pool.submit(() -> {
                for (int i = 0; i < 25; i++) {
                    barrier.await(10, TimeUnit.SECONDS);
                    post(ledger.id(), transfer(a.id(), b.id(), "1"));
                    barrier.await(10, TimeUnit.SECONDS);
                }
                return true;
            });
            for (int i = 0; i < 25; i++) {
                barrier.await(10, TimeUnit.SECONDS);
                var report = reports.trialBalance(ledger.id());
                assertThat(report.totalDebits()).isEqualTo(report.totalCredits());
                assertThat(report.closingDebits()).isEqualTo(report.closingCredits());
                assertThat(report.accounts()).hasSize(2);
                assertThat(report.accounts().stream().map(row -> new BigDecimal(row.totalDebits())).reduce(BigDecimal.ZERO, BigDecimal::add))
                        .isEqualByComparingTo(report.totalDebits());
                barrier.await(10, TimeUnit.SECONDS);
            }
            assertThat(writer.get(10, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(reports.trialBalance(ledger.id()).totalDebits()).isEqualTo("25.00");
    }
}
