import { chromium } from "@playwright/test";
import { performance } from "node:perf_hooks";
import { spawnSync } from "node:child_process";
import { mkdirSync, writeFileSync } from "node:fs";
import { resolve } from "node:path";
import { env, login, origin } from "../e2e/helpers.ts";
const requests =
  Number(process.argv[process.argv.indexOf("--requests") + 1] ?? 300) || 300;
const concurrency =
  Number(process.argv[process.argv.indexOf("--concurrency") + 1] ?? 4) || 4;
if (
  !Number.isInteger(requests) ||
  requests < 1 ||
  requests > 10000 ||
  !Number.isInteger(concurrency) ||
  concurrency < 1 ||
  concurrency > 16
)
  throw new Error("Use 1..10000 requests and 1..16 sessions");
function redisStats() {
  const result = spawnSync(
    "docker",
    [
      "compose",
      "-f",
      "compose.yaml",
      "-f",
      "compose.iam.yaml",
      "exec",
      "-T",
      "redis",
      "sh",
      "-ec",
      'REDISCLI_AUTH="$REDIS_PASSWORD" redis-cli INFO',
    ],
    { cwd: resolve(".."), encoding: "utf8", timeout: 15000 },
  );
  if (result.status !== 0) throw new Error("Redis measurement unavailable");
  const values = Object.fromEntries(
    result.stdout
      .split(/\r?\n/)
      .filter((s) => s.includes(":"))
      .map((s) => s.split(":", 2)),
  );
  return Object.fromEntries(
    [
      "connected_clients",
      "used_memory",
      "total_commands_processed",
      "keyspace_hits",
      "keyspace_misses",
      "rejected_connections",
      "instantaneous_ops_per_sec",
    ].map((k) => [k, Number(values[k])]),
  );
}
const browser = await chromium.launch();
const sessions = [];
for (let i = 0; i < concurrency; i++) {
  const context = await browser.newContext({ baseURL: origin });
  const page = await context.newPage();
  await login(page, "applicant", env.IAM_APPLICANT_PASSWORD);
  await page.close();
  sessions.push(context);
}
const operations = await browser.newContext({ baseURL: origin });
const staff = await operations.newPage();
await login(staff, "operations", env.IAM_OPERATIONS_PASSWORD, true);
await staff.close();
const beforePressure = await (
  await operations.request.get("/bff/api/operations/metrics")
).json();
const beforeRedis = redisStats();
const latencies: number[] = [];
const errors: Record<string, number> = {};
let counter = 0;
const start = performance.now();
await Promise.all(
  sessions.map(async (context) => {
    for (;;) {
      const index = counter++;
      if (index >= requests) return;
      const path = [
        "/bff/me",
        "/bff/api/loan-applications?view=mine&page=0&size=10",
        "/bff/product",
      ][index % 3];
      const began = performance.now();
      try {
        const response = await context.request.get(path);
        await response.body();
        if (response.status() !== 200)
          errors[String(response.status())] =
            (errors[String(response.status())] ?? 0) + 1;
      } catch {
        errors.network = (errors.network ?? 0) + 1;
      }
      latencies.push(performance.now() - began);
    }
  }),
);
const elapsed = performance.now() - start;
const afterRedis = redisStats();
const afterPressure = await (
  await operations.request.get("/bff/api/operations/metrics")
).json();
latencies.sort((a, b) => a - b);
const percentile = (p: number) =>
  Number(
    latencies[
      Math.min(latencies.length - 1, Math.ceil(latencies.length * p) - 1)
    ].toFixed(2),
  );
const report = {
  recordedAt: new Date().toISOString(),
  scope:
    "Real Keycloak cookie sessions; read-only BFF me, applicant queue and product; excludes document, AI and ledger write throughput",
  requests,
  concurrency,
  elapsedMs: Number(elapsed.toFixed(2)),
  requestsPerSecond: Number((requests / (elapsed / 1000)).toFixed(2)),
  latencyMs: {
    p50: percentile(0.5),
    p95: percentile(0.95),
    p99: percentile(0.99),
    max: percentile(1),
  },
  errors,
  redis: {
    before: beforeRedis,
    after: afterRedis,
    commandsDuringRun:
      afterRedis.total_commands_processed -
      beforeRedis.total_commands_processed,
  },
  downstream: { before: beforePressure, after: afterPressure },
};
mkdirSync(resolve("../.iam"), { recursive: true });
writeFileSync(
  resolve("../.iam/bff-load.json"),
  JSON.stringify(report, null, 2),
);
console.log(JSON.stringify(report, null, 2));
await browser.close();
if (Object.keys(errors).length) process.exitCode = 1;
