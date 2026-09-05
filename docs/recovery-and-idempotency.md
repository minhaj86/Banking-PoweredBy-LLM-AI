# Placeholder Bank - Recovery and idempotency

Every cross-service workflow is at least once. A local database transaction cannot guarantee exactly-once delivery to an HTTP service.

## Local admission and revisions

Request claims are scoped by verified tenant, caller, operation, target and Idempotency-Key. A SHA-256 fingerprint covers the business payload and expected version where applicable. Claim, result identity and domain writes commit together. ON CONFLICT DO NOTHING and a separate SELECT see a concurrent winner safely. Different payloads under one key return 409. There is no catch-uniqueness/query-in-aborted-transaction flow.

Submission freezes form, product/policy configuration and document UUIDs in one transaction with its durable job. Approval serialises on the application row and version, then commits one decision, one unique loan, terms/schedule, audit and provisioning job. Losing approval/withdrawal requests roll back. Concurrent approval with a shared key replays the same decision.

## Workers

~~~mermaid
stateDiagram-v2
    [*] --> READY: local transaction commits job
    READY --> RUNNING: SKIP LOCKED claim; fence and attempt increment
    RUNNING --> DONE: matching live fence and local result commit
    RUNNING --> READY: retryable failure with backoff
    RUNNING --> RUNNING: expired lease reclaimed with newer fence
    RUNNING --> FAILED: permanent error or attempt budget
    FAILED --> READY: authorised idempotent retry
~~~

Claims use short transactions, attempt counts, available_at, lease_until and a monotonically increasing fence. No row lock or transaction spans storage, scanner, OCR/parser, AI, verification or ledger calls. Completion locks the live job/fence and then applies a short local result transaction. Old workers may perform a remote request but cannot overwrite local results after their lease expires.

Default leases are five minutes, provider calls thirty seconds and four attempts. Backoff is exponential, bounded at five minutes with jitter. Waiting for queued documents defers assessment without consuming the attempt budget. Permanent validation errors fail; retryable network failures retain the approved loan. A process crash leaves an expiring claim. No in-memory queue is required for correctness.

Manual processing retries require OPERATIONS and an idempotency key. Older assessment revisions cannot be retried into the current revision. Reconciliation enqueues/revives up to 100 approved loans missing mappings per tenant/call. A mapping conflict becomes an explicit permanent failure; review its restricted job code and verify the configured ledger/reference before repair. No automatic deletion/replacement of an approved loan occurs.

## Ledger uncertainty

The stable external reference and durable ledger-side uniqueness survive a successful remote commit followed by a lost response, process restart, duplicate worker, or failure to save the local mapping. Recovery first looks up the reference and verifies the account attributes; otherwise it calls the idempotent provisioning route. Fenced mapping insertion and lifecycle completion commit together.

If ledger-core is unavailable, the approved loan remains PENDING_LEDGER_ACCOUNT or PROVISIONING_FAILED. Restore ledger availability, then retry the same loan or run reconciliation. Reconciliation does not create a replacement loan, funded balance or journal.

## Upload uncertainty and retention

A PENDING metadata row commits before any object write. Retries reuse the generated object key and original business fingerprint. Completion checks storage metadata; processing additionally checks content checksum and AES-GCM authentication. Incomplete uploads cannot be assessed. Orphans are restricted to keys with recorded pending intent; cleanup never lists or deletes arbitrary user objects.

With retention enabled, worker maintenance queues pending uploads older than a day and expired terminal-application documents. The worker commits a PURGING intent while serialised with legal-hold changes, releases locks, deletes only that object's generated key, and commits the metadata/audit result under the job fence. A newly applied legal hold wins before the deletion intent; a hold request after PURGING starts returns a conflict. Failed deletions remain recoverable and block a late hold rather than falsely reporting protection.

Sensitive payload erasure for rejected/withdrawn applications uses a narrowly guarded schema-owner function. It checks age, terminal state, legal hold and completed document erasure, then removes encrypted payloads while retaining minimal references/audit metadata. Approved-loan data has separate retention obligations and is not purged by this function. Configure retention after a legal/data-protection review.

Objects written by the early storage-compatibility failure during development remain quarantined/failed synthetic evidence. New version uploads are the recovery path; no user data or demo volumes are removed.

