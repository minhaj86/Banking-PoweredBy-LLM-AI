package com.example.loans.service;

import com.example.loans.exception.LoanException;
import org.springframework.stereotype.Component;
import java.math.*;
import java.util.*;

@Component
public class AnnuityCalculator {
    private static final MathContext MC = new MathContext(34,RoundingMode.HALF_EVEN);
    public record Instalment(int period, String payment, String principal, String interest, String remainingPrincipal) {}
    public record Schedule(String monthlyInstalment, List<Instalment> periods) {}
    public Schedule calculate(BigDecimal principal, BigDecimal annualRate, int months) {
        if (principal == null || principal.signum() <= 0 || principal.scale() > 2 || annualRate == null || annualRate.signum() < 0 || annualRate.compareTo(BigDecimal.ONE)>0 || months<1 || months>600) throw LoanException.invalid("INVALID_TERMS","Unsupported repayment terms");
        BigDecimal monthlyRate = annualRate.divide(new BigDecimal("12"),MC);
        BigDecimal payment;
        if (annualRate.signum() == 0) payment = principal.divide(new BigDecimal(months),2,RoundingMode.HALF_EVEN);
        else {
            var growth = BigDecimal.ONE.add(monthlyRate,MC).pow(months,MC);
            payment = principal.multiply(monthlyRate,MC).multiply(growth,MC).divide(growth.subtract(BigDecimal.ONE),MC).setScale(2,RoundingMode.HALF_EVEN);
        }
        BigDecimal remaining = principal.setScale(2,RoundingMode.UNNECESSARY);
        var rows = new ArrayList<Instalment>();
        for (int n=1;n<=months;n++) {
            var interest = remaining.multiply(monthlyRate,MC).setScale(2,RoundingMode.HALF_EVEN);
            var repaid = n==months ? remaining : payment.subtract(interest).max(BigDecimal.ZERO).min(remaining);
            remaining = remaining.subtract(repaid);
            rows.add(new Instalment(n,repaid.add(interest).toPlainString(),repaid.toPlainString(),interest.toPlainString(),remaining.toPlainString()));
        }
        return new Schedule(payment.toPlainString(),List.copyOf(rows));
    }
}
