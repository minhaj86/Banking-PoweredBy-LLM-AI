# Placeholder Bank - Deployment, development and scaling

The normal local deployment uses `compose.yaml` plus `compose.iam.yaml`. Nginx serves static frontend assets under the same origin as the BFF and Keycloak paths. Two BFF replicas share encrypted Spring Session data, the authorized-client store, refresh locks and the provider-session registry in Redis. The local setup intentionally exercises requests across replicas without sticky sessions.

Runtime databases remain separate PostgreSQL instances. Redis is never a financial data store. Account provisioning is a durable loan-service job, while ledger posting correctness remains inside ledger-core transactions and locks. Neither UI polling nor AI execution runs in a ledger posting transaction.

## Local verification

PowerShell from the root, with JDK 21 on `JAVA_HOME` and Docker running:

```powershell
.\mvnw.cmd verify
Push-Location loan-web
npm ci
npm run typecheck
npm run lint
npm test
npm run build
npx playwright install chromium
npm run test:e2e
npm run test:load
Pop-Location
docker compose -f compose.yaml -f compose.iam.yaml exec -T loans curl -fsS http://localhost:8081/v3/api-docs | Set-Content -Encoding UTF8 .iam/loan-openapi.json
Push-Location loan-web
npm run test:contract
Pop-Location
```

Unix equivalents:

```sh
sh ./mvnw verify
cd loan-web
npm ci
npm run typecheck && npm run lint && npm test && npm run build
npx playwright install chromium
npm run test:e2e
npm run test:load
cd ..
docker compose -f compose.yaml -f compose.iam.yaml exec -T loans curl -fsS http://localhost:8081/v3/api-docs > .iam/loan-openapi.json
(cd loan-web && npm run test:contract)
```

The E2E suite uses real Keycloak registration/email, native OTP, two BFF replicas, real database/storage/ClamAV processing and the real ledger. Synthetic financial providers remain explicitly local. Its ledger assertion runs outside the browser with the least-privileged `loan-ledger` machine identity and checks zero debits/credits, no account entries and the same immutable account reference. It also proves that this identity receives 403 for journal posting. Generated test identities/data and volumes are preserved.

## Same-origin Vite development

Run the usual IAM setup first, then add the development overlay:

```sh
docker compose -f compose.yaml -f compose.iam.yaml -f compose.dev.yaml up -d --wait web-dev web
```

The same command works in PowerShell. Continue opening `http://localhost:18082`, including for login and callbacks. Vite/HMR is private to the Compose network and uses a separate Linux `node_modules` volume. Its health check waits for installation and Vite startup. The development SPA location permits Vite's inline React refresh preamble and a same-origin WebSocket in its development CSP. API routes keep their normal policy. The static image does not include this script exception.

Return to the normal static deployment without deleting any volume:

```sh
docker compose -f compose.yaml -f compose.iam.yaml -f compose.dev.yaml stop web-dev
docker compose -f compose.yaml -f compose.iam.yaml up -d --wait web
```

The read-only load script uses real cookie sessions and exercises `/bff/me`, the applicant queue and product reads. It records latency percentiles, errors, Redis memory/commands/hits/misses and before/after downstream job pressure in ignored `.iam/bff-load.json`. It excludes AI, document-processing and ledger-write throughput. See [UI verification](ui-verification.md) for executed commands/results; a reproducible test is not a throughput claim until measured.

## Capacity and failure behavior

Lists paginate in the backend; queue summaries batch loan lookup. Queries cancel obsolete requests. Polling backs off, pauses in hidden tabs and stops after ten minutes until manual refresh. Uploads run one at a time in each form and remain bounded to the backend's configured size. Each BFF accepts at most two concurrent uploads and streams multipart bodies/content without retaining a document-sized byte array. Its Tomcat pool has 80 threads, 200 connections and a 40-request accept queue. Financial and decision mutations await backend results; there is no financial optimistic cache.

Redis uses `noeviction`; full/unavailable authentication infrastructure fails closed. Local Redis is ephemeral, so restarting it logs users out. Production can use appropriately protected persistence/replication, with monitored latency, shared key rotation and bounded resource pools. Distributed refresh must remain bounded by token-service timeouts and a shared lock lease; avoid running independent uncoordinated client stores per replica.

The IAM overlay sets per-JVM container memory bounds. On constrained machines, run build and integration tests sequentially and set `COMPOSE_PARALLEL_LIMIT=1`. Preserve all named database/storage/key volumes if Docker is restarted. Never use `down -v` as routine recovery.

Production deployment requires immutable image promotion, TLS, managed secrets, production Keycloak/PostgreSQL/Redis operations, backups, network policies, runtime/migration privilege separation, mail delivery, provider availability and approved handling of financial data. This repository demo does not establish legal or regulatory certification.
