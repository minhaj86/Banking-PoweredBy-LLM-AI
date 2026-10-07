import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { ApplicationSummary } from "./ApplicationSummary";
import { applicationSchema, newDraft } from "./form";
import { application, product } from "../../test/fixtures";
import { money, percent } from "../../lib/format";
describe("application validation and exact presentation", () => {
  it("allows an incomplete backend draft while identifying missing submission fields", () => {
    const result = applicationSchema(product).safeParse(newDraft(product));
    expect(result.success).toBe(false);
    if (!result.success)
      expect(result.error.issues.map((i) => i.path.join("."))).toContain(
        "requestedAmount",
      );
  });
  it("validates configured product limits without floating-point conversion", () => {
    const form = application().application;
    expect(applicationSchema(product).safeParse(form).success).toBe(true);
    form.requestedAmount = "300000.01";
    expect(applicationSchema(product).safeParse(form).success).toBe(false);
    form.requestedAmount = "50000.001";
    expect(applicationSchema(product).safeParse(form).success).toBe(false);
  });
  it("requires non-overlap acknowledgement and a privacy acknowledgement", () => {
    const form = application().application;
    form.expenses.excludesDebtRepayments = false;
    form.privacy.financialEvidenceProcessingAcknowledged = false;
    const result = applicationSchema(product).safeParse(form);
    expect(result.success).toBe(false);
    if (!result.success)
      expect(result.error.issues.map((i) => i.path.join("."))).toEqual(
        expect.arrayContaining([
          "expenses.excludesDebtRepayments",
          "privacy.financialEvidenceProcessingAcknowledged",
        ]),
      );
  });
  it("renders values beyond JavaScript safe integer precision exactly", () => {
    expect(money("9007199254740993.17")).toBe(
      "9\u202f007\u202f199\u202f254\u202f740\u202f993.17 SEK",
    );
    expect(percent("0.080000")).toBe("8%");
    const form = application().application;
    form.income.netAmount = "9007199254740993.17";
    render(<ApplicationSummary input={form} />);
    expect(
      screen.getByText(
        "9\u202f007\u202f199\u202f254\u202f740\u202f993.17 SEK",
        { normalizer: (v) => v },
      ),
    ).toBeVisible();
  });
});
