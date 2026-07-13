# Placeholder Bank - Concurrency and idempotency

## Lock protocol

Every posting path, including full reversals, executes with READ COMMITTED isolation:

1. Validate bounded business input and compute a deterministic fingerprint before acquiring database locks.
2. Begin a fresh database transaction and reserve the idempotency scope with INSERT ... ON CONFLICT DO NOTHING.
3. SELECT every distinct affected account in PostgreSQL UUID order FOR UPDATE. The same SQL ordering is used everywhere; Java's signed UUID comparison is not substituted.
4. Issue a **separate** aggregate SELECT only after all locks return. A READ COMMITTED snapshot taken before a lock wait could be stale, so a combined lock-and-balance query is deliberately avoided.
5. Sum all line effects per account and validate the final normal balance for protected accounts.
6. Write the draft, all lines, and finalise. Commit the complete transaction and release locks.

Closure locks its single account, reads a new aggregate, requires zero, then closes it. Therefore posting cannot check ACTIVE and race a closure into writing a closed account. Two spends from the same protected customer liability serialize; the loser sees the winner's committed balance.

Concurrent reversals first acquire the same original account locks. Only then do they check for an existing reversal, so the loser returns ALREADY_REVERSED instead of a misleading insufficient-balance error. The database UNIQUE reversal constraint remains a second safeguard.

Deferred journal validation also locks affected accounts in order, then checks status and final protected balances with subsequent statements. This protects direct SQL in addition to the application protocol. The supported application flow acquires account locks before inserting any header/entry; arbitrary SQL clients should follow it as well to avoid avoidable deadlocks. Insert guards lock headers so finalisation and later entry insertion cannot cross.

## Transaction boundaries and retries

TransactionExecutor uses TransactionTemplate with REQUIRES_NEW and READ_COMMITTED. The callback includes the idempotency reservation, all journal writes, and response reconstruction. JpaTransactionManager makes the JPA EntityManager and JDBC repositories participate in the **same DataSource transaction**. A return value is not returned to the controller until commit succeeds.

A retry loop surrounds TransactionTemplate.execute, including COMMIT. There is no self-invoked @Transactional method that silently skips a proxy. Non-retried creation/read methods use ordinary proxied @Transactional service methods.

Only SQLSTATE 40001 (serialization failure), 40P01 (deadlock), and 55P03 (lock unavailability/lock timeout) are retried, at most **three attempts**. Each attempt has a new transaction; backoff is 25ms then 50ms plus 0..24ms jitter, outside any transaction. Per attempt: lock timeout 3s, SQL statement timeout 10s, Spring transaction timeout 15s. Contention exhaustion returns 503 DATABASE_RETRY_EXHAUSTED. A query timeout or uncertain connection failure is surfaced as 503 rather than blindly retried; the client should replay the same key to resolve uncertain commit outcome.

Validation and conflict failures are not retried. No credentials or request bodies appear in retry logs.

## Idempotency protocol

The primary key is (ledger_id, operation, idempotency_key). POST and REVERSE scopes are distinct, and the same key may be used independently in different ledgers.

The insert reserves the future journal UUID and fingerprint. PostgreSQL uniqueness arbitrates concurrent requests. ON CONFLICT DO NOTHING waits for an in-flight competitor; a new SELECT statement after a conflict observes its committed reservation. No unique exception is caught followed by reads inside an aborted transaction.

A winner that rolls back leaves no claim. A losing request can then reserve the key and post. A successful reservation has a deferred FK to a real journal in the same ledger, so an abandoned reservation cannot commit. There is no persistent IN_PROGRESS state or expiry-based lock to get stuck.

The request fingerprint is SHA-256 of a versioned canonical representation of validated business fields:

- POST: external reference, description, ordered account IDs/sides/normalised amounts/entry descriptions.
- REVERSE: target transaction UUID and required reason.
- Money is always normalised to exactly two decimals; comparisons are numeric.
- Text, including whitespace and empty strings, is significant. Missing optional fields and explicit null are equivalent.
- Entry order is significant because it defines stable line numbers; swapping lines is a different request.
- Correlation headers, tracing, generated IDs, timestamps, and other server fields are excluded.

Currently the canonical representation is the fixed record JSON structure with a version/operation prefix. Treat it as a persisted protocol: future versions must preserve the ability to compare old records or retain a compatible versioned canonicalizer.

The record stores response status 201 and transaction ID; the response is reliably reconstructed from immutable header/entries and immutable ledger currency. The recordedAt and entry IDs do not change. First success and successful replays both return HTTP 201 and the same body/Location. Correlation response headers may change per attempt.

A same-key request with a different valid fingerprint returns 409 IDEMPOTENCY_CONFLICT. Structural/money validation precedes deduplication, so malformed retries return 400/422 rather than bypassing validation. Replay checks precede current account/reversal rules: a previously successful request remains replayable even if accounts later close.

## Reporting and verification

Trial balance is one aggregate statement; ledger totals are sums of those fetched rows. This avoids mixed snapshots across independently queried account balances. History is offset-paginated by recordedAt, transaction UUID, line number. Neither recordedAt (assigned before commit) nor UUID is an exact commit sequence. Concurrent inserts can change the contents of later pages.

Integration tests coordinate operations with latches/barriers inside separate database transactions, assert distinct backend connection IDs, and exercise simultaneous spends, duplicate keys, conflicting keys, reversals, closure, and reports. Deferred-constraint tests explicitly call COMMIT; they do not rely on test-level rollback. An injected PostgreSQL trigger fails the second entry to verify atomic rollback and subsequent reuse of the same key.
