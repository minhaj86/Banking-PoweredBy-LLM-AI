# Placeholder Bank - Accounting model

The ledger records the bank/business's books. Debits and credits are sides, not synonyms for negative and positive. Every transaction balances across at least two distinct accounts; a compound transaction can have many lines and can repeat an account.

| Account type | Normal side | Balance |
| --- | --- | --- |
| ASSET | DEBIT | debits minus credits |
| EXPENSE | DEBIT | debits minus credits |
| LIABILITY | CREDIT | credits minus debits |
| EQUITY | CREDIT | credits minus debits |
| REVENUE | CREDIT | credits minus debits |

A deposit increases the bank's cash asset with a debit, and increases its obligation to a customer with a credit. Moving customer funds debits the source liability and credits the destination liability. A fee debits the customer's liability and credits the bank's revenue.

A new account returns 0.00 debit total, credit total, and balance. An account may have a negative normal-direction balance unless enforceNonNegativeBalance is explicitly true. Checking a posting uses the sum of **all** its lines for each account, so a temporary negative effect from an individual line does not incorrectly reject an otherwise valid final balance.

## Money and currency

Each ledger has one immutable currency, restricted to SEK/EUR/USD. Every account and entry inherits it. There are no cross-ledger postings or FX amounts. Money uses BigDecimal and unconstrained PostgreSQL NUMERIC with explicit positive, scale, and maximum-value checks. NUMERIC(p,2) is deliberately avoided: PostgreSQL would round before a CHECK constraint could inspect the original precision.

Line maximum: 999999999999.99. Transaction maximum: 9999999999999.99 **on each side**, not both sides combined. Each transaction has 2..100 consecutive numbered lines. Plain decimal input must fit 64 characters. Unnecessary trailing fractional zeroes are accepted, but rounding is never performed. Aggregates use exact arbitrary precision without a mutable or fixed-precision stored balance.

## Journal lifecycle and immutability

Application posting reserves the result identifier, inserts an internal DRAFT header, inserts all lines, and sets POSTED inside a single database transaction. A deferred constraint trigger re-reads the final state at COMMIT and rejects any remaining draft, missing lines, nonconsecutive numbering, unequal totals, missing side, invalid account count, or excessive totals.

An entry INSERT takes a row lock on its header and verifies DRAFT. Header finalisation cannot race an append. Once POSTED, header changes, entry changes, deletions, and later entry inserts are rejected by triggers. The runtime role also lacks the privileges to delete, truncate, or disable safeguards. Schema owners and superusers remain trusted administrative actors, not an adversarial security boundary.

A journal is only publicly visible once the complete transaction commits. No endpoint changes a balance or journal row. Opening balances must be balanced postings against an appropriate equity/clearing counter-account.

## Reversals and closing accounts

A full reversal is a new transaction with each original line's account, amount, and line ordering retained and its side swapped. It carries the original identifier and required reason. Original rows never change. A database uniqueness constraint permits only one reversal of an original; deferred validation verifies the exact opposite lines and forbids reversing a reversal.

Reversals follow the same account locks, status checks, and non-negative rules as regular postings. They can fail if an account closed, or if subsequent activity means the reversing debit/credit would overdraw a protected account. Return funds or use an appropriate new corrective posting under the same rules; do not bypass them.

An account closes only at zero normal balance while its row is locked. A closed account retains all history and cannot receive new postings. Closure is irreversible through this API; repeating closure returns the same CLOSED account. Account deletion and reopening are not implemented.

## Trial balance

One aggregate SQL statement returns every account, including empty and closed accounts, with debit/credit totals. Closing debit is max(debits - credits, 0); closing credit is max(credits - debits, 0), irrespective of the account's normal side. The service sums the already fetched rows for ledger totals. Consequently all numbers come from the same statement snapshot, and ledger debit/credit totals and closing debit/credit totals match.
