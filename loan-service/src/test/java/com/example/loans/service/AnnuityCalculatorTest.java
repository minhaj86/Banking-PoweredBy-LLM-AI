package com.example.loans.service;

import org.junit.jupiter.api.Test;
import java.math.*;
import static org.assertj.core.api.Assertions.*;

class AnnuityCalculatorTest {
    private final AnnuityCalculator calc=new AnnuityCalculator();
    @Test void zeroInterestAndRoundingReconcileExactly() {
        var s=calc.calculate(new BigDecimal("1000.00"),BigDecimal.ZERO,3);
        assertThat(s.monthlyInstalment()).isEqualTo("333.33");
        assertThat(s.periods().getLast().payment()).isEqualTo("333.34");
        assertThat(s.periods().getLast().remainingPrincipal()).isEqualTo("0.00");
        assertThat(s.periods().stream().map(p->new BigDecimal(p.principal())).reduce(BigDecimal.ZERO,BigDecimal::add)).isEqualByComparingTo("1000.00");
    }
    @Test void interestAndPrincipalReconcileForManyTerms() {
        for(String rate:java.util.List.of("0","0.08","0.11","0.999999")) for(int months:java.util.List.of(3,12,60,120)) {
            var s=calc.calculate(new BigDecimal("123456.78"),new BigDecimal(rate),months);
            assertThat(s.periods()).hasSize(months);
            assertThat(s.periods().getLast().remainingPrincipal()).isEqualTo("0.00");
            assertThat(s.periods().stream().map(p->new BigDecimal(p.principal())).reduce(BigDecimal.ZERO,BigDecimal::add)).isEqualByComparingTo("123456.78");
            assertThat(s.periods()).allSatisfy(p->assertThat(new BigDecimal(p.payment())).isEqualByComparingTo(new BigDecimal(p.principal()).add(new BigDecimal(p.interest()))));
        }
    }
    @Test void knownAnnuityAndInvalidTerms() {
        assertThat(calc.calculate(new BigDecimal("100000.00"),new BigDecimal("0.08"),60).monthlyInstalment()).isEqualTo("2027.64");
        assertThatThrownBy(()->calc.calculate(new BigDecimal("1.001"),BigDecimal.ZERO,12)).hasMessageContaining("Unsupported");
    }
}

