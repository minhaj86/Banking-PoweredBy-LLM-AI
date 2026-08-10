package com.example.loans.integration.ai;

import com.example.loans.exception.LoanException;
import lombok.RequiredArgsConstructor;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

@RequiredArgsConstructor
public class MockCreditAssessmentAiService implements CreditAssessmentAiService {
    private final AdvisoryValidator validator;
    private final AtomicBoolean unavailable=new AtomicBoolean();
    public void setUnavailable(boolean value) { unavailable.set(value); }
    @Override public Execution assess(Input input) {
        if(unavailable.get()) throw LoanException.unavailable("SYNTHETIC_AI_OUTAGE");
        var outcome=!input.blockers().isEmpty()?Outcome.INSUFFICIENT_EVIDENCE:!input.policyFindings().isEmpty()?Outcome.AFFORDABILITY_CONCERNS:Outcome.SUPPORTS_REVIEW_FOR_APPROVAL;
        var income=input.facts().stream().filter(f->f.field().equals("net_income")).findFirst();
        var findings=income.isEmpty()?List.<Finding>of():List.of(new Finding(FindingCode.INCOME_SUPPORTED,List.of(income.get().source())));
        var advisory=new Advisory(outcome,findings,List.of(),input.blockers(),List.of(Limitation.ADVISORY_ONLY,Limitation.EXTRACTION_NOT_AUTHENTICATION,Limitation.SYNTHETIC_MOCK_AI),List.of(Question.CONFIRM_FINANCIAL_FACTS));
        return new Execution(validator.validate(input,advisory),"mock","deterministic-demo-v1",PROMPT_VERSION,SCHEMA_VERSION,0);
    }
}
