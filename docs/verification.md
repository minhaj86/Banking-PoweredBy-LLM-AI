# Placeholder Bank - Verification and local measurements

Verified on 28 October 2026 on Windows with JDK 21.0.12.1, the Maven Wrapper (Maven 3.9.11), Docker Desktop 29.8.2, eight Docker CPUs and 15.54 GiB Docker memory. All applicants, documents and external verification results used synthetic data.

## Final build and tests

From the repository root, JAVA_HOME was set to the installed JDK 21. The final command was:

~~~powershell
.\mvnw.cmd -B -ntp verify
~~~

Result: BUILD SUCCESS in 2 minutes 4 seconds. Both executable JARs were built.

| Module | Surefire unit tests | Failsafe integration tests | Failures / errors / skipped |
| --- | ---: | ---: | --- |
| ledger-core | 26 | 60 | 0 / 0 / 0 |
| loan-service | 15 | 24 | 0 / 0 / 0 |
| Total | 41 | 84 | 0 / 0 / 0 |

**125 tests executed; none skipped.** Counts were checked from the final XML reports in each module's target directory. Earlier unit-only and focused PostgreSQL/storage commands also passed after the documented fixes; the final root verify includes their current tests.

The integration suites use real PostgreSQL Testcontainers, restricted runtime users and separate concurrent transactions. They cover existing ledger invariants, concurrent external-reference provisioning, secured SERVICE routes, application ownership/roles/tenants, version/idempotency races, approval versus withdrawal, immutable terms, evidence gates, manual extraction, separate PEP/compliance review, retention/legal hold, AI validation/failure, queue capacity, leases/fences, outage/lost-response/local-mapping recovery, and reconciliation.

RealLedgerIT runs the packaged ledger-core service over HTTP with a separate PostgreSQL instance. It verifies a lost response after actual remote account commit, a real paused/unpaused ledger outage, recovery of the same account, ASSET/DEBIT attributes, zero balances and no journal entries. The scanner and KYC/bureau providers are synthetic in this test; document security scanning is exercised separately by the live Compose demo.

S3StorageIT uses an actual authenticated SeaweedFS S3 server. It verifies encrypted write/head/read/delete and asserts that stored ciphertext starts with the encryption envelope rather than AWS chunk frames. No in-memory storage substitute is used for that test.

## Actual local commands and results

| Command | Result |
| --- | --- |
| powershell.exe -NoProfile -ExecutionPolicy Bypass -File loan-service/scripts/init-local.ps1 | Generated random ignored local credentials; subsequent runs preserve the file |
| docker compose config --quiet | Valid configuration |
| docker compose up -d --build --wait --wait-timeout 600 | Complete root stack built and started; bootstrap jobs exited 0 |
| docker compose up -d --build --no-deps --wait loans | Final loan image rebuilt with the last approval/queue/AI safeguards; healthy |
| powershell.exe -NoProfile -ExecutionPolicy Bypass -File loan-service/scripts/demo.ps1 -Recovery -FailureCases | Passed on the final running image |
| bash loan-service/scripts/demo.sh --failure-cases | Passed in a temporary Alpine 3.21 container against the actual stack |
| powershell.exe -NoProfile -ExecutionPolicy Bypass -File ledger-core/scripts/demo.ps1 -BaseUrl http://localhost:18080 | Existing ledger demo passed; expected deposit/transfer/fee/reversal balances and balanced trial totals |
| powershell.exe -NoProfile -ExecutionPolicy Bypass -File loan-service/scripts/load.ps1 -Applications 100 -Concurrency 4 -Submit | Passed, 300 successful requests, no errors |
| PowerShell parser and bash -n for all supplied scripts | Syntax checks passed |
| VS Code Problems for both modules | No errors reported |

The Unix demo ran with LOAN_URL=http://loans:8081 and LEDGER_DEMO_URL=http://ledger:8080 on the existing private Compose network. Its temporary container mounted the repository read-only and installed Bash, curl, jq and OpenSSL. PowerShell exercised ledger stop/start recovery; the Unix container run exercised the normal flow and failure cases without Docker control inside that container.

Both demo variants verified explicit officer approval, identical replay results, one principal-receivable mapping/account, AWAITING_AGREEMENT with NOT_ACCEPTED agreement, zero financial balance and no entries. Missing-document and affordability-concern applications stayed pending human action. A synthetic AI outage exhausted retries to ASSESSMENT_FAILED with no credit rejection.

The root stack remains running at http://localhost:18081 (loans) and http://localhost:18080 (ledger). All generated credentials, synthetic data and named volumes were preserved. The ledger-only Compose project and its volumes were not removed.

## Measured API load

Final measurement timestamp: 2026-10-28T11:00:49Z. The generated workload was 100 synthetic applications at concurrency four, each with create, submit and processing-status requests. This is a short warmed local smoke measurement, not a capacity benchmark.

| Measurement | Observed |
| --- | ---: |
| Completed HTTP requests | 300 |
| Elapsed workload time | 1.977 seconds |
| Total request throughput | 151.75 requests/second |
| Created/submitted applications | 100 |
| HTTP / transport errors | 0 |
| Intake latency p50 / p95 / p99 | 20.50 / 40.72 / 41.36 ms |
| Status latency p50 / p95 / p99 | 7.82 / 14.30 / 22.88 ms |
| Durable backlog before / immediately after | 0 / 96 |
| Maximum sampled backlog | 96 |
| Maximum sampled JVM heap during workload | 145,715,744 bytes (about 139 MiB) |
| JVM heap immediately after workload | 77,934,576 bytes |

A later operations snapshot showed backlog zero. Worker polling/concurrency deliberately drained more slowly than intake. These measurements exclude uploads, OCR, real verification providers, real AI model calls and meaningful human-review throughput. The applications intentionally lacked supporting documents and were routed to evidence-required review. No completed-AI-assessments-per-second claim is made.

A later docker stats --no-stream snapshot, after the backlog drained, reported:

| Container | CPU snapshot | Memory snapshot |
| --- | ---: | ---: |
| loan-service | 0.76% | 546 MiB |
| ledger-core | 0.21% | 383.4 MiB |
| loan PostgreSQL | 4.82% | 76.03 MiB |
| ledger PostgreSQL | 0.00% | 64.15 MiB |
| ClamAV | 0.01% | 968.7 MiB |
| S3 storage | 0.23% | 33.72 MiB |

These container values are later point samples, not peak workload CPU/RSS. JVM heap was sampled during the workload. No machine-wide peak resource claim is inferred.

## Failures found and fixed

The original pinned MinIO images could not be pulled from the registry; the local stack was changed to the verified SeaweedFS image while retaining the S3 adapter boundary. AWS SDK automatic chunk framing was incompatible with that local server version and initially produced unreadable encrypted objects; framing was disabled, content checksum/authentication retained, and a real-storage regression test added. The first S3 test's loopback bind prevented host access; its test server now explicitly binds all container interfaces.

The legacy API test's expected route count was updated for the two additive ledger routes. Test-only spy setup failures were corrected. A BusyBox date incompatibility in the Unix demo was corrected and the complete Unix demo rerun successfully. Initial failed synthetic records/objects were preserved rather than deleting volumes or user data.

Docker image packaging intentionally uses skipTests. Those packaging invocations are not counted as test verification; the 125-test root verify above executed on the host with real containers.

## Checks not performed and reasons

- Live OpenAI/Ollama assessment requests: no approved provider credentials/operator-selected deployed model were supplied. Spring AI adapters compile; mock behavior, malformed/refused responses, timeouts and evidence validation are tested.
- Real identity, compliance or bureau verification: no real provider integrations/contracts were configured. All local verification is explicitly synthetic. Production rejects development shortcuts and unavailable real gates block approval.
- Live external OIDC issuer discovery/key rotation: no issuer was supplied. Local signed JWT authentication was exercised end to end; secured role/tenant behavior was integration tested.
- Native macOS/BSD execution and the Unix PowerShell load wrapper: this host is Windows. Bash demos/syntax were exercised in Linux; the load generator ran in Windows PowerShell.
- Production parser sandboxing, encryption-key rotation, legal/compliance certification, real lending or funding, and production-scale AI/human throughput: outside the implemented local origination scope and require the prerequisites in [security and production readiness](security-and-production-readiness.md).

No unexecuted check or mock verification is reported as a real passing external verification.

