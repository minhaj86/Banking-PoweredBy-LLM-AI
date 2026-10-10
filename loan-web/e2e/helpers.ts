import { expect } from "@playwright/test";
import type { Page, APIRequestContext } from "@playwright/test";
import { readFileSync, existsSync, mkdirSync, writeFileSync } from "node:fs";
import { resolve } from "node:path";
import { createHmac, randomBytes } from "node:crypto";
export const origin = process.env.PORTAL_URL ?? "http://localhost:18082";
export const env = Object.fromEntries(
  readFileSync(resolve("../.env"), "utf8")
    .split(/\r?\n/)
    .filter((line) => line && !line.startsWith("#"))
    .map((line) => {
      const i = line.indexOf("=");
      return [line.slice(0, i), line.slice(i + 1)];
    }),
);
export function totp(secret: string) {
  const alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
  let bits = "";
  for (const char of secret.toUpperCase().replaceAll(" ", "")) {
    bits += alphabet.indexOf(char).toString(2).padStart(5, "0");
  }
  const bytes = Buffer.from(
    (bits.match(/.{8}/g) ?? []).map((b) => parseInt(b, 2)),
  );
  const counter = Buffer.alloc(8);
  counter.writeBigUInt64BE(BigInt(Math.floor(Date.now() / 30000)));
  const hmac = createHmac("sha1", bytes).update(counter).digest();
  const offset = hmac[hmac.length - 1] & 15;
  return String((hmac.readUInt32BE(offset) & 0x7fffffff) % 1000000).padStart(
    6,
    "0",
  );
}
const otpPath = resolve("../.iam/otp-state.json");
async function freshTotp(username: string, secret: string) {
  const path = resolve("../.iam/otp-counters.json");
  const counters: Record<string, number> = existsSync(path)
    ? (JSON.parse(readFileSync(path, "utf8")) as Record<string, number>)
    : {};
  if (counters[username] === Math.floor(Date.now() / 30000))
    await new Promise((resolve) =>
      setTimeout(resolve, 31000 - (Date.now() % 30000)),
    );
  counters[username] = Math.floor(Date.now() / 30000);
  writeFileSync(path, JSON.stringify(counters), { mode: 0o600 });
  return totp(secret);
}
export async function login(
  page: Page,
  username: string,
  password: string,
  staff = false,
  returnTo = staff ? "/staff" : "/app",
) {
  await page.goto(
    `/bff/login?returnTo=${encodeURIComponent(returnTo)}&staff=${staff}`,
  );
  if (await page.getByLabel("Username or email").isVisible()) {
    await page.getByLabel("Username or email").fill(username);
    await page
      .getByRole("textbox", { name: "Password", exact: true })
      .fill(password);
    await page.getByRole("button", { name: "Sign In", exact: true }).click();
  }
  await Promise.any([
    page
      .getByRole("heading", { name: "Mobile Authenticator Setup" })
      .waitFor({ timeout: 15000 }),
    page.locator('input[name="otp"]').waitFor({ timeout: 15000 }),
    page.waitForURL(origin + returnTo, { timeout: 15000 }),
  ]);
  if (
    await page
      .getByRole("heading", { name: "Mobile Authenticator Setup" })
      .isVisible()
      .catch(() => false)
  ) {
    await page
      .getByRole("link", { name: "Unable to scan?", exact: true })
      .click();
    const secret = (
      await page.locator("#kc-totp-secret-key").innerText()
    ).replaceAll(" ", "");
    const states: Record<string, string> = existsSync(otpPath)
      ? (JSON.parse(readFileSync(otpPath, "utf8")) as Record<string, string>)
      : {};
    states[username] = secret;
    mkdirSync(resolve("../.iam"), { recursive: true });
    writeFileSync(otpPath, JSON.stringify(states), { mode: 0o600 });
    await page
      .locator('input[name="totp"]')
      .fill(await freshTotp(username, secret));
    const device = page.locator('input[name="userLabel"]');
    if (await device.isVisible()) await device.fill("Local test authenticator");
    await page.getByRole("button", { name: "Submit", exact: true }).click();
  } else if (
    await page
      .locator('input[name="otp"]')
      .isVisible()
      .catch(() => false)
  ) {
    const states = JSON.parse(readFileSync(otpPath, "utf8")) as Record<
      string,
      string
    >;
    await page
      .locator('input[name="otp"]')
      .fill(await freshTotp(username, states[username]));
    await page.getByRole("button", { name: "Sign In", exact: true }).click();
  }
  await expect(page).toHaveURL(origin + returnTo);
}
export async function admin(path: string, method = "GET", body?: unknown) {
  const response = await fetch(
    "http://localhost:18085/auth/realms/master/protocol/openid-connect/token",
    {
      method: "POST",
      body: new URLSearchParams({
        grant_type: "password",
        client_id: "admin-cli",
        username: "local-admin",
        password: env.IAM_ADMIN_PASSWORD,
      }),
    },
  );
  const token = (await response.json()) as { access_token: string };
  if (!token.access_token) throw new Error("IAM admin fixture unavailable");
  const result = await fetch(
    "http://localhost:18085/auth/admin/realms/loans" + path,
    {
      method,
      headers: {
        Authorization: "Bearer " + token.access_token,
        "Content-Type": "application/json",
      },
      body: body === undefined ? undefined : JSON.stringify(body),
    },
  );
  if (!result.ok) throw new Error(`IAM fixture failed (${result.status})`);
  return result.status === 204 ? undefined : ((await result.json()) as unknown);
}
export async function mailLink(
  request: APIRequestContext,
  email: string,
  kind: "verify" | "reset" = "verify",
) {
  let id = "";
  await expect
    .poll(
      async () => {
        const response = await request.get(
          "http://localhost:18084/api/v1/search",
          { params: { query: `to:${email}` } },
        );
        const data = (await response.json()) as {
          messages: { ID: string; Subject: string }[];
        };
        const message = data.messages?.find((m) =>
          kind === "verify"
            ? /verif/i.test(m.Subject)
            : /reset/i.test(m.Subject),
        );
        id = message?.ID ?? "";
        return Boolean(id);
      },
      { timeout: 30000 },
    )
    .toBe(true);
  const data = (await (
    await request.get(`http://localhost:18084/api/v1/message/${id}`)
  ).json()) as { HTML: string; Text: string };
  const url = (data.HTML.match(
    /href="(http:\/\/localhost:18082\/auth\/[^"]+)"/,
  ) ?? data.Text.match(/(http:\/\/localhost:18082\/auth\/\S+)/))?.[1];
  if (!url) throw new Error("Expected Keycloak email action link");
  return url.replaceAll("&amp;", "&");
}
export function syntheticUser() {
  const suffix = Date.now().toString(36) + randomBytes(3).toString("hex");
  return {
    username: "applicant-" + suffix,
    email: `applicant-${suffix}@example.test`,
    password: "Synthetic9!" + randomBytes(18).toString("base64url"),
  };
}
export async function browserHasNoTokens(page: Page) {
  expect(
    await page.evaluate(() => ({
      local: Object.keys(localStorage),
      session: Object.keys(sessionStorage),
      cookies: document.cookie,
    })),
  ).toEqual({
    local: [],
    session: [],
    cookies: expect.not.stringMatching(
      /LOAN_SESSION|access_token|refresh_token|id_token/,
    ),
  });
  const response = await page.request.get("/bff/me");
  const text = await response.text();
  expect(text).not.toMatch(
    /access_token|refresh_token|id_token|eyJ[a-zA-Z0-9_-]{20}/,
  );
}
export async function csrfHeaders(request: APIRequestContext) {
  const response = await request.get("/bff/csrf");
  const data = (await response.json()) as { headerName: string; token: string };
  return { [data.headerName]: data.token };
}
