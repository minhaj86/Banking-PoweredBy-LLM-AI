import { test, expect } from "@playwright/test";
import { mailLink, syntheticUser, origin, login, env } from "./helpers";
test("Keycloak registration, provider logout, forgotten password and reset email", async ({
  page,
  request,
}) => {
  const user = syntheticUser();
  await page.goto("/bff/login?returnTo=/app&register=true");
  for (const [name, value] of [
    ["Username", user.username],
    ["Email", user.email],
    ["First name", "Synthetic"],
    ["Last name", "Recovery"],
    ["Password", user.password],
    ["Confirm password", user.password],
  ])
    await page.getByRole("textbox", { name, exact: true }).fill(value);
  await page.getByRole("button", { name: "Register", exact: true }).click();
  await expect(
    page.getByRole("heading", { name: "Email verification", exact: false }),
  ).toBeVisible();
  await page.goto(await mailLink(request, user.email));
  await expect(page).toHaveURL(origin + "/app");
  await page.getByRole("button", { name: "Sign out", exact: true }).click();
  await expect(page.getByRole("button", { name: /^Log ?out$/i })).toBeVisible();
  await page.getByRole("button", { name: /^Log ?out$/i }).click();
  await expect(page).toHaveURL(origin + "/");
  expect((await page.request.get("/bff/me")).status()).toBe(401);
  await page.goto("/bff/login?returnTo=/app");
  await page
    .getByRole("link", { name: "Forgot Password?", exact: true })
    .click();
  await page
    .getByRole("textbox", { name: "Username or email", exact: true })
    .fill(user.username);
  await page.getByRole("button", { name: "Submit", exact: true }).click();
  await page.goto(await mailLink(request, user.email, "reset"));
  const newPassword = user.password + "Reset9";
  await page
    .getByRole("textbox", { name: "New Password", exact: true })
    .fill(newPassword);
  await page
    .getByRole("textbox", { name: "Confirm password", exact: true })
    .fill(newPassword);
  await page.getByRole("button", { name: "Submit", exact: true }).click();
  await expect(page).toHaveURL(origin + "/app");
  await page.getByRole("button", { name: "Sign out", exact: true }).click();
  await page.getByRole("button", { name: /^Log ?out$/i }).click();
  await expect(page).toHaveURL(origin + "/");
  await login(page, user.username, newPassword);
  expect((await page.request.get("/bff/me")).status()).toBe(200);
});
test("invalid password stays on the provider and cannot establish a BFF session", async ({
  page,
}) => {
  await page.goto("/bff/login?returnTo=/app");
  await page.getByLabel("Username or email").fill("applicant");
  await page
    .getByRole("textbox", { name: "Password", exact: true })
    .fill(env.IAM_APPLICANT_PASSWORD + "incorrect");
  await page.getByRole("button", { name: "Sign In", exact: true }).click();
  await expect(
    page.getByText("Invalid username or password.", { exact: true }),
  ).toBeVisible();
  expect((await page.request.get("/bff/me")).status()).toBe(401);
});
