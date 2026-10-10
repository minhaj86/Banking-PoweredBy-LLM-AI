import { test, expect } from "@playwright/test";
import { env, login, admin, origin } from "./helpers";
test.describe("OIDC standard callback validation", () => {
  for (const parameter of ["state", "nonce", "code_challenge"])
    test(`rejects altered ${parameter} without establishing a session`, async ({
      page,
    }) => {
      await page.goto("/bff/login?returnTo=/app");
      const target = new URL(page.url());
      target.searchParams.set(
        parameter,
        parameter === "code_challenge" ? "A".repeat(43) : "tampered-value",
      );
      await page.goto(target.toString());
      await page.getByLabel("Username or email").fill("applicant");
      await page
        .getByRole("textbox", { name: "Password", exact: true })
        .fill(env.IAM_APPLICANT_PASSWORD);
      await page.getByRole("button", { name: "Sign In", exact: true }).click();
      await expect(page).toHaveURL(origin + "/?authentication=failed");
      expect((await page.request.get("/bff/me")).status()).toBe(401);
    });
});
test("provider initiated logout invalidates the shared BFF session", async ({
  browser,
}) => {
  const contexts = await Promise.all([
    browser.newContext({ baseURL: origin }),
    browser.newContext({ baseURL: origin }),
  ]);
  try {
    for (const context of contexts) {
      const page = await context.newPage();
      await login(page, "applicant", env.IAM_APPLICANT_PASSWORD);
      await page.close();
    }
    const users = (await admin("/users?username=applicant&exact=true")) as {
      id: string;
    }[];
    expect(
      ((await admin(`/users/${users[0].id}/sessions`)) as unknown[]).length,
    ).toBeGreaterThanOrEqual(2);
    await admin(`/users/${users[0].id}/logout`, "POST");
    for (const context of contexts)
      await expect
        .poll(async () => (await context.request.get("/bff/me")).status(), {
          timeout: 10000,
        })
        .toBe(401);
  } finally {
    await Promise.all(contexts.map((context) => context.close()));
  }
});
test("lower-assurance staff session cannot use privileged routes", async ({
  page,
}) => {
  await login(page, "officer", env.IAM_OFFICER_PASSWORD, false);
  const me = (await (await page.request.get("/bff/me")).json()) as {
    staffStepUpRequired: boolean;
    capabilities: string[];
  };
  expect(me.staffStepUpRequired).toBe(true);
  expect(me.capabilities).not.toContain("REVIEW");
  expect((await page.request.get("/bff/api/staff/summary")).status()).toBe(403);
  expect(
    (await page.request.get("/bff/api/loan-applications?view=staff")).status(),
  ).toBe(403);
  await login(page, "officer", env.IAM_OFFICER_PASSWORD, true);
  expect(
    (
      (await (await page.request.get("/bff/me")).json()) as {
        capabilities: string[];
      }
    ).capabilities,
  ).toContain("REVIEW");
});
test("role change becomes effective on concurrent refresh across both instances", async ({
  browser,
}) => {
  test.setTimeout(120000);
  const context = await browser.newContext({ baseURL: origin });
  const page = await context.newPage();
  await login(page, "officer", env.IAM_OFFICER_PASSWORD, true);
  await page.close();
  const users = (await admin("/users?username=officer&exact=true")) as {
    id: string;
  }[];
  const groups = (await admin(`/users/${users[0].id}/groups`)) as {
    id: string;
    name: string;
  }[];
  const group = groups.find((g) => g.name === "loan-officer")!;
  expect(group).toBeTruthy();
  try {
    await admin(`/users/${users[0].id}/groups/${group.id}`, "DELETE");
    const before = (await (await context.request.get("/bff/me")).json()) as {
      capabilities: string[];
    };
    expect(before.capabilities).toContain("REVIEW");
    const start = Date.now();
    await new Promise((resolve) => setTimeout(resolve, 47000));
    const responses = await Promise.all(
      Array.from({ length: 12 }, () => context.request.get("/bff/me")),
    );
    for (const response of responses) {
      expect(response.status()).toBe(200);
      expect(
        ((await response.json()) as { capabilities: string[] }).capabilities,
      ).not.toContain("REVIEW");
    }
    expect(Date.now() - start).toBeLessThan(65000);
    expect((await context.request.get("/bff/api/staff/summary")).status()).toBe(
      403,
    );
  } finally {
    await admin(`/users/${users[0].id}/groups/${group.id}`, "PUT");
    await context.close();
  }
});
