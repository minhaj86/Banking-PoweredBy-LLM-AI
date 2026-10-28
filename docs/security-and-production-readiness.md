# Placeholder Bank - Security and production prerequisites

This repository is a tested local origination foundation. It is not legally certified, a validated underwriting model or ready for real lending.

## Authentication and service separation

loan-service always requires a verified JWT for business APIs. Only the explicit local profile accepts locally generated HMAC tokens and synthetic verification/AI. Default production startup rejects those shortcuts, missing OIDC issuer/audience configuration, missing encryption keys and unapproved external model configuration. Real KYC/bureau implementations must replace the unavailable provider beans before production approvals can pass.

JWT sub and tenant determine ownership; authorities come from a trusted roles claim. Applicants can access only their resources. Staff access remains within the tenant. Officer, compliance and operations permissions are checked at service boundaries and HTTP endpoints. Error lookups do not reveal another tenant's existence.

ledger-core retains its historical unauthenticated local API for backward compatibility. Activate its secured profile with LEDGER_OIDC_ISSUER and LEDGER_OIDC_AUDIENCE in protected deployments; it then requires SERVICE for business routes and validates issuer/audience. Configure loan-service's LEDGER_BEARER_TOKEN through a deployment credential provider/rotation mechanism. The static configurable token is adequate for local/service integration testing, not a complete client-credentials token lifecycle. Do not expose the legacy ledger profile on a production network.

Runtime database accounts are separate restricted users. loan-service cannot read the ledger database. Flyway uses schema-owner credentials. Production must run migrations separately and remove migration credentials from the running applications. Use separate databases, network policies, backups and restore validation.

## Data handling

Application/revision identity, financial/KYC payloads, assessments, free-text decisions and review notes are AES-256-GCM encrypted at rest in PostgreSQL. Object contents are separately authenticated/encrypted before S3 upload. Keys are required external configuration, never hardcoded production secrets. The current key envelope is v1 and does not implement automatic key rotation; plan key IDs, controlled re-encryption, backup key custody and recovery before production.

Buckets have no anonymous policy, public endpoint or permanent links. Downloads require ownership/roles, a complete upload and CLEAN scan, use attachment/nosniff/no-store headers, and write access audit metadata. Object keys contain only generated identifiers. ClamAV status is independent of extraction; UNAVAILABLE cannot become CLEAN. An audit records access intent before the storage read, so it may also record a failed download.

Actual signatures distinguish PDF/JPEG/PNG. Uploads are limited to 10 MiB, PDFs to twenty pages and supported extracted text to 20,000 characters per page. Image headers bound pixel counts. External calls and parser waiting have timeouts. A bounded parser pool limits resource impact; Java interruption cannot forcibly terminate every malicious parser. Production should isolate PDF/OCR processing in a sandboxed process/container with CPU/memory/time budgets and tighten ClamAV archive limits. No embedded content is executed and no document URL is fetched.

AWS request chunk framing is disabled for compatibility with the local S3 implementation. Independent SHA-256 verification and AES-GCM authentication still protect content. TLS is required for externally routed database, object store, OIDC, verification, AI and ledger traffic; the Compose network's plain HTTP is local demonstration infrastructure.

Ordinary logs contain only bounded event, job kind, error code and exception class. No raw provider messages, personal IDs, documents, prompts, financial payloads or applicant-labelled metrics are logged by application code. Provider tracing/body logging should remain disabled. Audit stores actor/action/resource/revision metadata; explanations stay encrypted in separate domain records.

## Human review and privacy

Financial evidence includes source/provenance and must be explicitly confirmed by the officer. The initial policy does not allow an affordability override. Required evidence, current revisions, policy/product eligibility, verification validity, matching assessed terms and material acknowledgements are checked atomically before approval.

PEP/compliance remains separate. Compliance reviewers can resolve a provider's REVIEW_REQUIRED result with an evidence reference, restricted reason, validity period and append-only record. They cannot use that route to bypass unavailable identity/bureau verification. Applicant decision explanations are separate from restricted notes. A review-request endpoint records human reconsideration or correction requests without silently reversing final decisions.

Privacy notice version and acknowledgement timestamp are recorded. This is not an assertion that generic consent is the legal basis for every processing operation. Names, ethnicity, religion, health, gender, nationality and unrelated KYC attributes are excluded from underwriting model inputs.

Retention is configurable and disabled until explicitly enabled. Legal holds and purge intent serialise. Documents can expire independently of append-only audit history. Rejected/withdrawn sensitive application and reviewer payloads can be erased after the configured period when documents are gone and no hold applies. Minimal decision/audit/loan references are retained. Production needs an approved retention schedule, data-subject workflows, backup expiration policy, and protection/retention treatment for actor/customer references and idempotency metadata. Approved-loan identity/terms retention requires the eventual agreement/servicing/legal lifecycle.

## Deployment work still required

Before real use, arrange lender authorisation where applicable; legal/regulatory review; a data-protection assessment; relevant AI governance and representative bias/model evaluation; notices and lawful processing bases; AI/provider contracts and approved processing locations; real identity, sanctions/compliance and bureau providers; tested reviewer/reconsideration procedures; secure secret rotation, TLS, network isolation and least-privilege S3 credentials; sandboxed extraction/OCR; monitoring/on-call, rate quotas, audit export, disaster recovery and retention/hold operations.

The current product intentionally excludes co-borrowers, collateral, refinancing, variable rates, payment execution, disbursement, servicing and collections. Provider integration has configurable adapters; no live external AI/KYC/bureau credentials were used in repository verification.

