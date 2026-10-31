# Placeholder Bank

A working local banking foundation: a PostgreSQL double-entry ledger, independently deployed loan origination service, React applicant/staff portal, and a Spring OIDC BFF with real Keycloak authentication. Approval creates an unfunded loan and a zero-balance principal account awaiting agreement. It does not sign an agreement or disburse funds.

| Module / service | Responsibility |
|---|---|
| [ledger-core](ledger-core/README.md) | Immutable journals, accounts, exact balances, reversals and closure |
| [loan-service](loan-service/README.md) | Encrypted revisions/documents, processing jobs, human decisions and recoverable account provisioning |
| [loan-bff](loan-bff/) | Confidential OIDC/PKCE, CSRF, encrypted shared Redis sessions, authorized API relay |
| [loan-web](loan-web/) | Strict React/TypeScript applicant and staff workspaces |
| Keycloak / PostgreSQL / Redis / storage / ClamAV / Mailpit | IAM, separate durable databases, web sessions, private evidence, real scans and local email |

The root [pom.xml](pom.xml) is the Java 21 Maven parent/aggregator; the Maven Wrapper and shared version management stay here. Technical module names, Java packages, database/migration identities, realm/client IDs, API routes and external idempotency references are preserved. Frontend display branding is centralized in [brand.ts](loan-web/src/app/brand.ts).

The editable [draw.io architecture diagram](docs/placeholder-bank-architecture.drawio) contains ten linked pages covering the system, web/IAM, service components, processing, lifecycle, provisioning recovery, ledger transactions, both data models and deployment. Open it with diagrams.net or a draw.io editor; shapes include source references in their tooltips.

## Prerequisites and first run

Docker workflow: Git, Docker Desktop/Engine with Linux containers, and current Compose with `--wait` and `!reset` support. Host Java, Maven and Node are unnecessary for image builds and local stack commands. Native build/test additionally uses **JDK 21**, the Maven Wrapper, **Node >=24** and **npm**. Integration tests require Docker; browser tests require the ready IAM stack and Playwright Chromium. Bash secret initialization uses OpenSSL.

From the repository root, PowerShell:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File iam/init.ps1
.\scripts\bank.ps1 doctor
.\scripts\bank.ps1 build
.\scripts\bank.ps1 start
.\scripts\bank.ps1 status
.\scripts\bank.ps1 deps
```

Bash:

```bash
sh iam/init.sh
./scripts/bank.sh doctor
./scripts/bank.sh build
./scripts/bank.sh start
./scripts/bank.sh status
./scripts/bank.sh deps
```

On Windows with restricted script execution, prefix any PowerShell example with `powershell -NoProfile -ExecutionPolicy Bypass -File`. On Unix, ensure the Bash script is executable with `chmod +x scripts/bank.sh`; `bash scripts/bank.sh ...` also works. The scripts resolve paths from their own location, so invocation by absolute path works from another directory and paths containing spaces are supported.

Initialization generates private credentials in ignored `.env`, preserves existing values, and does not execute environment-file contents. [.env.example](.env.example) documents the required names. Do not overwrite existing keys or passwords belonging to initialized volumes. [IAM setup](docs/keycloak-local-setup.md) explains synthetic accounts, staff authenticator enrollment/recovery, registration, verification email and password reset. Keep `.iam` authenticator state private.

Newly initialized environments use the `placeholder-bank` Compose slug. Existing `.env` files and the original `finance-loan-origination` fallback retain their project and volume names. An explicit `-ProjectName` / `--project-name` selects an isolated environment; reuse that option on every command. Changing a project name deliberately selects different volumes. The separate ledger-only stack keeps its original `finance-ledger-core` name.

## Everyday commands

| Action | PowerShell | Bash |
|---|---|---|
| Prerequisites | `.\scripts\bank.ps1 doctor` | `./scripts/bank.sh doctor` |
| Build images (tests omitted) | `.\scripts\bank.ps1 build` | `./scripts/bank.sh build` |
| Start / wait for readiness | `.\scripts\bank.ps1 start` | `./scripts/bank.sh start` |
| Dependency container readiness | `.\scripts\bank.ps1 deps -Service loans` | `./scripts/bank.sh deps --service loans` |
| Status | `.\scripts\bank.ps1 status` | `./scripts/bank.sh status` |
| Recent logs | `.\scripts\bank.ps1 logs` | `./scripts/bank.sh logs` |
| Follow one service | `.\scripts\bank.ps1 logs -Service loans -Follow` | `./scripts/bank.sh logs --service loans --follow` |
| Restart / wait | `.\scripts\bank.ps1 restart` | `./scripts/bank.sh restart` |
| Stop, preserving data | `.\scripts\bank.ps1 stop` | `./scripts/bank.sh stop` |
| Regenerable output only | `.\scripts\bank.ps1 clean` | `./scripts/bank.sh clean` |

`deps` checks dependent **containers**, including PostgreSQL, Keycloak, Redis, object storage and ClamAV. It derives relationships and required health/completion conditions from the selected Compose configuration. It does not run Maven/npm vulnerability or version audits. `status` checks the expected stack and returns a failure when it is stopped, missing, starting, unhealthy or unverified.

Default stack is `iam`. Select `-Stack backend` / `--stack backend` for the existing backend-only demo, or `-Stack dev` / `--stack dev` for the same-origin Vite overlay. Start defaults to **no build**; use `start -Build` / `start --build` explicitly. Readiness timeout defaults to 600 seconds and can be changed. Build/test and the first image/browser/dependency installation may download pinned dependencies. No command updates lockfiles or versions automatically.

**Stop and restart preserve databases, Keycloak state, documents, keys and every Docker volume.** Redis is intentionally ephemeral in this local stack, so restarting Redis ends browser sessions. There is no database reset, `down -v`, global prune or broad Git cleanup command.

See the [command reference](docs/developer-commands.md) for options, readiness semantics, exit codes and safe cleanup. Shared VS Code tasks invoke the appropriate entry point for each platform.

## Local URLs

| Service | IAM stack URL |
|---|---|
| Applicant / staff portal | http://localhost:18082/ |
| Verification / reset mail | http://localhost:18084/ |
| Authorized Keycloak administration | http://localhost:18085/auth/admin/ |

The canonical issuer remains `http://localhost:18082/auth/realms/loans`; exact OIDC callbacks/logout URLs are unchanged. Financial backends, databases, Redis and storage remain private in the IAM stack. The backend-only stack exposes loan/ledger APIs on loopback ports 18081/18080, configurable through the existing environment variables. Runtime endpoint information is discovered by `status`.

## Build and test

```powershell
.\scripts\bank.ps1 doctor -Workflow native
.\scripts\bank.ps1 build -Workflow native
.\scripts\bank.ps1 test -Suite unit
.\scripts\bank.ps1 test -Suite integration
.\scripts\bank.ps1 test -Suite frontend
.\scripts\bank.ps1 test -Suite e2e
.\scripts\bank.ps1 test -Suite all
```

```bash
./scripts/bank.sh doctor --workflow native
./scripts/bank.sh build --workflow native
./scripts/bank.sh test --suite unit
./scripts/bank.sh test --suite integration
./scripts/bank.sh test --suite frontend
./scripts/bank.sh test --suite e2e
./scripts/bank.sh test --suite all
```

Native builds use the root Maven reactor and `npm ci`, with tests explicitly omitted. Unit tests use root `mvnw test`; integration selection uses root `mvnw verify`, including Surefire, Failsafe PostgreSQL/Redis tests, private storage and the sibling packaged ledger JAR. Frontend selection runs locked installation, type check, lint, component tests and build. `all` runs verify, frontend checks and the real-Keycloak browser journey. A missing prerequisite fails the selection; unexecuted suites are not described as passing.

[Latest lifecycle/branding verification](docs/developer-verification.md) and [web/IAM verification](docs/ui-verification.md) record executed commands and limits. The existing [loan demo](loan-service/README.md) and [ledger-only demo](ledger-core/README.md) remain available; their direct Compose commands use different profiles and should not be mixed into a running IAM environment.

## Troubleshooting and design references

Run `doctor`, then `deps` or `status`, then `logs -Service NAME` / `logs --service NAME`. A daemon failure, configuration error, occupied external port and unhealthy service are reported separately. Startup failures include the failing service state and a targeted logs command. Missing images need `build`; `start` does not silently rebuild them. Keep existing project options and private environment files when retrying; never delete volumes to repair startup.

[Architecture](docs/ui-architecture.md) · [Authentication/sessions](docs/authentication-and-sessions.md) · [Permissions](docs/roles-and-permissions.md) · [Applicant journey](docs/applicant-journey.md) · [Staff review](docs/staff-review-workflow.md) · [Frontend security](docs/frontend-security.md) · [Deployment/scaling](docs/deployment-and-scaling.md) · [Ledger accounting](docs/loan-accounting.md)

This local foundation uses real IAM and scanning with explicit synthetic financial providers. Production TLS/mail, real KYC/bureau/AI providers, operational security, backups and legal review remain separate requirements. BankID, agreement signing, funding and repayment workflows are not implemented.
