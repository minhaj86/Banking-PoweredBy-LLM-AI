package com.example.loans.service;

import com.example.loans.config.LoanProperties;
import com.example.loans.document.FinancialEvidence;
import com.example.loans.domain.valueobject.Decimals;
import com.example.loans.dto.request.ApplicationInput;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import java.math.*;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class AffordabilityService {
    private final AnnuityCalculator annuity;
    public record Result(String monthlyNetIncome,String monthlyNonDebtExpenses,String existingMonthlyDebtRepayments,
            String proposedMonthlyInstalment,String residualIncome,String debtServiceToIncome,String stressedResidualIncome,
            List<String> blockers,List<String> findings,List<FinancialEvidence.Fact> facts,AnnuityCalculator.Schedule schedule) {}
    public Result assess(ApplicationInput form,LoanProperties.Product policy,List<FinancialEvidence.Fact> facts,Instant assessedAt) {
        var blockers=new LinkedHashSet<String>(); var findings=new LinkedHashSet<String>();
        var principal=Decimals.money(form.requestedAmount());
        var schedule=annuity.calculate(principal,policy.getNominalAnnualRate(),form.termMonths());
        if (!form.currency().equals("SEK") || !form.income().currency().equals("SEK") || !form.expenses().currency().equals("SEK") || form.debts().stream().anyMatch(d -> !d.currency().equals("SEK"))) blockers.add("MIXED_CURRENCY");
        if (!form.expenses().excludesDebtRepayments()) blockers.add("EXPENSE_DEBT_OVERLAP");
        if (form.income().variable() || form.income().frequency().equals("IRREGULAR") || !form.income().otherSources().isEmpty()) blockers.add("IRREGULAR_OR_ADDITIONAL_INCOME_REQUIRES_REVIEW");
        if (form.income().periodStart().isAfter(form.income().periodEnd())) blockers.add("INCONSISTENT_INCOME_PERIOD");
        LocalDate today=assessedAt.atZone(ZoneOffset.UTC).toLocalDate();
        if (form.identity().dateOfBirth().plusYears(policy.getMinimumAge()).isAfter(today)) blockers.add("LEGAL_AGE_GATE");
        BigDecimal declared=monthly(form.income().netAmount(),form.income().frequency());
        if (declared==null) blockers.add("MISSING_DECLARED_INCOME");
        var incomeFacts=facts.stream().filter(f -> f.field().equals("net_income") && f.currency().equals("SEK") && f.frequency().equals("MONTHLY")).toList();
        BigDecimal income=null;
        for (var f:incomeFacts) {
            BigDecimal value=Decimals.money(f.amount());
            if (income!=null && income.compareTo(value)!=0) blockers.add("CONFLICTING_INCOME_EVIDENCE");
            if (income==null) income=value;
        }
        if (income==null) blockers.add("MISSING_INCOME_EVIDENCE");
        if (income!=null && declared!=null && income.compareTo(declared)!=0) blockers.add("DECLARED_INCOME_MISMATCH");
        // DOCUMENT_EXTRACTED is evidence, never proof of authenticity. A reviewer must explicitly confirm these facts.
        BigDecimal housing=Decimals.optional(form.expenses().housing()),living=Decimals.optional(form.expenses().otherNonDebt());
        BigDecimal expenses=housing==null||living==null?null:housing.add(living);
        if (expenses==null) blockers.add("MISSING_NON_DEBT_EXPENSES");
        BigDecimal debt=BigDecimal.ZERO.setScale(2);
        for (var d:form.debts()) {
            BigDecimal amount=Decimals.optional(d.monthlyRepayment());
            if (amount==null) { debt=null; blockers.add("MISSING_DEBT_REPAYMENT"); break; }
            debt=debt.add(amount);
        }
        if(!form.debts().isEmpty() && facts.stream().noneMatch(f -> f.field().equals("debt_repayment"))) blockers.add("MISSING_DEBT_EVIDENCE");
        if(debt!=null && !form.debts().isEmpty()) {
            var extracted=facts.stream().filter(f -> f.field().equals("debt_repayment")).map(f -> Decimals.money(f.amount())).reduce(BigDecimal.ZERO,BigDecimal::add);
            if(extracted.compareTo(debt)!=0) blockers.add("DEBT_EVIDENCE_MISMATCH");
        }
        BigDecimal instalment=new BigDecimal(schedule.monthlyInstalment()),residual=null,dsti=null,stress=null;
        if(income!=null && expenses!=null && debt!=null) {
            residual=income.subtract(expenses).subtract(debt).subtract(instalment);
            BigDecimal stressedPayment=new BigDecimal(annuity.calculate(principal,policy.getStressAnnualRate(),form.termMonths()).monthlyInstalment());
            stress=income.subtract(expenses).subtract(debt).subtract(stressedPayment);
            if(income.signum()>0) dsti=debt.add(instalment).divide(income,6,RoundingMode.HALF_EVEN);
            else blockers.add("NON_POSITIVE_INCOME");
            if(residual.compareTo(policy.getMinimumResidual())<0 || stress.compareTo(policy.getMinimumResidual())<0) findings.add("RESIDUAL_INCOME_CONCERN");
            if(dsti!=null && dsti.compareTo(policy.getMaximumDsti())>0) findings.add("DEBT_SERVICE_CONCERN");
        }
        return new Result(Decimals.format(income),Decimals.format(expenses),Decimals.format(debt),instalment.toPlainString(),
                Decimals.format(residual),Decimals.format(dsti),Decimals.format(stress),List.copyOf(blockers),List.copyOf(findings),List.copyOf(facts),schedule);
    }
    private BigDecimal monthly(String value,String frequency) {
        BigDecimal amount=Decimals.optional(value);
        if(amount==null) return null;
        return switch(frequency) {
            case "MONTHLY" -> amount;
            case "ANNUAL" -> amount.divide(new BigDecimal("12"),2,RoundingMode.HALF_EVEN);
            case "WEEKLY" -> amount.multiply(new BigDecimal("52")).divide(new BigDecimal("12"),2,RoundingMode.HALF_EVEN);
            default -> null;
        };
    }
}

