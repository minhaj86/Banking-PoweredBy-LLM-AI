package com.example.loans.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity @Table(name="loan_terms") @Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class LoanTerms {
    @Id @Column(updatable=false) private java.util.UUID loanId;
    @Column(updatable=false) private String productId;
    @Column(updatable=false) private int productVersion;
    @Column(updatable=false) private String policyVersion;
    @Column(columnDefinition="numeric",updatable=false) private BigDecimal principal;
    @Column(updatable=false) private String currency;
    @Column(updatable=false) private int termMonths;
    @Column(columnDefinition="numeric",updatable=false) private BigDecimal nominalAnnualRate;
    @Column(updatable=false) private String repaymentFrequency;
    @Column(columnDefinition="text",updatable=false) private String indicativeSchedule;
    public static LoanTerms approved(java.util.UUID loan,String product,int version,String policy,BigDecimal principal,int months,BigDecimal rate,String schedule) {
        var t=new LoanTerms(); t.loanId=loan; t.productId=product; t.productVersion=version; t.policyVersion=policy;
        t.principal=principal; t.currency="SEK"; t.termMonths=months; t.nominalAnnualRate=rate; t.repaymentFrequency="MONTHLY"; t.indicativeSchedule=schedule; return t;
    }
}

