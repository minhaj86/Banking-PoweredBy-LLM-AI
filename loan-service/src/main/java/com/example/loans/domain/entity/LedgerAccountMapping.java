package com.example.loans.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="ledger_account_mapping") @Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class LedgerAccountMapping {
    @Id @Column(updatable=false) private UUID loanId;
    @Column(updatable=false) private String purpose;
    @Column(updatable=false) private UUID ledgerId;
    @Column(updatable=false) private UUID accountId;
    @Column(updatable=false) private String externalReference;
    @Column(updatable=false) private String currency;
    @Column(updatable=false) private Instant createdAt;
    public static LedgerAccountMapping principal(UUID loan,UUID ledger,UUID account,String reference) {
        var m=new LedgerAccountMapping(); m.loanId=loan; m.ledgerId=ledger; m.accountId=account; m.externalReference=reference;
        m.purpose="PRINCIPAL_RECEIVABLE"; m.currency="SEK"; m.createdAt=Instant.now(); return m;
    }
}

