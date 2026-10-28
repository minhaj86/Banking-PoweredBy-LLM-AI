# Placeholder Bank - Executed web and IAM verification

Verified on **28 October 2026** on Windows / PowerShell with JDK 21.0.12, Node 24.22.1, Docker Desktop and Chromium. The normal deployment was left running at `http://localhost:18082` with **two BFF replicas**. Existing database/storage volumes, local keys, users, authenticators and synthetic workflow records were preserved.

## Final results

| Check | Result |
|---|---|
| Root Maven `verify` | **144 passed**, 0 failures, 0 errors, 0 skipped; 2m29s |
| ledger-core | 86 unit / PostgreSQL integration tests |
| loan-service | 44 unit / PostgreSQL, storage and real-ledger integration tests |
| loan-bff | 14 unit / HTTP security / real-Redis integration tests |
| Frontend type check and ESLint | Passed; no lint warnings |
| Vitest / Testing Library | **22 passed** across 6 files |
| Production frontend build | Passed; staff routes and PDF viewer split into lazy chunks |
| Real-Keycloak Playwright suite | **11 passed**, no retries; 2.1 minutes |
| Two-context provider logout regression | **6 consecutive passes** after the principal-wide logout configuration correction |
| Same-origin Vite overlay | Started healthy; real confidential PKCE / shared sessions / CSRF / logout test passed |
| Running OpenAPI contract | 18 paths, decimal-string schemas and required version/idempotency headers verified |
| Editor diagnostics | No errors found |
| Read-only BFF load | 300 requests, 4 independent real cookie sessions, **0 errors** |

The final root Maven run completed at 15:05:28 local time; the final load was recorded at 14:16:50 UTC. Test reports are generated under each Java module's `target/surefire-reports` and `target/failsafe-reports`, and `loan-web/playwright-report`. These generated reports and synthetic screenshots are ignored by version control. The counts above come from the final XML reports and successful command exits, rather than treating unexecuted tests as passing.

## Commands executed

From the repository root, with `JAVA_HOME` set to the installed JDK 21 and Node 24 on `PATH`:

```powershell
.\mvnw.cmd verify
.\mvnw.cmd -pl loan-bff -am verify

Push-Location loan-web
npm.cmd run typecheck
npm.cmd run lint
npm.cmd test
npm.cmd run build
npm.cmd run test:e2e
npx.cmd playwright test security.spec.ts --grep 'provider initiated' --repeat-each=6
npm.cmd run test:load
Pop-Location

docker compose -f compose.yaml -f compose.iam.yaml exec -T loans curl -fsS http://localhost:8081/v3/api-docs | Set-Content -Encoding UTF8 .iam/loan-openapi.json
npm.cmd --prefix loan-web run test:contract
```

The real-ledger proof is executed by the Playwright journey as a Node-side Docker fixture, using `docker compose -f compose.yaml -f compose.iam.yaml run --rm --no-deps ledger-proof <loan-id> <ledger-id> <account-id>`. It has the limited `loan-ledger` credential, not a browser or ledger-admin credential.

The development overlay was started and tested, then stopped and replaced by the static deployment:

```powershell
docker compose -f compose.yaml -f compose.iam.yaml -f compose.dev.yaml up -d --no-build --wait --wait-timeout 240 web-dev web
Push-Location loan-web
npx.cmd playwright test auth.spec.ts --grep 'real confidential'
Pop-Location
docker compose -f compose.yaml -f compose.iam.yaml -f compose.dev.yaml stop web-dev
docker compose -f compose.yaml -f compose.iam.yaml build bff-a web
docker compose -f compose.yaml -f compose.iam.yaml up -d --no-build --wait --wait-timeout 240 bff-a bff-b web
```

The additive Keycloak client migration was applied with `docker compose -f compose.yaml -f compose.iam.yaml run --rm --no-deps iam-bootstrap`. Local initialization, full-stack setup, credentials, canonical URLs and Unix command equivalents are in [Keycloak setup](keycloak-local-setup.md) and [deployment](deployment-and-scaling.md). Unix equivalents are documented for reproduction; they were not executed on a separate Unix host.

## What the tests prove

The real-authentication journey registers a synthetic applicant, verifies the Mailpit email, saves the eight-step draft, uploads synthetic PDFs, waits for real ClamAV scans, opens the authenticated PDF.js preview, submits revision 1, and signs an officer into Keycloak's native OTP step-up flow. The officer requests information; the applicant replaces evidence and submits revision 2; the officer explicitly acknowledges the findings and approves the exact assessed terms. The same decision intent replays successfully, while a new stale intent returns 409.

It then waits for the actual durable provisioning job and asserts an unfunded loan awaiting agreement, actual ledger balance/debits/credits of `0.00`, no journal entries for the provisioned account, and the stable `loan:<id>:principal` reference. An authorized operations retry returns the same loan/account mapping. The machine identity is denied journal posting with 403. Approval does not accept an agreement, disburse money or create an ACTIVE loan.

Authentication tests cover real code/PKCE login, session fixation protection, state/nonce/PKCE tampering, invalid password, email/password recovery, native staff OTP, lower-assurance denial, exact return-path restrictions, CSRF, forged logout-token rejection, two-replica session reuse, and two separate browser contexts invalidated by a provider logout. A role is removed through authorized test IAM administration; after token refresh, twelve concurrent requests lose the capability without refresh-token reuse failure. The test restores group membership afterward. Browser storage and API projections are checked for OAuth token exposure.

BFF tests additionally validate real RSA JWT signatures/issuer/audience/expiry and encrypted shared Redis state. Concurrent refresh uses two replica objects and a barrier; logout racing an in-flight refresh uses latches and proves that credentials cannot be restored. Maximum session lifetime removes token material. Backend tests cover ownership/tenant boundaries, verified email, actual staff-assurance gates, self-review denial, multi-role applicant ownership, encrypted/versioned partial drafts, idempotency and queue filtering. Existing PostgreSQL commit/concurrency and real-ledger tests remain in the root run.

Component tests cover wizard validation/navigation, saving and failed/conflicting saves, edits made while a save is in flight, upload quarantine/errors, permission-aware navigation, identity/session cache clearing, decision confirmation, API errors and exact financial display.

Automated axe checks passed for the applicant dashboard, mobile wizard, document workspace, staff review and mobile loan page. Manual visual inspection covered the public page and the generated desktop document/review and mobile wizard/loan screenshots. Mobile assertions check for page-wide horizontal overflow; the repayment table remains a keyboard-focusable horizontal scroll region. This is not a claim of a full assistive-technology certification.

## Measured load

`npm.cmd run test:load` runs `node scripts/load.ts --requests 300 --concurrency 4`. The latest measured run used the final BFF image and real Keycloak cookie sessions. Requests read `/bff/me`, the applicant queue and the current product. Login setup is outside the timed request phase. Both BFF replicas use the shared Redis store.

| Measurement | Observed |
|---|---|
| Requests / concurrency / errors | 300 / 4 / 0 |
| Timed phase / throughput | 1,477.39 ms / 203.06 requests per second |
| Latency p50 / p95 / p99 / maximum | 18.74 / 30.79 / 37.78 / 40.01 ms |
| Redis connected clients | 7 before and after |
| Redis used memory | 2,797,920 to 2,812,144 bytes |
| Redis commands during phase | 2,702 |
| Redis hits / misses | +700 / +200 |
| Redis rejected connections | 0 |
| Downstream job backlog | 0 before and after |
| Loan-service heap used | 82,216,568 to 77,874,784 bytes |

The raw local measurement is saved to ignored `.iam/bff-load.json`. This short, warm local read workload is **not** a production capacity target or a measurement of AI, document-processing, ledger-write or sustained-load throughput. Other clients and ordinary JVM garbage collection can affect these measurements.

## Corrections and limits

Earlier runs found and corrected a draft version mismatch, Keycloak's missing standard access-subject scope, PDF worker MIME/CSP behavior, accessible progress names and mobile overflow. The development axe test initially scanned before the dashboard heading had loaded; it now waits for that visible page state. A stronger two-browser logout test exposed SID-only revocation leaving another login active; the configured signed, principal-wide policy now clears both. The final successful suites above ran after these corrections. Transient Docker Desktop interruption during an earlier heavy parallel build was recovered without deleting volumes; final builds and verification completed successfully.

Production TLS, mail delivery, Redis resilience/restore, a production IAM rollout, real financial KYC/bureau/AI providers, external penetration testing, screen-reader testing and production capacity testing were not exercised by this local verification. Financial providers are explicitly synthetic in the IAM demo; document scanning and IAM are real. No BankID, agreement signing, funding or repayment workflow is implemented. Assignment and configuration editing remain absent because there are no suitable versioned backend workflows; configuration is read-only. English copy and UTC display are the initial presentation settings.

The legacy backend-only shell demos were not rerun while the real-IAM overlay was active. Their existing backend correctness, concurrency, storage and real-ledger regression tests did run successfully in root `verify`. The local setup is a working integration foundation, not production or legal/compliance certification.
