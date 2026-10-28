# Placeholder Bank - Branding and lifecycle verification

Verification date: 28 October 2026, Windows PowerShell 5.1 and Git Bash, Java 21, Node 24 and Docker Desktop. Source changes are implemented. Final image rollout and browser verification are blocked by the host disk/storage failure described below.

## Implemented changes

- Central frontend `APP_NAME`, slug, title and description in [brand.ts](../loan-web/src/app/brand.ts), used by header/footer/staff layout and Vite-generated HTML.
- Placeholder Bank README/documentation, Maven/npm descriptions, OpenAPI titles/descriptions and additive Keycloak realm/client/email display-name updates. Technical packages, Maven artifact coordinates, database/migration identities, realm/client IDs, issuer/callback URLs, API paths and idempotency references remain unchanged.
- Existing Compose project/volume identity preserved. New environment initialization writes `COMPOSE_PROJECT_NAME=placeholder-bank`; existing `.env` is preserved.
- Matching [PowerShell](../scripts/bank.ps1) / [Bash](../scripts/bank.sh) help, doctor, start, stop, restart, build, test, deps, status, logs and clean entry points; [command reference](developer-commands.md), shared tasks, editor settings, Git/Docker exclusions and sanitized configuration.
- `deps` checks **container dependencies**, including PostgreSQL, Keycloak, Redis, storage and scanner readiness. No package vulnerability/version audit was added.

## Executed checks

| Check | Actual result |
|---|---|
| `bank.ps1 test -Suite integration` | Root Maven verify passed: **144 tests**, 0 failures, 0 errors, 0 skipped; 1m59s |
| Java module totals | ledger-core 86; loan-service 44; loan-bff 14, from final XML reports |
| `bank.sh test --suite frontend` | Locked npm installation, strict typecheck, ESLint, **22 component tests** and production build passed |
| `scripts/tests/lifecycle-smoke.ps1` | **54 assertions passed**, exercising both real entry points from outside a fixture root containing spaces |
| PowerShell parser / `bash -n` | All new PowerShell scripts and Bash/shared probe syntax passed |
| Native doctor | Both entry points recognized JDK 21, Wrapper and Node 24/npm |
| Runtime dependency checks | Both entry points checked the actual loan-service transitive dependencies successfully before the later storage fault |
| Ignore verification | 25 actual source, config, Wrapper, migration and synthetic-fixture examples remained visible; `.env`, `.iam`, dependencies, output and private-data examples were ignored |
| Lockfile | Unchanged byte-for-byte |
| Git whitespace / review | `git diff --check` and original-file baseline whitespace comparisons passed; source changes reviewed without staging |
| Full application image build | **Failed**, exit 1: Docker BuildKit storage became read-only after C: filled |
| Updated live branding / real-Keycloak browser suite | **Not completed for this change**, pending disk space and Docker recovery; previous UI verification is historical, not a new pass |

The lifecycle fixture uses an explicitly separate project and PostgreSQL/Redis/HTTP services, with no financial data. It proved repeated-start container identity preservation, real failing/recovering HTTP health, unavailable Docker, occupied ports, missing configuration, unknown-service rejection, native prerequisite failures, Docker workflows without a valid host JDK, build/status failure exits, bounded logs, selected-service restart and complete-stack stop/start. A PostgreSQL row and private environment-file hash survived restart/stop/start. Both cleaners removed only allowlisted output and refused a junction outside the fixture root. Fixture containers were stopped; their private configuration and test volumes remain preserved.

The pre-existing application container IDs and volume mappings were checked against a snapshot and remained unchanged during these lifecycle tests. The production stack was not stopped/restarted for smoke testing.

## Commands

Executed from the repository root unless an absolute path was used to verify invocation from `C:/Windows/Temp`:

```powershell
git rev-parse --is-inside-work-tree
git init --initial-branch=main
git rev-parse --show-toplevel
git branch --show-current
git check-ignore -v -- .env .iam/otp-state.json loan-web/node_modules loan-web/dist loan-bff/target .reports/local.txt data/private.db secrets/private.pem
git diff --check

powershell -NoProfile -ExecutionPolicy Bypass -File scripts/tests/lifecycle-smoke.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/bank.ps1 doctor -Workflow native
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/bank.ps1 deps -Service loans
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/bank.ps1 test -Suite integration
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/bank.ps1 build
```

The installed Git Bash executable also ran these commands (using `bash scripts/bank.sh`):

```bash
bash -n scripts/bank.sh
bash -n scripts/container-health.sh
bash scripts/bank.sh doctor --workflow native
bash scripts/bank.sh deps --service loans
bash scripts/bank.sh test --suite frontend
chmod +x scripts/bank.sh
```

The local Bash file mode was verified as 755. No separate Linux/macOS host run or PSScriptAnalyzer installation was performed; the PowerShell parser and executable smoke suite were used. Initial parallel verification hit host memory pressure; final successful application/lifecycle runs were sequential, and the frontend worker pool is now bounded to two workers. The test-runner handling of successful native stderr output was corrected before its final successful run.

## Git and private files

No repository or parent working tree existed. Git was initialized at `C:/Users/rubai/OneDrive/code/finance-ledger-core` on `main`. There are no commits, staged paths or remotes. All source/configuration files are intentionally untracked until the user reviews and stages them. Existing source was not removed, and no global Git identity was configured.

There were **zero tracked files**, so there were no tracked generated files or secrets to untrack or rotate. Actual credentials remain in ignored `.env` and `.iam` authenticator state. `.gitignore` cannot remove future tracked secrets; any later such finding requires explicit review. No secret values were printed.

## Current storage block and recovery

The full Docker image build encountered `metadata_v2.db: read-only file system`. Diagnostics showed C: had zero free space, Linux disk I/O errors, and a failed write probe in an existing container. Docker's API and cached health state can still show running containers; that does not establish writable storage.

Automatic approval review rejected both proposed cleanup actions: deleting the two downloaded setup archives, and deleting regenerable JAR outputs beneath the repository. Both were reported as blocked by policy. **Neither cleanup occurred.** No application data, volume, environment file, key, Git history or installed tool was deleted. Docker has not been restarted while awaiting the user's recovery choice.

The remaining work needs at least 4 GB freed on C: and approval for a brief Docker Desktop recovery restart. After storage is writable, build the images, apply the display-name migration, update the frontend, rerun runtime status/deps and the real-Keycloak browser suite, and record those outcomes here. The existing browser page can retain the previous branding until that image update is applied. Do not delete volumes or run a global prune to recover.
