import type { Application, Assessment, Me, Product } from "../types/api";
import { newDraft } from "../features/applications/form";
export const product: Product = {
  id: "personal-sek",
  version: 1,
  name: "Personal loan",
  currency: "SEK",
  eligible: true,
  policyVersion: "demo-1",
  minAmount: "1000.00",
  maxAmount: "300000.00",
  minMonths: 3,
  maxMonths: 120,
  nominalAnnualRate: "0.080000",
  questionnaireVersion: "demo-1",
  privacyNoticeVersion: "demo-1",
  requiredCategories: ["PAYSLIP", "TAX_ASSESSMENT"],
  requiredPayslips: 1,
  evidenceFreshnessDays: 120,
  maxFileBytes: 10485760,
  mediaTypes: ["application/pdf", "image/png", "image/jpeg"],
  maxPages: 20,
};
export const me: Me = {
  identityKey: "oidc:applicant",
  tenant: "demo",
  displayName: "Synthetic Applicant",
  emailVerified: true,
  roles: ["APPLICANT"],
  capabilities: ["APPLY"],
  staffStepUpRequired: false,
  accountUrl: "/auth/realms/loans/account/",
  instance: "bff-a",
};
export function application(): Application {
  const input = newDraft(product);
  input.requestedAmount = "50000.00";
  input.purpose = "Synthetic improvements";
  input.identity = {
    legalName: "Synthetic Applicant",
    dateOfBirth: "1990-01-15",
    identityIdentifier: "SYNTHETIC-ID",
    email: "applicant@example.test",
    phone: "+4600000000",
    address: "Synthetic street 1",
    countryOfResidence: "SE",
  };
  input.employment = {
    type: "EMPLOYED",
    startDate: "2020-01-01",
    employer: "Synthetic employer",
  };
  input.income.netAmount = "30000.00";
  input.expenses = {
    housing: "4000.00",
    otherNonDebt: "4000.00",
    householdSize: 1,
    currency: "SEK",
    excludesDebtRepayments: true,
  };
  input.kyc.relationshipPurpose = "Personal loan";
  input.kyc.sourceOfFunds = "Salary";
  input.privacy.financialEvidenceProcessingAcknowledged = true;
  return {
    id: "11111111-1111-4111-8111-111111111111",
    state: "DRAFT",
    version: 4,
    revision: 0,
    application: input,
    loanId: null,
    decisionExplanation: null,
    updatedAt: "2026-10-28T12:00:00Z",
  };
}
export const assessment: Assessment = {
  revision: 2,
  productId: product.id,
  productVersion: 1,
  policyVersion: "demo-1",
  documentIds: ["doc-one"],
  assessedAt: "2026-10-28T12:00:00Z",
  evidenceLimitations: ["EXTRACTION_NOT_AUTHENTICATION"],
  affordability: {
    monthlyNetIncome: "30000.00",
    monthlyNonDebtExpenses: "8000.00",
    existingMonthlyDebtRepayments: "0.00",
    proposedMonthlyInstalment: "4349.42",
    residualIncome: "17650.58",
    debtServiceToIncome: "0.144981",
    stressedResidualIncome: "17580.92",
    blockers: [],
    findings: [],
    facts: [],
    schedule: { monthlyInstalment: "4349.42", periods: [] },
  },
  verifications: [],
  ai: {
    provider: "mock",
    model: "local",
    promptVersion: "v1",
    schemaVersion: "v1",
    executionMillis: 1,
    advisory: {
      outcome: "SUPPORTS_REVIEW_FOR_APPROVAL",
      findings: [],
      inconsistencies: [],
      missingInformation: [],
      limitations: ["ADVISORY_ONLY"],
      questionsForReviewer: ["CONFIRM_FINANCIAL_FACTS"],
    },
  },
};
