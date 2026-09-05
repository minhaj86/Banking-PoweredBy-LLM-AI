# Placeholder Bank - Application and loan states

~~~mermaid
stateDiagram-v2
    [*] --> DRAFT
    DRAFT --> SUBMITTED: freeze revision and enqueue
    SUBMITTED --> ASSESSMENT_PENDING: worker snapshot
    ASSESSMENT_PENDING --> DOCUMENTS_REQUIRED: missing / unusable evidence
    ASSESSMENT_PENDING --> MANUAL_REVIEW: completed advisory and policy results
    ASSESSMENT_PENDING --> ASSESSMENT_FAILED: exhausted technical failure
    DOCUMENTS_REQUIRED --> DRAFT: edit / upload replacement
    MANUAL_REVIEW --> DRAFT: edit / additional evidence
    ASSESSMENT_FAILED --> DRAFT: correct / resubmit
    ASSESSMENT_FAILED --> SUBMITTED: controlled current-job retry
    MANUAL_REVIEW --> DOCUMENTS_REQUIRED: officer information request
    MANUAL_REVIEW --> APPROVED: explicit eligible officer decision
    MANUAL_REVIEW --> REJECTED: explicit officer decision
    DRAFT --> WITHDRAWN
    SUBMITTED --> WITHDRAWN
    ASSESSMENT_PENDING --> WITHDRAWN
    DOCUMENTS_REQUIRED --> WITHDRAWN
    MANUAL_REVIEW --> WITHDRAWN
    ASSESSMENT_FAILED --> WITHDRAWN
~~~

Editable states DRAFT, DOCUMENTS_REQUIRED, MANUAL_REVIEW and ASSESSMENT_FAILED may submit a new immutable revision, even without changing fields. An edit or upload first returns an editable application to DRAFT and invalidates approval based on the previous assessment. A reviewer transcription produces a superseding document UUID with REVIEWER_CONFIRMED provenance, then requires a fresh submission.

Final decisions require MANUAL_REVIEW. An information request in DOCUMENTS_REQUIRED may append a more specific request without changing state. Withdrawal is permitted from every pre-approval nonterminal state and uses the same application row lock/version as approval. APPROVED, REJECTED and WITHDRAWN cannot be changed through general updates. Post-approval cancellation is intentionally unimplemented. A reconsideration request is append-only and creates a human work item; it does not silently reopen a final application.

The version returned by GET is required for edits, uploads, submission, withdrawal, decisions, compliance reviews and legal holds. Keys make submission and decisions replayable despite a now-stale version when the original payload and key are retried.

Document upload progresses PENDING → COMPLETE → QUEUED → EXTRACTED or MANUAL_REVIEW. Security scan status is separate: NOT_SCANNED, CLEAN, INFECTED or UNAVAILABLE. Quarantine remains effective unless CLEAN. Malformed or resource-limited files fail or require manual review. A missing scanner is never a clean scan.

Each assessment is bound to an application revision. Fenced commits also compare the current revision and state, so an old result cannot revive a withdrawn application or overwrite a newer assessment. Technical failure becomes ASSESSMENT_FAILED, never REJECTED.

~~~mermaid
stateDiagram-v2
    [*] --> PENDING_LEDGER_ACCOUNT: approval commits
    PENDING_LEDGER_ACCOUNT --> AWAITING_AGREEMENT: account mapping saved
    PENDING_LEDGER_ACCOUNT --> PROVISIONING_FAILED: permanent / exhausted failure
    PROVISIONING_FAILED --> PENDING_LEDGER_ACCOUNT: controlled retry / reconciliation
    PROVISIONING_FAILED --> AWAITING_AGREEMENT: successful recovered attempt
~~~

Agreement status remains NOT_ACCEPTED throughout this iteration. ACTIVE is absent from the database lifecycle constraint. There is no disbursement endpoint or local outstanding-balance column.

