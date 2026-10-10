import { test, expect } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";
import { env, login, browserHasNoTokens, csrfHeaders, origin } from "./helpers";
test("real confidential PKCE login, shared sessions, CSRF and logout", async ({
  page,
}) => {
  const noSession = await page.request.get("/bff/me");
  expect(noSession.status()).toBe(401);
  expect(noSession.headers()["content-type"]).toContain(
    "application/problem+json",
  );
  await page.goto("/bff/login?returnTo=/app");
  const auth = new URL(page.url());
  expect(auth.searchParams.get("code_challenge_method")).toBe("S256");
  expect(auth.searchParams.get("code_challenge")).toMatch(
    /^[A-Za-z0-9_-]{43}$/,
  );
  expect(auth.searchParams.get("state")).toBeTruthy();
  expect(auth.searchParams.get("nonce")).toBeTruthy();
  const beforeLogin = (await page.context().cookies()).find(
    (c) => c.name === "LOAN_SESSION",
  )?.value;
  await page.getByLabel("Username or email").fill("applicant");
  await page
    .getByRole("textbox", { name: "Password", exact: true })
    .fill(env.IAM_APPLICANT_PASSWORD);
  await page.getByRole("button", { name: "Sign In", exact: true }).click();
  await expect(page).toHaveURL(origin + "/app");
  const instances = new Set<string>();
  for (let i = 0; i < 8; i++) {
    const me = (await (await page.request.get("/bff/me")).json()) as {
      instance: string;
      roles: string[];
    };
    instances.add(me.instance);
    expect(me.roles).toContain("APPLICANT");
  }
  expect([...instances].sort()).toEqual(["bff-a", "bff-b"]);
  await browserHasNoTokens(page);
  const cookies = await page.context().cookies();
  const session = cookies.find((c) => c.name === "LOAN_SESSION");
  expect(session?.httpOnly).toBe(true);
  expect(session?.sameSite).toBe("Lax");
  expect(session?.value).not.toBe(beforeLogin);
  expect(
    (
      await page.request.post("/logout/connect/back-channel/keycloak", {
        form: { logout_token: "not-a-signed-token" },
      })
    ).status(),
  ).toBe(400);
  expect((await page.request.get("/bff/me")).status()).toBe(200);
  expect((await page.request.post("/bff/logout")).status()).toBe(403);
  expect((await page.request.get("/bff/me")).status()).toBe(200);
  expect(
    (
      await page.request.get("/bff/login?returnTo=https%3A%2F%2Fevil.example", {
        maxRedirects: 0,
      })
    ).status(),
  ).toBe(400);
  await expect(
    page.getByRole("heading", { name: "Welcome, applicant.", exact: true }),
  ).toBeVisible();
  const a11y = await new AxeBuilder({ page }).analyze();
  expect(a11y.violations).toEqual([]);
  const headers = await csrfHeaders(page.request);
  const logout = await page.request.post("/bff/logout", {
    headers,
    maxRedirects: 0,
  });
  expect(logout.status()).toBe(302);
  expect(logout.headers().location).not.toContain("id_token_hint");
  expect((await page.request.get("/bff/me")).status()).toBe(401);
});
test("staff native OTP step-up grants only the officer capability", async ({
  page,
}) => {
  await login(page, "officer", env.IAM_OFFICER_PASSWORD, true);
  const me = (await (await page.request.get("/bff/me")).json()) as {
    capabilities: string[];
    staffStepUpRequired: boolean;
  };
  expect(me.staffStepUpRequired).toBe(false);
  expect(me.capabilities).toContain("REVIEW");
  expect(me.capabilities).not.toContain("OPERATE");
  expect(me.capabilities).not.toContain("COMPLIANCE");
  await browserHasNoTokens(page);
  expect((await page.request.get("/bff/api/operations/jobs")).status()).toBe(
    403,
  );
});
