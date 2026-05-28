# Ledger invariants

- Java 21, Maven Wrapper, PostgreSQL only for database correctness tests. Preserve the existing package boundaries.
- The root [pom.xml](pom.xml) is the Maven parent/aggregator; the complete service lives in [ledger-core/](ledger-core/). Keep shared version management and the Maven Wrapper at the root.
- Money uses BigDecimal / exact NUMERIC and JSON decimal strings. No implicit rounding, float, double, or direct balance writes.
- A posted journal has 2..100 lines, at least two distinct accounts, both sides, equal debit/credit totals, and only active accounts from its ledger.
- Headers and entries are immutable, including late INSERT. Keep deferred commit checks, foreign keys, reversal uniqueness, and runtime privilege restrictions.
- Post and reverse use one atomic transaction for the idempotency claim, header, every entry, and finalisation.
- Lock all affected account rows in PostgreSQL UUID order. Read aggregates in a separate statement after all locks. Hold locks to commit. Closure follows the same protocol.
- Aggregate repeated-account lines before checking protected normal balances. Reversals never bypass account rules.
- Idempotency is scoped by ledger + operation + key; never catch a uniqueness exception and query in an aborted transaction.
- Keep controllers thin, DTOs immutable, constructor injection, and entities out of API responses.
- Never delete user data or demo volumes as a routine cleanup.

## Verification

Set JAVA_HOME to a JDK 21 and start Docker. Run these commands from the parent directory.
- Windows: `./mvnw.cmd verify`; `./ledger-core/scripts/demo.ps1`.
- Unix: `sh ./mvnw verify`; `bash ./ledger-core/scripts/demo.sh` (curl and jq).
- Local stack: `docker compose -f ledger-core/compose.yaml up -d --build --wait`.
- Run the service from the parent: `./mvnw.cmd -pl ledger-core spring-boot:run` (Unix: `sh ./mvnw -pl ledger-core spring-boot:run`).
- Unit tests only: `./mvnw.cmd test` or `sh ./mvnw test`.
- verify runs Surefire unit tests and Failsafe PostgreSQL integration tests. Never claim skipped/unexecuted tests passed.
- Concurrency tests must use separate connections/transactions with barriers or latches. Deferred-constraint tests must attempt COMMIT.

## Loan origination invariants

- [loan-service/](loan-service/) is independently deployable and owns its own PostgreSQL database. Never share ledger JPA entities/repositories or access the ledger database from loan-service.
- Approval is not agreement acceptance or funding. Account provisioning creates an ASSET, DEBIT-normal principal receivable at zero balance, with no journal and no ACTIVE lifecycle. Keep outstanding balances exclusively in ledger-core.
- Freeze submitted form/document/product/policy versions. Additional evidence requires a new revision. Persist submission/approval and their durable jobs atomically. Reject stale processing results and decisions.
- Release database locks/transactions before storage, scanner, KYC, bureau, AI and ledger HTTP calls. Use leases and fences for at-least-once workers and stable external references for remote recovery.
- Financial arithmetic uses BigDecimal, explicit rounding and exact principal reconciliation. Missing financial facts stay unknown. Extraction is not independent verification.
- AI receives only approved financial inputs and validated sources. It has no decision, ledger, payment or database tools. Never make a final decision from an advisory result or a technical failure.
- Require JWT-derived tenant/ownership, explicit officer review, current verification gates and material acknowledgements. Compliance/PEP is separate from credit risk.
- Keep uploads private, encrypted and quarantined until a real clean scan. Scanner/OCR unavailability requires review; never invent clean/verified results.
- Preserve append-only decisions, terms, revisions and minimal audit records. Sensitive erasure is a controlled retention operation with legal-hold checks, not unrestricted CRUD.
- Run root verify for the real-ledger end-to-end test: it needs the sibling packaged ledger JAR. Loan database/concurrency tests use PostgreSQL Testcontainers only.
- Complete synthetic demo: root Compose plus [loan demo](loan-service/scripts/demo.ps1) or [Unix demo](loan-service/scripts/demo.sh). Preserve all volumes and generated local keys.

## Web and IAM invariants

- [loan-bff/](loan-bff/) owns standard OIDC code/PKCE login and encrypted shared Redis authentication state. Keep OAuth access/refresh/ID tokens server-side. Never add a browser token/password endpoint or BFF IAM-admin/ledger credential.
- [loan-web/](loan-web/) uses strict TypeScript, decimal strings and the real loan API through one same-origin BFF client. Persist sensitive drafts only in the encrypted backend. Keep query keys identity/tenant aware and clear sensitive caches and forms on session/identity/permission changes.
- Require CSRF for every cookie mutation, including multipart upload and logout. Restrict downstream paths, methods and query names. Keep uploads/downloads bounded and streamed through the BFF. Do not preview quarantined evidence or render uploaded/AI HTML.
- Keep Keycloak's standard `basic` scope (access-token subject), explicit API audiences, trusted tenant/role mappers and exact redirects. Staff access requires the actual native `loan-staff` OTP assurance in both BFF and loan-service; never hardcode assurance or treat IAM as financial KYC.
- Use issuer plus subject for ownership. Preserve legacy `loan-local` subjects. Reject self-review in backend operations. PLATFORM_ADMIN does not grant credit, document or ledger authority.
- Approval/provisioning remains unfunded and awaiting agreement, with actual balances read from ledger-core. No optimistic decision/verification/balance updates, automatic replay after reauthentication, bulk approval or AI acceptance shortcut.
- Run root Maven verification and `loan-web` typecheck/lint/component/build/real-Keycloak Playwright checks. Use [IAM setup](docs/keycloak-local-setup.md), preserve `.env`, `.iam` authenticator state and all volumes, and report skipped/unexecuted checks honestly.

## Developer lifecycle and branding

- Product display name is **Placeholder Bank**, with public slug `placeholder-bank`. Keep frontend branding in [brand.ts](loan-web/src/app/brand.ts). Preserve technical service names, database/migration and volume identities, IAM realm/client IDs, issuer/callback URLs and idempotency references.
- Canonical entry points are [scripts/bank.ps1](scripts/bank.ps1) and [scripts/bank.sh](scripts/bank.sh). They resolve the repository root themselves. Use `help`, `doctor`, `start`, `stop`, `restart`, `build`, `test`, `deps`, `status`, `logs` and `clean`; see [command reference](docs/developer-commands.md).
- Docker workflow: `./scripts/bank.ps1 doctor`, `build`, `start`, `deps`, `status` (Bash: `./scripts/bank.sh` with the same commands). Docker-only workflows must not require a host JDK/Maven/Node installation. Compose files are the service/dependency source of truth. `deps` means runtime container dependencies, not package audits.
- Native build: `./scripts/bank.ps1 build -Workflow native` or `./scripts/bank.sh build --workflow native`. Tests are omitted from builds. Use `test -Suite unit|integration|frontend|e2e|all` or Bash `test --suite ...`. Integration selection runs root Maven verify; all additionally runs frontend checks and real-Keycloak Playwright.
- Default IAM stack uses the existing project identity and volumes. Existing environments must not be silently moved to the new slug. New initialization may select `placeholder-bank`; reuse any explicit project/environment/stack options consistently.
- Start/restart must verify readiness; a running container alone is insufficient. Stop preserves containers/volumes. Clean uses only [clean-paths.txt](scripts/clean-paths.txt), rejects links escaping the root, and preserves `.env`, `.iam`, dependencies and runtime data. Never add prune, database reset, `down -v` or `git clean -fdx` to routine lifecycle commands.
- Keep lockfiles, Maven Wrapper files, migrations, synthetic fixtures and useful shared VS Code configuration versionable. Exclude real secrets, local data and generated output from Git and Docker contexts. Never stage all files, commit, push, replace Git metadata or change remotes as routine lifecycle setup.
