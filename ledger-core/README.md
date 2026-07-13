# Placeholder Bank - Finance Ledger Core

A working Java 21 / Spring Boot double-entry ledger with PostgreSQL, immutable journals, race-safe idempotency, full reversals, account closure, balances, history, and trial balances.

This service is the `ledger-core` Maven module of the [parent project](../README.md). The [parent POM](../pom.xml) owns shared versions and plugin management; this module owns the application dependencies and Spring Boot packaging.

Run the commands on this page from the `ledger-core` directory. The Maven Wrapper is shared in the parent directory. To verify every module from the parent directory, use `.\mvnw.cmd verify` or `sh ./mvnw verify`; to launch this service there, add `-pl ledger-core` before `spring-boot:run`.

This is a local development foundation, not a production-certified banking platform. Authentication, authorisation, operational auditing, backup/restore validation, and production security hardening remain necessary.

## Run with Docker

Prerequisite: Docker Engine/Desktop with Linux containers and Compose v2. No host JDK is needed to run the containerised service.

PowerShell:

```powershell
Copy-Item .env.example .env
docker compose up -d --build --wait
Invoke-RestMethod http://localhost:8080/actuator/health
.\scripts\demo.ps1
```

If this Windows machine disables script files, run the demo with a process-only policy override:
`powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\demo.ps1`.
This does not change the machine's persistent execution policy.

Unix shell:

```sh
cp .env.example .env
docker compose up -d --build --wait
curl --fail http://localhost:8080/actuator/health
bash scripts/demo.sh  # requires curl and jq
```

The service is at <http://localhost:8080>, Swagger UI at <http://localhost:8080/swagger-ui/index.html>, and OpenAPI JSON at <http://localhost:8080/v3/api-docs>. Both published ports bind to **127.0.0.1**. Change APP_PORT or DB_PORT in your environment or .env if those ports are occupied. Supply the new URL to either demo script.

`docker compose down` stops the service and preserves its database volume. Bootstrap runs only on a new database volume; changing passwords in .env does not change an existing database's role passwords.

The defaults in [.env.example](.env.example) are explicitly development-only. The app connects as non-superuser `ledger_app`; Flyway connects as separate non-superuser schema owner `ledger_migrator`. A bootstrap superuser creates the two roles and schema. Runtime cannot alter schema, disable triggers, delete, or truncate journals. For local simplicity, the application process receives both sets of credentials and runs Flyway at startup. Production should run migrations separately and withhold migration credentials from the application process.

## Build and test on the host

Install **JDK 21**, set JAVA_HOME to its installation directory, and start Docker. Maven 3.9.11 is downloaded by the included Apache Maven Wrapper 3.3.4 and checked against a pinned SHA-256. Spring Boot 3.5.16, springdoc 2.8.17, Testcontainers 1.21.4, and PostgreSQL 17.10 are pinned; Boot manages compatible transitive dependency versions. Docker Java base images are digest-pinned.

PowerShell (replace the JDK path with your actual installation):

```powershell
$env:JAVA_HOME = 'C:\path\to\jdk-21'
..\mvnw.cmd verify
```

Unix shell:

```sh
export JAVA_HOME=/path/to/jdk-21
sh ../mvnw verify
```

Normal **verify runs both unit and PostgreSQL integration tests**, and builds the executable JAR. Docker is required for integration tests; there is no H2 fallback and no automatic test skipping. Tests provision a disposable PostgreSQL container with separate migration and restricted runtime roles. They do not use the Compose database. Docker 29 compatibility is configured through the test-only docker-java API version.

For a faster unit-only edit cycle, use `..\mvnw.cmd test` or `sh ../mvnw test`. The executable is `target/ledger-core-0.1.0-SNAPSHOT.jar`. Test reports are in [target/surefire-reports](target/surefire-reports) and [target/failsafe-reports](target/failsafe-reports). Shared [VS Code tasks](../.vscode/tasks.json) run from the parent directory; launch the editor with JAVA_HOME set for those tasks.

## Run from source

First start just PostgreSQL. The defaults match the development credentials above.

PowerShell:

```powershell
docker compose up -d db --wait
..\mvnw.cmd spring-boot:run
# In a second terminal:
.\scripts\demo.ps1
```

Unix:

```sh
docker compose up -d db --wait
sh ../mvnw spring-boot:run
# In a second terminal:
bash scripts/demo.sh
```

Stop the Compose app before starting a host app on the same port. Host configuration comes from environment variables; Spring does not load Compose's .env file automatically.

| Variable | Default | Purpose |
| --- | --- | --- |
| DB_URL | jdbc:postgresql://localhost:5432/ledger | Runtime and migration database |
| DB_USER / DB_PASSWORD | ledger_app / dev-app-only | Restricted runtime connection |
| MIGRATION_DB_USER / MIGRATION_DB_PASSWORD | ledger_migrator / dev-migration-only | Flyway schema owner |
| PORT | 8080 | Host application HTTP port |
| APP_PORT / DB_PORT | 8080 / 5432 | Compose host port mappings |
| POSTGRES_PASSWORD | dev-bootstrap-only | Compose bootstrap role only |

For an existing PostgreSQL database, an administrator must create the roles/schema first; see [bootstrap SQL](docker/postgres/init.sh). The Flyway runtimeRole placeholder is an operator-controlled SQL identifier, never user request data.

## Demo and accounting perspective

The scripts create a new SEK ledger per run, post each operation, replay the deposit, assert exact balances, reverse the fee, and check that both fee entries remain visible. Demo data never appears in migrations.

| Operation | Debit | Credit |
| --- | --- | --- |
| Deposit 1000.00 | Bank Cash (asset) | Customer A Deposits (liability) |
| Transfer 200.00 | Customer A Deposits | Customer B Deposits |
| Fee 10.00 | Customer A Deposits | Fee Revenue |

Before reversal: Bank Cash **1000.00**, Customer A **790.00**, Customer B **200.00**, Fee Revenue **10.00**. After reversal: Customer A **800.00**, Fee Revenue **0.00**. Cash is debit-normal; the customer liabilities and revenue are credit-normal. The ledger always represents the bank/business's perspective.

## API

All paths below start with `/api/v1`. UUIDs are scoped to the requested ledger.

| Method | Path | Result |
| --- | --- | --- |
| POST / GET | /ledgers / /ledgers/{ledgerId} | Create / retrieve a ledger |
| POST / GET | /ledgers/{ledgerId}/accounts / .../accounts/{accountId} | Create / retrieve an account |
| POST | /ledgers/{ledgerId}/accounts/{accountId}/close | Close only at zero balance |
| GET | /ledgers/{ledgerId}/accounts/{accountId}/balance | Debit/credit totals and normal-direction balance |
| GET | /ledgers/{ledgerId}/accounts/{accountId}/entries | Ordered paginated history |
| POST / GET | /ledgers/{ledgerId}/transactions / .../transactions/{transactionId} | Post / retrieve a journal |
| POST | /ledgers/{ledgerId}/transactions/{transactionId}/reversals | Fully reverse an original |
| GET | /ledgers/{ledgerId}/trial-balance | Single-snapshot account and ledger totals |

Posting and reversal require `Idempotency-Key`: 1..128 ASCII letters/digits or `._:-`. First success **and replay both return 201**, with the same immutable body, transaction identifier, recordedAt, and Location. Responses may have different correlation identifiers. Text fields and **entry ordering are significant** for idempotency; monetary representations like `"1"`, `"01.00"`, and `"1.000"` are equivalent.

Example posting body (use created account UUIDs):

```json
{
  "externalReference": "customer-deposit-001",
  "description": "Customer deposits 1000 SEK",
  "entries": [
    { "accountId": "<cash-id>", "side": "DEBIT", "amount": "1000.00" },
    { "accountId": "<customer-a-id>", "side": "CREDIT", "amount": "1000.00" }
  ]
}
```

Unix example: save that body to deposit.json, then:

```sh
curl --fail-with-body -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: deposit-001' --data-binary @deposit.json \
  "http://localhost:8080/api/v1/ledgers/$ledgerId/transactions"
```

PowerShell:

```powershell
Invoke-RestMethod -Method Post -ContentType 'application/json' `
  -Headers @{ 'Idempotency-Key' = 'deposit-001' } `
  -InFile deposit.json -Uri "http://localhost:8080/api/v1/ledgers/$ledgerId/transactions"
```

Reversal body: `{"reason":"Fee charged in error"}`. Partial reversal fields are rejected as unknown input. A new key for an already reversed transaction returns 409. Replaying the original successful key remains successful even if accounts later close or balances change.

Amounts must be JSON **strings**, with plain unsigned decimal notation and at most 64 characters. SEK, EUR, and USD have two fractional digits. Each line is at most **999999999999.99**; each transaction's debit total and credit total are at most **9999999999999.99**, with **2..100 lines**. Input requiring rounding is rejected. Trailing fractional zeroes are accepted and normalised. Aggregate lifetime totals have no additional fixed application cap: PostgreSQL arbitrary-precision NUMERIC and BigDecimal calculate exact sums rather than storing a bounded balance column.

Currency is inherited from the ledger; entry-level currencies and unknown fields are rejected. Ledger currency and account ledger/type cannot change. Codes are case-sensitive, 1..50 letters/digits/underscore/hyphen, starting with a letter or digit. Names max 200, descriptions/reasons max 500, external references max 200. Missing optional text and explicit null are equivalent; empty strings and other whitespace are preserved.

History uses `?page=0&size=50&from=2026-01-01T00:00:00Z&to=2027-01-01T00:00:00Z`, with inclusive from and exclusive to. Page is 0..100000 and size 1..200. Ordering is recordedAt, transaction UUID, line number. These timestamps and UUIDs **do not represent exact commit order**. Offset pages can shift during concurrent posting; there is no stable cross-request snapshot.

| Status | Meaning |
| --- | --- |
| 201 | Created ledger/account/journal; journal replay also 201 |
| 200 | Retrieval, report, closure (including already closed) |
| 400 | Invalid JSON, non-string amount, bean validation, key, enum, or history range |
| 404 | Missing resource or resource outside requested ledger |
| 422 | Invalid monetary value, unbalanced journal, closed account, negative protected balance, forbidden reversal |
| 409 | Duplicate code, idempotency conflict, already reversed, nonzero closure, database constraint conflict |
| 503 | Unavailable database or exhausted transient retries |
| 500 | Unexpected server error |

Errors use application/problem+json with stable `code`, `message`, and `correlationId`:

```json
{
  "type": "urn:ledger:error:negative_balance",
  "title": "Unprocessable Entity",
  "status": 422,
  "detail": "Posting would make a protected account's normal balance negative",
  "code": "NEGATIVE_BALANCE",
  "message": "Posting would make a protected account's normal balance negative",
  "correlationId": "request-001"
}
```

Supply an optional safe `X-Correlation-ID` (1..64 letters/digits or `._:-`); otherwise the server creates one. It is returned in the response header and included in JSON logs. Logs contain operation IDs, status, latency, and retry events, never request bodies, credentials, or idempotency keys.

Read [accounting model](docs/accounting-model.md), [architecture](docs/architecture.md), [concurrency and idempotency](docs/concurrency-and-idempotency.md), [trade-offs](docs/trade-offs.md), and [verification results](docs/verification.md).
