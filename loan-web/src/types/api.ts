// Maintained against loan-service DTOs and /v3/api-docs. Decimal values remain strings.
export interface Me {
  identityKey: string;
  tenant: string;
  displayName: string;
  emailVerified: boolean;
  roles: string[];
  capabilities: string[];
  staffStepUpRequired: boolean;
  accountUrl: string;
  instance: string;
}
export interface Product {
  id: string;
  version: number;
  name: string;
  currency: string;
  eligible: boolean;
  policyVersion: string;
  minAmount: string;
  maxAmount: string;
  minMonths: number;
  maxMonths: number;
  nominalAnnualRate: string;
  questionnaireVersion: string;
  privacyNoticeVersion: string;
  requiredCategories: string[];
  requiredPayslips: number;
  evidenceFreshnessDays: number;
  maxFileBytes: number;
  mediaTypes: string[];
  maxPages: number;
}
export interface ApplicationInput {
  productId: string;
  productVersion: number;
  requestedAmount: string;
  currency: string;
  termMonths: number;
  purpose: string;
  identity: {
    legalName: string;
    dateOfBirth: string;
    identityIdentifier: string;
    email: string;
    phone: string;
    address: string;
    countryOfResidence: string;
  };
  employment: { type: string; startDate: string | null; employer: string };
  income: {
    grossAmount: string | null;
    netAmount: string | null;
    frequency: string;
    currency: string;
    periodStart: string;
    periodEnd: string;
    variable: boolean;
    otherSources: {
      kind: string;
      netAmount: string | null;
      frequency: string;
      currency: string;
      variable: boolean;
    }[];
  };
  expenses: {
    housing: string | null;
    otherNonDebt: string | null;
    householdSize: number;
    currency: string;
    excludesDebtRepayments: boolean;
  };
  debts: {
    creditor: string;
    type: string;
    outstandingBalance: string | null;
    creditLimit: string | null;
    utilisedBalance: string | null;
    monthlyRepayment: string | null;
    currency: string;
    remainingMonths: number | null;
    source: string;
  }[];
  kyc: {
    questionnaireVersion: string;
    answeredAt: string;
    relationshipPurpose: string;
    sourceOfFunds: string;
    taxResidencies: string[];
    pepDeclared: boolean;
  };
  privacy: {
    noticeVersion: string;
    acknowledgedAt: string;
    financialEvidenceProcessingAcknowledged: boolean;
  };
}
export interface Application {
  id: string;
  state: string;
  version: number;
  revision: number;
  application: ApplicationInput;
  loanId: string | null;
  decisionExplanation: string | null;
  updatedAt: string;
}
export interface ApplicationSummary {
  id: string;
  state: string;
  version: number;
  revision: number;
  updatedAt: string;
  productId: string | null;
  loanId: string | null;
}
export interface Page<T> {
  items: T[];
  total: number;
  page: number;
  size: number;
}
export interface Source {
  documentId: string;
  page: number;
  line: number;
  snippet: string;
}
export interface Fact {
  field: string;
  amount: string;
  currency: string;
  frequency: string;
  periodStart: string;
  periodEnd: string;
  provenance: string;
  source: Source;
}
export interface Document {
  id: string;
  category: string;
  uploadedForRevision: number;
  mediaType: string;
  sizeBytes: number;
  checksum: string;
  periodStart: string;
  periodEnd: string;
  supersedes: string | null;
  uploadStatus: string;
  scanStatus: string;
  processingStatus: string;
  evidence: { facts: Fact[]; limitations: string[] } | null;
}
export interface Schedule {
  monthlyInstalment: string;
  periods: {
    period: number;
    payment: string;
    principal: string;
    interest: string;
    remainingPrincipal: string;
  }[];
}
export interface Verification {
  gate: string;
  status: string;
  provider: string;
  reference: string;
  checkedAt: string;
  validUntil: string;
  synthetic: boolean;
}
export interface Assessment {
  revision: number;
  productId: string;
  productVersion: number;
  policyVersion: string;
  documentIds: string[];
  assessedAt: string;
  affordability: {
    monthlyNetIncome: string | null;
    monthlyNonDebtExpenses: string | null;
    existingMonthlyDebtRepayments: string | null;
    proposedMonthlyInstalment: string;
    residualIncome: string | null;
    debtServiceToIncome: string | null;
    stressedResidualIncome: string | null;
    blockers: string[];
    findings: string[];
    facts: Fact[];
    schedule: Schedule;
  };
  ai: {
    provider: string;
    model: string;
    promptVersion: string;
    schemaVersion: string;
    executionMillis: number;
    advisory: {
      outcome: string;
      findings: { code: string; evidenceReferences: Source[] }[];
      inconsistencies: { code: string; evidenceReferences: Source[] }[];
      missingInformation: string[];
      limitations: string[];
      questionsForReviewer: string[];
    };
  };
  verifications: Verification[];
  evidenceLimitations: string[];
}
export interface Decision {
  outcome: "APPROVE" | "REJECT" | "REQUEST_INFORMATION";
  reasonCodes: string[];
  explanation: string;
  applicantExplanation: string;
  acknowledgedDocuments: string[];
  acknowledgedFindings: string[];
  financialFactsConfirmed: boolean;
  declaredExpensesAndDebtsReviewed: boolean;
  authenticityReviewed: boolean;
}
export interface Loan {
  id: string;
  publicReference: string;
  applicationId: string;
  approvedRevision: number;
  decisionId: string;
  approvedAt: string;
  lifecycle: string;
  provisioningStatus: string;
  agreementStatus: string;
  version: number;
}
export interface Terms {
  productId: string;
  productVersion: number;
  policyVersion: string;
  principal: string;
  currency: string;
  termMonths: number;
  nominalAnnualRate: string;
  repaymentFrequency: string;
}
export interface Balance {
  accountId: string;
  currency: string;
  normalBalanceSide: string;
  totalDebits: string;
  totalCredits: string;
  balance: string;
}
export interface AccountMapping {
  ledgerId: string;
  accountId: string;
  purpose: string;
  externalReference: string;
  currency: string;
}
export interface Job {
  id: string;
  kind: string;
  state: string;
  attempts: number;
  availableAt: string;
  lastError: string | null;
  correlationId: string | null;
}
export interface Processing {
  state: string;
  revision: number;
  version: number;
  jobs?: Job[];
}
export interface Timeline {
  action: string;
  revision: number;
  occurred_at: string;
}
export interface InformationRequest {
  id: string;
  revision: number;
  explanation: string;
  createdAt: string;
}
export interface ReviewRequest {
  id: string;
  revision: number;
  request: { reasonCode: string; explanation: string };
  createdAt: string;
}
