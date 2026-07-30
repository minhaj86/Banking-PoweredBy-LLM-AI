package com.example.loans.service;

import com.example.loans.*;
import com.example.loans.config.LoanProperties;
import com.example.loans.document.FinancialEvidence.*;
import com.example.loans.dto.request.ApplicationInput;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class AffordabilityServiceTest {
    private final AffordabilityService service=new AffordabilityService(new AnnuityCalculator());
    private Fact fact(String amount) { return new Fact("net_income",amount,"SEK","MONTHLY",LocalDate.now().minusMonths(1),LocalDate.now(),Provenance.DOCUMENT_EXTRACTED,new Source(UUID.randomUUID(),1,1,"net_income="+amount+" SEK MONTHLY")); }
    @Test void residualDoesNotDoubleCountDebt() {
        var input=TestFixtures.input("30000.00","4000.00");
        var result=service.assess(input,new LoanProperties.Product(),List.of(fact("30000.00")),Instant.now());
        assertThat(result.residualIncome()).isEqualTo("15972.36");
        assertThat(result.existingMonthlyDebtRepayments()).isEqualTo("0.00");
        assertThat(result.blockers()).isEmpty();
    }
    @Test void unknownIncomeIsNeverZeroAndConflictsBlockApproval() {
        var missing=service.assess(TestFixtures.input(),new LoanProperties.Product(),List.of(),Instant.now());
        assertThat(missing.monthlyNetIncome()).isNull(); assertThat(missing.residualIncome()).isNull();
        assertThat(missing.blockers()).contains("MISSING_INCOME_EVIDENCE");
        assertThat(service.assess(TestFixtures.input(),new LoanProperties.Product(),List.of(fact("30000.00"),fact("40000.00")),Instant.now()).blockers()).contains("CONFLICTING_INCOME_EVIDENCE");
    }
    @Test void zeroIncomeDoesNotDivideAndConcernNeverBecomesDecision() {
        var r=service.assess(TestFixtures.input("0.00","1000.00"),new LoanProperties.Product(),List.of(fact("0.00")),Instant.now());
        assertThat(r.debtServiceToIncome()).isNull(); assertThat(r.blockers()).contains("NON_POSITIVE_INCOME");
        assertThat(r.findings()).contains("RESIDUAL_INCOME_CONCERN");
    }
    @Test void overlappingExpensesUnknownPaymentsAndMixedCurrencyRequireReview() {
        var a=TestFixtures.input("30000.00","4000.00");
        var in=new ApplicationInput(a.productId(),a.productVersion(),a.requestedAmount(),a.currency(),a.termMonths(),a.purpose(),a.identity(),a.employment(),a.income(),
            new ApplicationInput.Expenses("8000.00","4000.00",1,"SEK",false),
            List.of(new ApplicationInput.Debt("Synthetic lender","REVOLVING","1000.00","5000.00","1000.00",null,"EUR",12,"APPLICANT_DECLARED")),a.kyc(),a.privacy());
        var r=service.assess(in,new LoanProperties.Product(),List.of(fact("30000.00")),Instant.now());
        assertThat(r.blockers()).contains("EXPENSE_DEBT_OVERLAP","MISSING_DEBT_REPAYMENT","MIXED_CURRENCY");
        assertThat(r.existingMonthlyDebtRepayments()).isNull();assertThat(r.residualIncome()).isNull();
    }
}

