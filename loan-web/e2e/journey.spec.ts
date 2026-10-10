import { test, expect } from "@playwright/test";
import type { Page } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";
import { resolve } from "node:path";
import { spawnSync } from "node:child_process";
import {
  env,
  login,
  mailLink,
  syntheticUser,
  browserHasNoTokens,
  origin,
  csrfHeaders,
} from "./helpers";
import type { Application, Document, Loan, Balance } from "../src/types/api";
async function next(page: Page, title: string) {
  await page
    .getByRole("button", { name: "Save & continue", exact: true })
    .click();
  await expect(
    page.getByRole("heading", { name: title, exact: true }),
  ).toBeVisible();
}
async function select(page: Page, label: string, option: string) {
  await page.getByRole("combobox", { name: label, exact: true }).click();
  await page.getByRole("option", { name: option, exact: true }).click();
}
test("registration to additional evidence, human approval and zero-balance pending-agreement loan", async ({
  page,
  browser,
  request,
}, testInfo) => {
  test.setTimeout(240000);
  const user = syntheticUser();
  await page.goto("/");
  await page
    .getByRole("link", { name: "Create an account", exact: true })
    .click();
  await page
    .getByRole("textbox", { name: "Username", exact: true })
    .fill(user.username);
  await page
    .getByRole("textbox", { name: "Email", exact: true })
    .fill(user.email);
  await page
    .getByRole("textbox", { name: "First name", exact: true })
    .fill("Synthetic");
  await page
    .getByRole("textbox", { name: "Last name", exact: true })
    .fill("Applicant");
  await page
    .getByRole("textbox", { name: "Password", exact: true })
    .fill(user.password);
  await page
    .getByRole("textbox", { name: "Confirm password", exact: true })
    .fill(user.password);
  await page.getByRole("button", { name: "Register", exact: true }).click();
  await expect(
    page.getByRole("heading", { name: "Email verification", exact: false }),
  ).toBeVisible();
  await page.goto(await mailLink(request, user.email));
  await expect(page).toHaveURL(origin + "/app");
  const me = (await (await page.request.get("/bff/me")).json()) as {
    roles: string[];
    capabilities: string[];
    emailVerified: boolean;
  };
  expect(
    me.roles.filter((r) =>
      [
        "APPLICANT",
        "LOAN_OFFICER",
        "COMPLIANCE_REVIEWER",
        "OPERATIONS",
        "PLATFORM_ADMIN",
      ].includes(r),
    ),
  ).toEqual(["APPLICANT"]);
  expect(me.capabilities).toEqual(["APPLY"]);
  expect(me.emailVerified).toBe(true);
  await browserHasNoTokens(page);
  await page
    .getByRole("link", { name: "New application", exact: true })
    .click();
  await page.getByLabel("Requested amount (SEK)").fill("50000.00");
  await page.getByLabel("Term (months)").fill("12");
  await page
    .getByLabel("What will the loan be used for?")
    .fill("Synthetic home improvement");
  await next(page, "Personal details");
  const appId = page.url().match(/applications\/([^/]+)\/edit/)![1];
  await page.getByLabel("Full legal name").fill("Synthetic Applicant");
  await page.getByLabel("Date of birth").fill("1990-01-15");
  await page.getByLabel("Identity identifier").fill("SYNTHETIC-PORTAL-ONLY");
  await page.getByLabel("Contact email").fill(user.email);
  await page.getByLabel("Phone number").fill("+46000000000");
  await page.getByLabel("Home address").fill("Synthetic street 1");
  await page.setViewportSize({ width: 390, height: 844 });
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= window.innerWidth,
    ),
  ).toBe(true);
  expect((await new AxeBuilder({ page }).analyze()).violations).toEqual([]);
  await page.screenshot({
    path: testInfo.outputPath("wizard-mobile.png"),
    fullPage: true,
  });
  await page.setViewportSize({ width: 1440, height: 1000 });
  await next(page, "Employment & income");
  await page.getByLabel("Employer or business").fill("Synthetic Employer");
  await page.getByLabel("Employment start date").fill("2020-01-01");
  await page.getByLabel("Gross income (SEK)", { exact: true }).fill("40000.00");
  await page.getByLabel("Net income (SEK)", { exact: true }).fill("30000.00");
  await next(page, "Living expenses");
  await page.getByLabel("Monthly housing costs (SEK)").fill("4000.00");
  await page.getByLabel("Other monthly living costs (SEK)").fill("4000.00");
  await page
    .getByRole("checkbox", {
      name: "These living costs exclude all debt repayments",
      exact: false,
    })
    .check();
  await next(page, "Existing credit");
  await next(page, "KYC questions");
  await page
    .getByLabel("Purpose of your relationship with the lender")
    .fill("Personal loan for synthetic improvements");
  await page
    .getByLabel("Source of funds", { exact: true })
    .fill("Salary from synthetic employment");
  await next(page, "Documents");
  const endDate = new Date(Date.now() - 86400000).toISOString().slice(0, 10);
  const startDate = endDate.slice(0, 7) + "-01";
  for (const [category, file] of [
    ["Payslip", "payslip.pdf"],
    ["Tax assessment", "tax-assessment.pdf"],
  ]) {
    await select(page, "Document category", category);
    await page
      .getByLabel("Covered period start", { exact: true })
      .fill(startDate);
    await page.getByLabel("Covered period end", { exact: true }).fill(endDate);
    await page
      .getByLabel("Choose supporting document")
      .setInputFiles(resolve("../loan-service/scripts/fixtures", file));
    await page
      .getByRole("button", { name: "Upload document", exact: true })
      .click();
    await expect(
      page.getByText("Upload received.", { exact: false }),
    ).toBeVisible();
  }
  await expect
    .poll(
      async () => {
        const docs = (await (
          await page.request.get(
            `/bff/api/loan-applications/${appId}/documents`,
          )
        ).json()) as Document[];
        return docs.filter(
          (d) => d.scanStatus === "CLEAN" && d.processingStatus === "EXTRACTED",
        ).length;
      },
      { timeout: 45000 },
    )
    .toBe(2);
  await page
    .getByRole("button", { name: "Refresh document status", exact: true })
    .click();
  await page.screenshot({
    path: testInfo.outputPath("applicant-documents-desktop.png"),
    fullPage: true,
  });
  await page
    .getByRole("button", { name: "Preview evidence", exact: true })
    .first()
    .click();
  await expect(page.getByRole("dialog")).toBeVisible();
  await expect(
    page.getByRole("img", { name: "Document page 1.", exact: false }),
  ).toBeVisible();
  await page
    .getByRole("button", { name: "Close preview", exact: true })
    .click();
  await next(page, "Review & submit");
  await page
    .getByRole("checkbox", {
      name: "I acknowledge financial evidence processing",
      exact: false,
    })
    .check();
  expect((await new AxeBuilder({ page }).analyze()).violations).toEqual([]);
  await page
    .getByRole("button", { name: "Review submission", exact: true })
    .click();
  await page
    .getByRole("button", { name: "Confirm submission", exact: true })
    .click();
  await expect(page).toHaveURL(origin + `/app/applications/${appId}`);
  await expect
    .poll(
      async () =>
        (
          (await (
            await page.request.get(`/bff/api/loan-applications/${appId}`)
          ).json()) as Application
        ).state,
      { timeout: 60000 },
    )
    .toBe("MANUAL_REVIEW");
  const staffContext = await browser.newContext({ baseURL: origin });
  const staff = await staffContext.newPage();
  await login(staff, "officer", env.IAM_OFFICER_PASSWORD, true);
  await staff.goto(`/staff/applications/${appId}`);
  await expect(
    staff.getByRole("heading", {
      name: "Deterministic affordability",
      exact: true,
    }),
  ).toBeVisible();
  await staff.getByLabel("Reason codes").fill("EVIDENCE_UPDATE");
  await staff
    .getByLabel("Restricted reviewer explanation")
    .fill(
      "Human reviewer requests an updated payslip for synthetic evidence review.",
    );
  await staff
    .getByLabel("Explanation shown to the applicant")
    .fill(
      "Please replace the payslip with a current copy and submit a new revision.",
    );
  await staff
    .getByRole("button", { name: "Review decision", exact: true })
    .click();
  await staff
    .getByRole("button", { name: "Confirm human decision", exact: true })
    .click();
  await expect
    .poll(
      async () =>
        (
          (await (
            await page.request.get(`/bff/api/loan-applications/${appId}`)
          ).json()) as Application
        ).state,
    )
    .toBe("DOCUMENTS_REQUIRED");
  await page.reload();
  await expect(
    page.getByText("Please replace the payslip", { exact: false }),
  ).toHaveCount(2);
  await page
    .getByRole("link", { name: "Edit / add evidence", exact: true })
    .click();
  await page.getByRole("tab", { name: "Documents", exact: true }).click();
  const docs = (await (
    await page.request.get(`/bff/api/loan-applications/${appId}/documents`)
  ).json()) as Document[];
  const payslip = docs.find((d) => d.category === "PAYSLIP")!;
  await select(
    page,
    "Replaces an earlier document",
    `Payslip · ${payslip.periodEnd}`,
  );
  await page
    .getByLabel("Covered period start", { exact: true })
    .fill(startDate);
  await page.getByLabel("Covered period end", { exact: true }).fill(endDate);
  await page
    .getByLabel("Choose supporting document")
    .setInputFiles(resolve("../loan-service/scripts/fixtures/payslip.pdf"));
  await page
    .getByRole("button", { name: "Upload document", exact: true })
    .click();
  await expect(
    page.getByText("Upload received.", { exact: false }),
  ).toBeVisible();
  await expect
    .poll(
      async () => {
        const documents = (await (
          await page.request.get(
            `/bff/api/loan-applications/${appId}/documents`,
          )
        ).json()) as Document[];
        return documents.find((d) => d.supersedes === payslip.id)
          ?.processingStatus;
      },
      { timeout: 45000 },
    )
    .toBe("EXTRACTED");
  await page.getByRole("tab", { name: "Review & submit", exact: true }).click();
  await page
    .getByRole("button", { name: "Review submission", exact: true })
    .click();
  await page
    .getByRole("button", { name: "Confirm submission", exact: true })
    .click();
  await expect
    .poll(
      async () => {
        const app = (await (
          await page.request.get(`/bff/api/loan-applications/${appId}`)
        ).json()) as Application;
        return `${app.revision}:${app.state}`;
      },
      { timeout: 60000 },
    )
    .toBe("2:MANUAL_REVIEW");
  await staff.reload();
  await expect(
    staff.getByRole("heading", {
      name: "Deterministic affordability",
      exact: true,
    }),
  ).toBeVisible();
  await select(staff, "Decision outcome", "Approve");
  await staff.getByLabel("Reason codes").fill("HUMAN_REVIEW_COMPLETED");
  await staff
    .getByLabel("Restricted reviewer explanation")
    .fill(
      "Current synthetic evidence, declared finances and all material limitations were reviewed by a human.",
    );
  await staff
    .getByLabel("Explanation shown to the applicant")
    .fill(
      "Approved after human review. Agreement acceptance and funding are separate.",
    );
  const reviewChecks = staff.locator("input[type=checkbox]");
  for (const check of await reviewChecks.all()) await check.check();
  expect((await new AxeBuilder({ page: staff }).analyze()).violations).toEqual(
    [],
  );
  await staff.screenshot({
    path: testInfo.outputPath("staff-review-desktop.png"),
    fullPage: true,
  });
  await staff
    .getByRole("button", { name: "Review decision", exact: true })
    .click();
  await expect(staff.getByRole("dialog")).toContainText("Revision 2");
  const approvalRequest = staff.waitForRequest(
    (r) => r.url().endsWith("/decisions") && r.method() === "POST",
  );
  await staff
    .getByRole("button", { name: "Confirm human decision", exact: true })
    .click();
  const original = await approvalRequest;
  await expect
    .poll(
      async () =>
        (
          (await (
            await page.request.get(`/bff/api/loan-applications/${appId}`)
          ).json()) as Application
        ).state,
    )
    .toBe("APPROVED");
  const approved = (await (
    await page.request.get(`/bff/api/loan-applications/${appId}`)
  ).json()) as Application;
  expect(approved.loanId).toBeTruthy();
  const originalHeaders = original.headers();
  const replay = await staff.request.post(
    `/bff/api/loan-applications/${appId}/decisions`,
    { headers: originalHeaders, data: original.postDataJSON() },
  );
  expect(replay.status()).toBe(202);
  expect(((await replay.json()) as { loanId: string }).loanId).toBe(
    approved.loanId,
  );
  const stale = await staff.request.post(
    `/bff/api/loan-applications/${appId}/decisions`,
    {
      headers: { ...originalHeaders, "idempotency-key": crypto.randomUUID() },
      data: original.postDataJSON(),
    },
  );
  expect(stale.status()).toBe(409);
  await expect
    .poll(
      async () =>
        (
          (await (
            await page.request.get(`/bff/api/loans/${approved.loanId}`)
          ).json()) as Loan
        ).lifecycle,
      { timeout: 60000 },
    )
    .toBe("AWAITING_AGREEMENT");
  const balance = (await (
    await page.request.get(`/bff/api/loans/${approved.loanId}/balance`)
  ).json()) as Balance;
  expect(balance.balance).toBe("0.00");
  expect(balance.totalDebits).toBe("0.00");
  expect(balance.totalCredits).toBe("0.00");
  await page.goto(`/app/loans/${approved.loanId}`);
  await expect(
    page.getByText("Awaiting agreement", { exact: true }),
  ).toBeVisible();
  await expect(page.getByTestId("ledger-balance")).toHaveText("0.00 SEK");
  await page.setViewportSize({ width: 390, height: 844 });
  expect((await new AxeBuilder({ page }).analyze()).violations).toEqual([]);
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBe(true);
  await page.screenshot({
    path: testInfo.outputPath("loan-mobile.png"),
    fullPage: true,
  });
  await browserHasNoTokens(page);
  // Node-only ledger proof uses the least-privileged loan-ledger machine identity.
  const mapping = (await (
    await page.request.get(`/bff/api/loans/${approved.loanId}/ledger-account`)
  ).json()) as {
    ledgerId: string;
    accountId: string;
    externalReference: string;
  };
  expect(mapping.externalReference).toBe(`loan:${approved.loanId}:principal`);
  const operationsContext = await browser.newContext({ baseURL: origin });
  const operator = await operationsContext.newPage();
  await login(operator, "operations", env.IAM_OPERATIONS_PASSWORD, true);
  const retry = await operator.request.post(
    `/bff/api/loans/${approved.loanId}/provisioning/retry`,
    {
      headers: {
        ...(await csrfHeaders(operator.request)),
        "Idempotency-Key": crypto.randomUUID(),
      },
      data: {
        reasonCode: "RECOVERY_CHECK",
        explanation:
          "Verify stable existing account mapping during operational recovery.",
      },
    },
  );
  expect(retry.status()).toBe(202);
  expect(((await retry.json()) as Loan).id).toBe(approved.loanId);
  const recovered = (await (
    await operator.request.get(
      `/bff/api/loans/${approved.loanId}/ledger-account`,
    )
  ).json()) as { accountId: string };
  expect(recovered.accountId).toBe(mapping.accountId);
  await operationsContext.close();
  const proof = spawnSync(
    "docker",
    [
      "compose",
      "-f",
      "compose.yaml",
      "-f",
      "compose.iam.yaml",
      "run",
      "--rm",
      "--no-deps",
      "ledger-proof",
      approved.loanId!,
      mapping.ledgerId,
      mapping.accountId,
    ],
    { cwd: resolve(".."), encoding: "utf8", timeout: 30000 },
  );
  expect(proof.status, proof.stderr).toBe(0);
  expect(proof.stdout).toContain("no journal entries");
  const headers = await csrfHeaders(page.request);
  await page.request.post("/bff/logout", { headers, maxRedirects: 0 });
  expect(
    (await page.request.get(`/bff/api/loans/${approved.loanId}`)).status(),
  ).toBe(401);
  await staffContext.close();
});
