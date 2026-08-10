package com.example.loans.integration.ai;

import com.example.loans.exception.LoanException;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class AdvisoryValidator {
    public CreditAssessmentAiService.Advisory validate(CreditAssessmentAiService.Input input,CreditAssessmentAiService.Advisory output) {
        if(output==null || output.outcome()==null || output.findings()==null || output.inconsistencies()==null || output.missingInformation()==null || output.limitations()==null || output.questionsForReviewer()==null) throw invalid();
        if(output.findings().size()+output.inconsistencies().size()>30 || output.questionsForReviewer().size()>10) throw invalid();
        var allowed=new HashSet<>(input.facts().stream().map(f->f.source()).toList());
        var all=new ArrayList<>(output.findings()); all.addAll(output.inconsistencies());
        if(all.stream().anyMatch(Objects::isNull)||output.missingInformation().stream().anyMatch(Objects::isNull)||output.limitations().stream().anyMatch(Objects::isNull)||output.questionsForReviewer().stream().anyMatch(Objects::isNull)) throw invalid();
        for(var finding:all) {
            if(finding.code()==null || finding.evidenceReferences()==null || finding.evidenceReferences().size()>50 || !allowed.containsAll(finding.evidenceReferences())) throw invalid();
            if(finding.code()!=CreditAssessmentAiService.FindingCode.MISSING_INFORMATION && finding.code()!=CreditAssessmentAiService.FindingCode.AFFORDABILITY_CONCERN && finding.evidenceReferences().isEmpty()) throw invalid();
            if(finding.code()==CreditAssessmentAiService.FindingCode.AFFORDABILITY_CONCERN && input.policyFindings().isEmpty()) throw invalid();
            if(finding.code()==CreditAssessmentAiService.FindingCode.DECLARATION_MISMATCH && input.blockers().stream().noneMatch(s->s.contains("MISMATCH")||s.contains("CONFLICT"))) throw invalid();
            if(finding.code()==CreditAssessmentAiService.FindingCode.INCOME_SUPPORTED && finding.evidenceReferences().stream().anyMatch(s->input.facts().stream().noneMatch(f->f.source().equals(s)&&f.field().equals("net_income")))) throw invalid();
        }
        if(!input.blockers().containsAll(output.missingInformation())) throw invalid();
        if(!output.limitations().contains(CreditAssessmentAiService.Limitation.ADVISORY_ONLY) || !output.limitations().contains(CreditAssessmentAiService.Limitation.EXTRACTION_NOT_AUTHENTICATION)) throw invalid();
        if(output.outcome()==CreditAssessmentAiService.Outcome.SUPPORTS_REVIEW_FOR_APPROVAL && (!input.blockers().isEmpty() || !input.policyFindings().isEmpty())) throw invalid();
        return output;
    }
    private LoanException invalid() { return LoanException.invalid("AI_INVALID_OUTPUT","Advisory output failed schema, policy or evidence-reference validation"); }
}

