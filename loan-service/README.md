# Placeholder Bank - Personal loan origination

An independently deployable Java 21 / Spring Boot service for unsecured, fee-free SEK personal loans with one applicant, fixed nominal annual interest and monthly instalments. It uses its own PostgreSQL database and private S3-compatible object storage. Ledger integration is HTTP only.

Approval records terms and starts account provisioning. A provisioned loan awaits agreement. Approval does not accept an agreement, fund a loan, create an outstanding balance, or post a journal.

## Run the complete local demonstration

From the repository root, with Docker Desktop/Engine running:

~~~powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File loan-service/scripts/init-local.ps1
docker compose up -d --build --wait --wait-timeout 600
powershell.exe -NoProfile -ExecutionPolicy Bypass -File loan-service/scripts/demo.ps1 -Recovery -FailureCases
~~~

Unix requires Bash, curl, jq and OpenSSL:

~~~sh
sh loan-service/scripts/init-local.sh
docker compose up -d --build --wait --wait-timeout 600
bash loan-service/scripts/demo.sh --recovery --failure-cases
~~~

The scripts generate random local encryption, JWT and database keys in the ignored root .env. They preserve an existing file. Keep these keys with the persistent demo volumes; changing keys or passwords does not migrate existing data. No production credentials are included.

Local URLs: [loan API / Swagger](http://localhost:18081/swagger-ui/index.html), [loan health](http://localhost:18081/actuator/health), [ledger API](http://localhost:18080/swagger-ui/index.html). Database, object-store and scanner ports are internal to the Compose network. HTTP ports bind to loopback.

The root Compose project is finance-loan-origination and uses separate named volumes from the existing ledger-only project. The existing [ledger Compose configuration](../ledger-core/compose.yaml) continues to work independently. Stop the demo with docker compose stop or docker compose down; preserve volumes. No script removes volumes.

The demonstration creates synthetic applicants and payslip/tax PDFs, uploads and scans them with ClamAV, submits an immutable revision, polls asynchronous processing, prints arithmetic and advisory findings, executes an explicit synthetic officer review, repeats submission and approval, verifies the same ASSET/DEBIT account at zero balance with no entries, repeats provisioning, stops the demo ledger for a second approval and recovers it. Failure cases exercise missing evidence, low residual income and a local mock-AI outage. The demo's review acknowledgements are scripted synthetic inputs; actual deployment requires a meaningful human review process.

Local object storage is SeaweedFS 3.85, with authenticated S3 access and application-side AES-GCM encryption. ClamAV 1.4.3 downloads/uses its signature database and may take several minutes on its first start. If signatures cannot load, startup remains unhealthy; unscanned files never become CLEAN.

## Build and verification

Set JAVA_HOME to JDK 21 and start Docker. From the parent:

~~~powershell
.\mvnw.cmd verify
.\mvnw.cmd test
~~~

~~~sh
sh ./mvnw verify
sh ./mvnw test
~~~

verify runs unit tests, PostgreSQL Testcontainers tests, a real S3 storage round trip, and an HTTP end-to-end test using the packaged ledger-core JAR in a separate container with its own PostgreSQL instance. It never substitutes H2 or silently skips unavailable-container tests. Run root verify first: the real-ledger test requires the sibling ledger JAR. After that, -pl loan-service verify can run only this module.

The executable is target/loan-service-0.1.0-SNAPSHOT.jar. For host source operation, supply the environment below and select the module: .\mvnw.cmd -pl loan-service spring-boot:run (Unix: sh ./mvnw -pl loan-service spring-boot:run). Root Compose is the reproducible complete setup.

## Authentication and requests

Every business route requires a verified bearer JWT. Outside the explicit local profile, configure OIDC issuer discovery and the loan-service audience. Claims sub and tenant establish ownership; roles contains APPLICANT, LOAN_OFFICER, COMPLIANCE_REVIEWER, OPERATIONS, or SERVICE. No request field can select its tenant, owner or ledger.

For local tokens:

~~~powershell
$token = & ./loan-service/scripts/token.ps1 -Role APPLICANT
Invoke-RestMethod http://localhost:18081/api/v1/loan-applications -Headers @{Authorization="Bearer $token"}
~~~

~~~sh
token=$(bash loan-service/scripts/token.sh APPLICANT)
curl -fsS -H "Authorization: Bearer $token" http://localhost:18081/api/v1/loan-applications
~~~

[scripts/application.json](scripts/application.json) is a synthetic request example. Refresh its dates for a new demonstration. [demo.ps1](scripts/demo.ps1) and [demo.sh](scripts/demo.sh) contain runnable requests, multipart uploads, decisions and retries.

If-Match is the numeric version returned by GET, for example If-Match: 3. Reload before an edit/decision after a 409 VERSION_CONFLICT. Creation, upload, submission, withdrawal, decisions, reconsideration and manual retries require Idempotency-Key. Reuse a key and the same body/version after a lost response; a changed business payload conflicts.

Money fields require unsigned JSON decimal strings with at most two decimal places and at most twelve integer digits. Invalid scale is rejected before PostgreSQL can round it. Nominal rates are fractional decimal strings in responses (0.080000 means 8% nominal annually). They are not APR.

All paths below start /api/v1:

| Method | Path | Role / behavior |
| --- | --- | --- |
| POST / GET | /loan-applications | Applicant creates own draft; paginated summaries |
| GET / PUT | /loan-applications/{id} | Owner or staff reads; owner edits with version |
| POST | /loan-applications/{id}/submit | Owner; 202, durable assessment job |
| POST | /loan-applications/{id}/withdraw | Owner; serialized with approval |
| POST / GET | /loan-applications/{id}/documents | Owner uploads; metadata list |
| GET | /loan-applications/{id}/documents/{documentId} | Authorised metadata / officer financial evidence |
| GET | /loan-applications/{id}/documents/{documentId}/content | Authorised, successful scan required |
| POST | /loan-applications/{id}/documents/{documentId}/manual-extraction | Officer transcription; immutable replacement document |
| GET | /loan-applications/{id}/processing-status | Safe owner state; staff job details |
| GET | /loan-applications/{id}/assessment | Officer only |
| POST | /loan-applications/{id}/decisions | Officer; append-only explicit review |
| POST | /loan-applications/{id}/information-requests | Officer; outcome REQUEST_INFORMATION |
| POST | /loan-applications/{id}/review-requests | Human reconsideration / correction request |
| GET | /loan-applications/{id}/verifications | Officer or compliance reviewer |
| POST | /loan-applications/{id}/verification-reviews | Compliance reviewer resolves provider REVIEW_REQUIRED |
| GET | /loans/{id}, /terms, /repayment-schedule, /ledger-account | Owner or authorised staff |
| POST | /loans/{id}/provisioning/retry | Operations; 202, idempotent |
| POST | /operations/provisioning/reconcile | Operations; repairs up to 100 missing mappings per call |
| POST | /operations/applications/{id}/jobs/{jobId}/retry | Operations; failed current jobs only |
| POST | /operations/applications/{id}/legal-hold?enabled=true | Compliance reviewer; version required |
| GET | /operations/applications/{id}/audit | Operations / compliance; latest 200 metadata events |
| GET | /operations/metrics | Operations; tenant backlog and this JVM's resource snapshot |
| POST | /operations/demo/ai-unavailable?enabled=true | Explicit local profile, mock provider, operations only |

Applicants receive their submitted/draft information and the separate applicant decision explanation. Internal advisory findings, job failures and compliance notes are restricted. Collections and history have bounded reads; application lists support page=0&size=25 with size 1..100. Audit is currently a bounded recent view, not a complete export API.

Errors are application/problem+json with status, stable code and correlationId, also returned as X-Correlation-ID. Typical statuses: 400 malformed JSON or numeric money, 401 invalid token, 403 role, 404 ownership/tenant-safe lookup, 409 stale version/state/idempotency, 413 multipart size, 422 business validation, 503 queue/provider/database availability. 202 only acknowledges accepted work. Poll processing-status or provisioningStatus for completion.

## Configuration

| Variable / property | Meaning |
| --- | --- |
| SPRING_PROFILES_ACTIVE | local for synthetic demo; api disables workers; default requires production auth/providers |
| LOAN_DB_URL / USER / PASSWORD | Separate loan runtime PostgreSQL connection |
| LOAN_MIGRATION_USER / PASSWORD | Separate schema-owner Flyway credentials |
| LOAN_ENCRYPTION_KEY | Base64 32-byte AES key, external secret manager in production |
| LOAN_LOCAL_JWT_SECRET | Local-only base64 HMAC key |
| LOAN_OIDC_ISSUER / LOAN_AUDIENCE | Production verified issuer and audience |
| LOAN_SYNTHETIC_VERIFICATION | Only allowed in local profile |
| LOAN_LEDGER_ID / LEDGER_URL | Operator-selected lender SEK ledger and HTTP endpoint |
| LEDGER_BEARER_TOKEN | Service bearer credential for a secured ledger |
| S3_ENDPOINT / BUCKET / REGION / ACCESS_KEY / SECRET_KEY | Private S3 storage |
| CLAMAV_HOST / PORT | ClamD INSTREAM endpoint; absent scanner explicitly quarantines |
| AI_PROVIDER | mock, openai or ollama |
| AI_MODEL / ENDPOINT / API_KEY | Operator-selected model, endpoint, and credential; no external default model |
| AI_DATA_HANDLING_APPROVED | Required for any external/non-mock provider |
| LOAN_WORKERS_ENABLED | Disable in API-only replicas |
| LOAN_RETENTION_ENABLED | Enable configured worker-side retention |
| loan.product.* | Versioned product and underwriting policy configuration |
| loan.provider-timeout / lease / max-attempts | Bounded external calls, fenced leases, retry budget |
| loan.document-concurrency / ai-concurrency / provisioning-concurrency | Independent bounded worker lanes |
| loan.max-queued-jobs | Global transactional admission bound |

Do not edit policy content while retaining its version. Every revision freezes the full policy configuration, and approval checks both version and content. The demo policy requires one fresh payslip and tax evidence; these are configurable demonstration assumptions, not statements of law.

For separate API and worker deployments, use api on API replicas and workers-enabled=true on worker replicas; workers currently also expose an HTTP server for health. A production ingress can deny worker business routes. A shared PostgreSQL queue supports multiple replicas using SKIP LOCKED and fencing.

## Load measurement

~~~powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File loan-service/scripts/load.ps1 -Applications 100 -Concurrency 4 -Submit
docker stats --no-stream
~~~

Unix with PowerShell 7 installed: bash loan-service/scripts/load.sh -Applications 100 -Concurrency 4 -Submit. The generator reports intake/status percentiles, errors, request throughput, sampled JVM heap and backlog growth. It retains synthetic application records. It excludes document processing and real AI throughput. See [measured verification](../docs/verification.md).

## Limits and production prerequisites

See [architecture](../docs/architecture.md), [state machine](../docs/application-state-machine.md), [AI limitations](../docs/ai-assessment-and-limitations.md), [accounting](../docs/loan-accounting.md), [recovery](../docs/recovery-and-idempotency.md), and [security](../docs/security-and-production-readiness.md).

The embedded extractor supports labelled financial lines in the synthetic fixtures. Arbitrary prose, OCR and complex statement reconciliation require reviewer transcription or deployment-specific extraction/OCR adapters. Extracted evidence never proves authenticity. KYC and bureau mocks are synthetic and are forbidden outside local; real provider implementations and contracts are deployment work. No payouts, agreement acceptance, collections or servicing are implemented.

