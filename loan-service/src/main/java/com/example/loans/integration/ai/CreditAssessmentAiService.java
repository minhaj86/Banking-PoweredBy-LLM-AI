package com.example.loans.integration.ai;

import com.example.loans.document.FinancialEvidence;
import java.util.*;

public interface CreditAssessmentAiService {
    String PROMPT_VERSION="financial-advisory-v1";
    String SCHEMA_VERSION="advisory-v1";
    enum Outcome { SUPPORTS_REVIEW_FOR_APPROVAL, AFFORDABILITY_CONCERNS, INSUFFICIENT_EVIDENCE }
    enum FindingCode { INCOME_SUPPORTED, DECLARATION_MISMATCH, MISSING_INFORMATION, AFFORDABILITY_CONCERN, EXTRACTION_LIMITATION }
    enum Limitation { ADVISORY_ONLY, EXTRACTION_NOT_AUTHENTICATION, SYNTHETIC_MOCK_AI }
    enum Question { CONFIRM_FINANCIAL_FACTS, RESOLVE_DISCREPANCIES, REVIEW_AFFORDABILITY, OBTAIN_MISSING_EVIDENCE }
    record Finding(FindingCode code,List<FinancialEvidence.Source> evidenceReferences) {}
    record Advisory(Outcome outcome,List<Finding> findings,List<Finding> inconsistencies,
            List<String> missingInformation,List<Limitation> limitations,List<Question> questionsForReviewer) {}
    record Input(Map<String,String> financialInputs,List<FinancialEvidence.Fact> facts,List<String> blockers,List<String> policyFindings) {}
    record Execution(Advisory advisory,String provider,String model,String promptVersion,String schemaVersion,long executionMillis) {}
    Execution assess(Input input);
}

