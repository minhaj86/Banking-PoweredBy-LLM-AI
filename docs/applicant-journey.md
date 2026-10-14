# Placeholder Bank - Applicant journey

The public page reads the current product from the backend and makes no approval promise. Sign-in and account creation lead to Keycloak. The dashboard lists only the current tenant/identity's applications, latest state, outstanding-information state and linked approved loans.

The wizard has eight steps: loan amount/term/purpose; personal/contact details; employment/income; non-debt living expenses; existing credit; KYC declarations; documents; review/acknowledgement/submission. Configured product limits and evidence requirements come from loan-service. Decimal inputs remain strings. JavaScript performs no authoritative repayment or affordability calculations.

**Save draft** and **Save & continue** persist encrypted partial form data in the backend. Saved, unsaved, saving and failed states are explicit. There is no browser persistent draft store or offline cache. A response to an earlier save never resets newer form values. Every save carries an expected version and a stable idempotency key for that payload. A conflict asks the applicant to reload/review; it does not overwrite the other writer. Full backend validation occurs on submission. Earlier wizard steps can be revisited; validation summaries and step headings are focus targets.

Uploads require a category and covered dates, and offer a file picker/drop zone, bounded size/formats, progress, cancellation and retry. A retry of the same uncertain upload retains its original key/version. A changed file or metadata is a new intent. Evidence stays private and quarantined until a real ClamAV clean scan. Extraction, scan and upload are separate statuses. Extraction does not independently verify income or authenticity. Replacement references the superseded document and retains the original history.

Submission requires verified email, a complete form and the processing acknowledgement. The confirmation states the amount, term and product version. HTTP 202 means processing accepted. The backend freezes the form/document/product/policy revision and enqueues assessment atomically. No UI approval is inferred from 202 or an advisory result.

The detail page shows processing, a safe timeline, information requests, the applicant-facing explanation and an authorized human reconsideration/correction request. Withdrawal is available only before terminal states and is rechecked by the backend. Additional evidence returns through the editable draft and creates a new frozen revision on submission.

An approved loan displays immutable terms, **nominal** annual interest (not APR), an indicative backend schedule, agreement status, account provisioning and the actual ledger-derived balance. An unfunded approved loan is `PENDING_LEDGER_ACCOUNT` or `AWAITING_AGREEMENT`, with agreement `NOT_ACCEPTED`. It is never shown as ACTIVE or disbursed. There are no invented signing, payout or repayment buttons.

All timestamps display UTC; date-only fields retain their date semantics. Common amount/date/status formatting is centralized. English copy is the initial language. A full translated copy catalog is a future extension.
