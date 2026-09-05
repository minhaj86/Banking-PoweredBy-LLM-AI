# Placeholder Bank - Loan accounting boundary

The lender owns a receivable when a valid funding workflow creates a financial asset. An origination approval records proposed contractual terms; it does not yet create that funded asset's outstanding balance.

The provisioning worker creates one principal-receivable account with type ASSET, normal side DEBIT, non-negative balance protection, the configured SEK ledger and reference loan:<loan UUID>:principal. Its account code is LN-<loan UUID>. The ledger derives currency from its immutable ledger.

The additive ledger API is POST /api/v1/ledgers/{ledgerId}/accounts/provision with Idempotency-Key and fields externalReference, code, name, type, enforceNonNegativeBalance. GET /api/v1/ledgers/{ledgerId}/accounts/by-external-reference?externalReference=... recovers successful account creation after a lost response. The existing account-create route and journal API remain compatible.

V3 adds a nullable immutable account external_reference with UNIQUE(ledger_id,external_reference) and a durable account-provisioning request table. The request claim and account INSERT commit atomically. ON CONFLICT followed by a separate read handles concurrent commits without querying an aborted transaction. Same-key changed account terms conflict. Same external reference with different terms conflicts. Existing accounts without an external reference continue to work.

~~~mermaid
flowchart LR
    Approval --> Terms[Immutable principal and schedule]
    Approval --> Job[Provision account job]
    Job --> Account[ASSET / DEBIT account]
    Account --> Zero[Zero balance; no journal]
    Zero --> Agreement[AWAITING_AGREEMENT]
~~~

The mapping is unique by loan/purpose, remote ledger/account and external reference. Reuse validates ledger, inherited currency, loan reference, account code, ASSET/DEBIT, active status and balance protection. loan-service has no access to the ledger database and stores no authoritative financial balance.

Illustrative future funding accounting, outside this implementation:

| Debit | Credit |
| --- | --- |
| Loan principal receivable | Actual cash, settlement or customer-deposit counter-account, depending on payout design |

A funded workflow would also need agreement acceptance, payout authorisation, idempotent balanced posting and failure recovery. Approval/account creation alone never moves the lifecycle to ACTIVE. Indicative schedule principal is approved principal, not outstanding balance.

