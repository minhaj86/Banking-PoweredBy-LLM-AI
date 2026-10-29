# Placeholder Bank - Developer commands

[PowerShell](../scripts/bank.ps1) and [Bash](../scripts/bank.sh) expose the same commands. Both derive the repository root from their own file, quote paths, validate service names against Compose, and propagate failures. Neither evaluates `.env`, stages files nor changes dependency versions. `help` and `clean` work without Docker or native build tools.

For a shorter Docker-development command, [run-dev.sh](../scripts/run-dev.sh) forwards the lifecycle commands to `bank.sh`. It defaults to `start`, and supports `start`, `stop`, `restart`, `status`, `deps`, `logs`, `build`, and `doctor` with the same options and safety guarantees.

## Commands

| Command | Behavior / required tools |
|---|---|
| `help` | Commands, options, examples, side effects and download behavior |
| `doctor` | Git, Docker CLI/daemon/Compose, selected configuration, missing variable names and published-port conflicts; `Workflow native` checks JDK 21, shared Wrapper, Node >=24 and npm |
| `start` | Existing Compose `up -d --no-build --wait`; explicit build option adds `--build`; prints actual readiness/endpoints |
| `stop` | Compose `stop --timeout 30`; preserves containers and every volume |
| `restart` | Restart selected services, then wait with `up --no-recreate --no-build --wait`; no clean rebuild |
| `build` | Docker: build the configured images. Native: root Wrapper `-DskipTests package`, frontend `npm ci` then build. **Tests omitted** |
| `test` | Host tools selected by suite; see below. No skipped-suite success claim |
| `deps` | Read-only runtime dependency graph and container readiness, including PostgreSQL, Keycloak, Redis, private storage and scanner. A selected service includes itself and its transitive prerequisites |
| `status` | Expected service table with state, readiness, mapped endpoint and brief failure; exits nonzero unless ready |
| `logs` | Known services only; bounded recent lines, optional following; never dumps expanded Compose/environment configuration |
| `clean` | Remove only [clean-paths.txt](../scripts/clean-paths.txt), after root/link checks; no Docker operation |

`deps` concerns container dependencies. Maven/npm resolution, outdated-package reports and vulnerability audits are not performed by this command.

## Options and examples

| PowerShell | Bash | Meaning |
|---|---|---|
| `-Stack iam` | `--stack iam` | Default: root Compose plus IAM overlay |
| `-Stack backend` | `--stack backend` | Existing backend-only root Compose |
| `-Stack dev` | `--stack dev` | IAM plus private Vite overlay; browser still uses canonical port 18082 |
| `-Workflow native` | `--workflow native` | Native `doctor`/`build`; Docker is default |
| `-Service loans,web` | `--service loans --service web` | Lifecycle/status/log targets from the selected configuration |
| `-ProjectName NAME` | `--project-name NAME` | Explicit isolated Compose project; default is existing `.env`/Compose identity |
| `-EnvFile PATH` | `--env-file PATH` | Environment file relative to repository root, or absolute path |
| `-Timeout 900` | `--timeout 900` | Readiness timeout, 1..3600 seconds; default 600 |
| `start -Build` | `start --build` | Explicit image build before starting |
| `-Tail 50 -Follow` | `--tail 50 --follow` | Logs: 1..10000 lines (default 100), optionally follow |
| `-Suite all` | `--suite all` | Test selection (default `unit`) |

Build always builds all configured modules/images; service selection is for lifecycle/status/deps/logs. Compose environment profiles are respected; the optional ledger-proof verification profile is not enabled by default. Keep stack/project/environment options consistent between invocations. No service names or host ports are copied into the orchestrators: names, dependency conditions and configured ports come from Compose, and published endpoints come from Docker inspection.

```powershell
.\scripts\bank.ps1 doctor
.\scripts\bank.ps1 build
.\scripts\bank.ps1 start -Timeout 900
.\scripts\bank.ps1 deps -Service loans
.\scripts\bank.ps1 status
.\scripts\bank.ps1 logs -Service keycloak -Tail 50 -Follow
.\scripts\bank.ps1 restart -Service bff-a,bff-b
.\scripts\bank.ps1 stop
```

```bash
./scripts/bank.sh doctor
./scripts/bank.sh build
./scripts/bank.sh start --timeout 900
./scripts/bank.sh deps --service loans
./scripts/bank.sh status
./scripts/bank.sh logs --service keycloak --tail 50 --follow
./scripts/bank.sh restart --service bff-a --service bff-b
./scripts/bank.sh stop
```

The equivalent short entry point is:

```bash
./scripts/run-dev.sh start
./scripts/run-dev.sh status
./scripts/run-dev.sh deps --service loans
./scripts/run-dev.sh stop
```

These examples assume setup is complete. Initialization is `powershell -NoProfile -ExecutionPolicy Bypass -File iam/init.ps1` or `sh iam/init.sh` (OpenSSL). PowerShell examples also accept that process-only execution-policy prefix. Bash examples require `chmod +x scripts/bank.sh`, or invoke `bash scripts/bank.sh` explicitly. No global execution policy or Git identity is changed.

## Readiness and dependency behavior

The scripts resolve expected services with `compose config --services`, honoring the selected files and active profiles. PowerShell reads Compose's JSON model; Bash reads the normalized YAML's dependency/port sections. Expanded secret values remain internal and are never printed or saved. Missing required variables are reported by name; configuration/parser failures return 2 without starting anything.

A service is ready when its Docker health check passes. One-shot initialization referenced by a `service_completed_successfully` condition is ready only after exit 0. Merely running without a health check is **unverified**, not healthy. Storage and mail use the shared [container probe](../scripts/container-health.sh); older running containers can execute that same read-only probe through stdin until a later normal start applies the new health-check mount. Storage's master endpoint is checked live; its completed bootstrap additionally proves successful authenticated bucket setup. Database, Keycloak, Redis, scanner, application and gateway checks use their existing image/Compose health checks.

`status` distinguishes missing, stopped, starting, unhealthy, completed and unverified services. Docker-unavailable/configuration failures are explicit and return nonzero. `deps` prints the actual `service_healthy` / `service_completed_successfully` relationships, then the dependency health table. A stopped database or failing scanner therefore fails the dependency check even when the top-level container still exists.

Start uses Compose's readiness deadline, then checks selected services again. Failures show the service state and a targeted logs command. Restart uses the same readiness checks. No fixed startup sleep substitutes for health. Docker-only commands require no host Java, global Maven or Node.

## Tests and downloads

| Suite | Executes | Host prerequisites |
|---|---|---|
| `unit` | Root Maven Wrapper `test` (Surefire) | Git, JDK 21 |
| `integration` | Root `verify`, including units/Failsafe and sibling ledger packaging | Git, JDK 21, Docker |
| `frontend` | `npm ci`, typecheck, lint, Vitest, production build | Git, Node >=24, npm |
| `e2e` | `npm ci`, pinned Playwright Chromium installation, real-Keycloak Playwright tests | Git, Node/npm, Docker, ready IAM/dev stack, supported browser OS libraries |
| `all` | Root verify, frontend checks, real-authentication browser suite | All above |

`test` runs these host-based suites; it does not pretend that integration/browser tests ran inside an image build. Root `verify` is essential for the real-ledger test. Native build/test may download Maven, locked npm packages and Chromium. Image builds/first starts may download pinned images. `doctor`, `status`, `deps`, `logs`, `stop` and `clean` do not install tools or upgrade anything. `start` performs no silent build and `restart` does not rebuild. Missing prerequisites fail with actionable messages; selecting only unit/frontend tests leaves other suites unexecuted.

## Data, cleanup and Git

Default IAM/backend/dev environments reuse the current `.env`/Compose identity. New initialization writes `COMPOSE_PROJECT_NAME=placeholder-bank`; existing environment files retain the original project and volume ownership. Do not change a live project's name to perform a rename. Ports remain bound to loopback and OIDC issuer/realm/client/callback values remain unchanged.

Stop/restart keep PostgreSQL, Keycloak, private documents and volumes. Redis is intentionally nonpersistent locally; restarting Redis invalidates browser sessions. Neither script invokes volume deletion, Docker prune, a database reset or `git clean`.

Cleanup paths are literal allowlisted generated module output and `.reports`, all beneath the derived root. Both scripts reject links/junctions in their cleanup paths. They preserve `.env`, `.iam`, source, Git metadata, installed dependencies, external user files and all runtime volumes. Reports placed in `.reports` must be regenerable outputs; put private runtime configuration in the existing `.env`/`.iam` locations instead.

The repository was initialized on `main` without a commit, staged files or remote. `.gitignore` does not untrack existing files; any future tracked-secret remediation needs separate review/rotation. Keep the shared VS Code JSON, package lock, Wrapper files, migrations and synthetic fixtures versionable. On filesystems without persistent Unix execute metadata, record the Bash executable bit when you deliberately stage it (`git add --chmod=+x scripts/bank.sh`); the setup does not stage files automatically.

Exit codes: **0** successful command; **1** expected stack/dependency not ready; **2** usage, missing prerequisites or configuration; failed Maven/npm/Docker subprocesses preserve their own nonzero exit codes. A failed child stops subsequent build/test steps. Log following may exit with the shell's interrupt status.

The isolated orchestration regression is `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/tests/lifecycle-smoke.ps1`. It exercises both entry points from outside a root containing spaces, using an explicitly separate PostgreSQL/Redis/HTTP fixture. It preserves its private `.iam` fixture config and test volumes, and stops only that fixture. See [executed verification](developer-verification.md).
