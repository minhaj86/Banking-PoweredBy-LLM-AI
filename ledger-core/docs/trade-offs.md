# Placeholder Bank - Trade-offs, assumptions, and extensions

## Intentional scope

One service, one PostgreSQL database, posted transactions only. The service uses the business/bank perspective. It implements no holds, pending state in the public API, available credit, payment processing, interest, FX, external banking calls, Kafka, Redis, distributed transactions, blockchain, or frontend.

A ledger has one immutable SEK/EUR/USD currency. Account name, code, type, ledger, and non-negative policy are fixed after creation; only zero-balance closure is supported. Code matching is case-sensitive. External reference is descriptive, not unique; use Idempotency-Key for retries. Reversals are full and singular. Accounts and journals are not deleted. No backdating or caller-supplied recordedAt is allowed.

## Balance aggregation

The journal is authoritative. Reads aggregate exact debit/credit entries through an account index; postings read all affected accounts after locks. This makes correctness and reconciliation straightforward but cost grows with history, including the length of the lock-holding transaction. Hot accounts serialize and ultimately bound throughput. The single-query trial balance can be expensive for a large ledger, and its response currently contains all accounts without pagination.

A future rebuildable account balance projection could store debit and credit totals updated in the **same transaction** as the journal, under the same account locks. It must never replace the immutable journal as the source of truth. Add reconciliation, rebuild tooling, migration/catch-up validation, and safeguards before using it for protected-balance decisions. Asynchronous projections alone cannot enforce the current no-overdraft guarantee.

Entry INSERT and deferred constraints perform extra work for stronger database safeguards. Idempotency reservations have no TTL because long-lived replay is useful for financial writes; retention or archival needs a deliberate contract. The database can enforce balanced SQL postings without an API idempotency key, but every application posting creates a claim atomically.

## Limits and reporting

2..100 lines and explicit line/transaction money caps bound request and SQL work. Lifetime totals remain arbitrary precision. The API accepts plain decimal strings only; formats such as exponent notation, commas, signs, whitespace, NaN, and Infinity are rejected.

History has bounded page size and offset. Deep pages still cost more, and pages are not a single snapshot across requests. A future opaque cursor and explicit snapshot/export feature could improve this. UUIDs and recordedAt are not a committed sequence. A future accounting period/effective date feature requires its own model; do not reinterpret recordedAt.

Reversal and closure can be rejected due to intervening account activity. Corrections must comply with the same active-account and final balance rules; there is no privileged bypass.

## Local operation versus production

This implementation is a development foundation. Before production, add and validate:

- Authentication and ledger/account-scoped authorisation, including operator and tenant isolation.
- Security review, TLS, secrets management and rotation, network controls, dependency/image vulnerability scanning, rate/body limits, and denial-of-service resilience.
- A separate migration job; no schema owner credentials in the long-running application process.
- Operational auditing with access and administrative change records, retention policies, and tamper detection. Diagnostic JSON logs do not satisfy this.
- Backup/restore drills, point-in-time recovery, reconciliation after recovery, failover/uncertain-commit testing, monitoring, alerting, and capacity/load testing.
- Migration rollout/rollback plans, incident procedures, and applicable business/regulatory controls.

Docker role/bootstrap passwords are development-only. Local startup gives Flyway distinct credentials but in the same process. A privileged schema owner can replace triggers; the current safeguards target runtime bugs and unprivileged SQL, not a hostile administrator. Docker base image digests and Maven versions should be deliberately refreshed and reverified.

Testcontainers exercises real PostgreSQL constraints, isolation, separate concurrent transactions, and the restricted runtime role. It does not certify production security, disaster recovery, sustained throughput, or multi-region behavior.
