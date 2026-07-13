# Placeholder Bank - Verification results

## Maven module layout verification — 2026-10-28

The root is now the `com.example:finance-ledger-parent` Maven parent/aggregator with the `com.example:ledger-core` application module. All 73 files under the former src directory were SHA-256 checked before and after the move; their contents are unchanged.

Executed from the parent directory with JAVA_HOME set to JDK 21:

```powershell
.\mvnw.cmd -B -ntp verify
docker compose -f ledger-core/compose.yaml config --quiet
docker compose -f ledger-core/compose.yaml up -d --build --wait
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\ledger-core\scripts\demo.ps1
```

Results: both Maven reactor projects succeeded; **26 unit and 58 PostgreSQL integration tests passed**, with no failures, errors, or skipped tests. Docker built the module JAR and both services became healthy. The relocated PowerShell demo reproduced the expected balances, idempotent replay, reversal, and retained history.

The existing `finance-ledger-core_ledger-data` database volume was retained. Its eight pre-existing journal transactions were confirmed before running the new demo. No existing ledger data was deleted.

Also executed `..\mvnw.cmd -B -ntp validate` from the module directory: **BUILD SUCCESS**, resolving the parent through ../pom.xml. The service artifact is now `ledger-core/target/ledger-core-0.1.0-SNAPSHOT.jar` relative to the parent. Documentation links resolve, and editor diagnostics reported no errors after the Java project configuration refreshed.

The records below describe the initial implementation **before** the module move. Their commands and absolute paths show the original layout; use the commands above or the [parent README](../../README.md) for the current layout.

Executed on 2026-10-28 (Europe/Stockholm) on Windows PowerShell with Temurin JDK 21.0.12.1+1, Docker Desktop's Linux engine 29.8.2, Maven Wrapper / Maven 3.9.11, and PostgreSQL 17.10.

## Final build and tests

Actual command, after setting JAVA_HOME to the installed JDK 21:

```powershell
.\mvnw.cmd -B -ntp verify
```

Result: **BUILD SUCCESS**, 26 unit tests + 58 PostgreSQL integration tests = **84 passed**, zero failures, zero errors, zero skipped.

| Suite | Tests |
| --- | ---: |
| MoneyTest | 15 |
| PostingTest | 8 |
| TransactionExecutorTest | 3 |
| AccountingRulesIT | 18 |
| ApiIT | 9 |
| ConcurrencyIT | 7 |
| DatabaseSafeguardsIT | 18 |
| IdempotencyAndReversalIT | 5 |
| LedgerPostingIT | 1 |

The build produces the executable JAR in [target](../target/). Surefire and Failsafe XML reports are generated under that directory. Integration tests use a disposable real PostgreSQL container, migrate with a distinct schema owner, and exercise the application through its restricted non-superuser role.

Earlier incremental runs exposed a duplicate Hibernate column mapping, a test helper/static-import naming collision, and an incorrect expected OpenAPI path count. All were fixed before the successful final run. The final Swagger examples were also checked as JSON objects containing string amounts, with creation endpoints documented as 201.

## Local Docker service

Executed:

```powershell
docker compose up -d --build --wait
docker compose ps
Invoke-RestMethod http://localhost:8080/actuator/health
```

Result: image built successfully, both app and database healthy, health returned `{"status":"UP"}`. The final source was rebuilt into the running application after the Swagger change. The app and database ports are bound to 127.0.0.1:8080 and 127.0.0.1:5432.

The image build packages without running tests inside Docker; the host Maven verify command above ran the full test suite against PostgreSQL. These are separate checks.

## PowerShell demo

The initial direct invocation was blocked by this machine's execution policy. It was then run successfully with a **process-only** override, without changing persistent policy:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\demo.ps1
```

Verified via live HTTP:

- Deposit replay returned the same transaction and recordedAt.
- Before reversal: Bank Cash 1000.00, Customer A 790.00, Customer B 200.00, Fee Revenue 10.00.
- After fee reversal: Customer A 800.00, Fee Revenue 0.00.
- Revenue history contained both original and reversal lines with the original reference.
- Trial debit and credit movement totals were both 1220.00; closing debit and credit totals matched.

## Bash demo

Also ran the Unix script from a temporary Linux container connected to the same Compose network. The container installed jq only for the check and was removed automatically; no utility packages were added to the service image:

```powershell
docker run --rm --network finance-ledger-core_default `
  --mount 'type=bind,source=C:\Users\rubai\OneDrive\code\finance-ledger-core\scripts,target=/demo,readonly' `
  eclipse-temurin:21-jdk-jammy `
  bash -c 'apt-get update -qq && apt-get install -y -qq jq >/dev/null && bash /demo/demo.sh http://app:8080'
```

Result: **Demo passed**, independently reproducing the required balances, idempotent deposit replay, fee reversal, retained history, and balanced report.

Both demos create independent ledgers and intentionally retain their data. The Compose stack is left running; `docker compose down` stops it without removing the database volume.

## Correctness review

Reviewed the money and balance sign rules, composite ledger ownership foreign keys, journal insert/finalisation guards, deferred commit checks, idempotency reservation/replay visibility, reversal ordering, and transaction boundaries.

The concurrency tests coordinate distinct backend connections with barriers/latches. In the 100.00 funding / two concurrent 80.00 spends test, exactly one spend succeeded, source balance was 20.00, and counts proved that the losing attempt left no header, entries, or idempotency record. Separate tests covered concurrent matching/conflicting keys, reversals, closure versus posting, and single-snapshot reports.

Database tests explicitly attempt COMMIT for incomplete/unbalanced drafts, attempt late insertion into posted journals as the schema owner, and inject a second-line database failure to prove atomic rollback and reuse of the rolled-back key. Unit tests verify bounded retries in fresh transactions, including commit-time failures, and no retries for business/constraint errors.

Editor diagnostics reported no errors after the final code changes. No required build, PostgreSQL, or demo check remained blocked. Production security, disaster recovery, operational auditing, and load/failover testing were not performed; see [trade-offs](trade-offs.md).
