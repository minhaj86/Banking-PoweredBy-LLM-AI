import { z } from "zod";
import type { ApplicationInput, Product } from "../../types/api";
const required = z.string().trim().min(1, "Enter this information");
const decimal = z
  .string()
  .regex(
    /^\d+(\.\d{1,2})?$/,
    "Use a decimal amount with at most two decimal places",
  );
const optionalMoney = decimal.nullable();
const date = required.regex(/^\d{4}-\d{2}-\d{2}$/, "Choose a date");
const country = z.string().regex(/^[A-Z]{2}$/, "Use a two-letter country code");
function cents(s: string) {
  const [whole, fraction = ""] = s.split(".");
  return BigInt(whole) * 100n + BigInt(fraction.padEnd(2, "0"));
}
export function applicationSchema(product: Product) {
  return z.object({
    productId: required,
    productVersion: z.number().int(),
    requestedAmount: decimal.refine(
      (s) =>
        /^\d+(\.\d{1,2})?$/.test(s) &&
        cents(s) >= cents(product.minAmount) &&
        cents(s) <= cents(product.maxAmount),
      `Amount must be ${product.minAmount}–${product.maxAmount} SEK`,
    ),
    currency: z.literal("SEK"),
    termMonths: z.number().int().min(product.minMonths).max(product.maxMonths),
    purpose: required.max(200),
    identity: z.object({
      legalName: required.max(200),
      dateOfBirth: date,
      identityIdentifier: required.max(100),
      email: z.email("Enter a valid email address"),
      phone: required.max(40),
      address: required.max(500),
      countryOfResidence: country,
    }),
    employment: z
      .object({
        type: required,
        startDate: date.nullable(),
        employer: z.string().max(200),
      })
      .refine(
        (v) =>
          v.type !== "EMPLOYED" || Boolean(v.startDate && v.employer.trim()),
        {
          message:
            "Employment start date and employer are required for employed applicants",
          path: ["employer"],
        },
      ),
    income: z.object({
      grossAmount: optionalMoney,
      netAmount: decimal,
      frequency: required,
      currency: z.literal("SEK"),
      periodStart: date,
      periodEnd: date,
      variable: z.boolean(),
      otherSources: z.array(
        z.object({
          kind: required,
          netAmount: optionalMoney,
          frequency: required,
          currency: required,
          variable: z.boolean(),
        }),
      ),
    }),
    expenses: z.object({
      housing: decimal,
      otherNonDebt: decimal,
      householdSize: z.number().int().min(1).max(30),
      currency: z.literal("SEK"),
      excludesDebtRepayments: z.literal(
        true,
        "Confirm that living expenses exclude debt repayments",
      ),
    }),
    debts: z
      .array(
        z.object({
          creditor: required.max(200),
          type: required,
          outstandingBalance: optionalMoney,
          creditLimit: optionalMoney,
          utilisedBalance: optionalMoney,
          monthlyRepayment: decimal,
          currency: z.literal("SEK"),
          remainingMonths: z.number().int().nonnegative().nullable(),
          source: z.literal("APPLICANT_DECLARED"),
        }),
      )
      .max(50),
    kyc: z.object({
      questionnaireVersion: required,
      answeredAt: required,
      relationshipPurpose: required.max(500),
      sourceOfFunds: required.max(500),
      taxResidencies: z.array(country).min(1).max(10),
      pepDeclared: z.boolean(),
    }),
    privacy: z.object({
      noticeVersion: required,
      acknowledgedAt: required,
      financialEvidenceProcessingAcknowledged: z.literal(
        true,
        "Acknowledge the financial evidence processing notice",
      ),
    }),
  });
}
export function newDraft(p: Product): ApplicationInput {
  const today = new Date().toISOString().slice(0, 10);
  const now = new Date().toISOString();
  return {
    productId: p.id,
    productVersion: p.version,
    requestedAmount: "",
    currency: "SEK",
    termMonths: Math.max(p.minMonths, 12),
    purpose: "",
    identity: {
      legalName: "",
      dateOfBirth: "",
      identityIdentifier: "",
      email: "",
      phone: "",
      address: "",
      countryOfResidence: "SE",
    },
    employment: { type: "EMPLOYED", startDate: null, employer: "" },
    income: {
      grossAmount: null,
      netAmount: null,
      frequency: "MONTHLY",
      currency: "SEK",
      periodStart: today,
      periodEnd: today,
      variable: false,
      otherSources: [],
    },
    expenses: {
      housing: null,
      otherNonDebt: null,
      householdSize: 1,
      currency: "SEK",
      excludesDebtRepayments: false,
    },
    debts: [],
    kyc: {
      questionnaireVersion: p.questionnaireVersion,
      answeredAt: now,
      relationshipPurpose: "",
      sourceOfFunds: "",
      taxResidencies: ["SE"],
      pepDeclared: false,
    },
    privacy: {
      noticeVersion: p.privacyNoticeVersion,
      acknowledgedAt: now,
      financialEvidenceProcessingAcknowledged: false,
    },
  };
}
export const steps = [
  "Your loan",
  "Personal details",
  "Employment & income",
  "Living expenses",
  "Existing credit",
  "KYC questions",
  "Documents",
  "Review & submit",
];
export const stepFields = [
  ["requestedAmount", "termMonths", "purpose"],
  ["identity"],
  ["employment", "income"],
  ["expenses"],
  ["debts"],
  ["kyc"],
  [],
  ["privacy"],
] as const;
