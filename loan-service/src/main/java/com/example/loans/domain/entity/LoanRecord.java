package com.example.loans.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="loan") @Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class LoanRecord {
    @Id private UUID id;
    @Column(updatable=false) private String publicReference;
    @Column(updatable=false) private UUID applicationId;
    @Column(updatable=false) private int approvedRevision;
    @Column(updatable=false) private String tenant;
    @Column(updatable=false) private String applicant;
    @Column(updatable=false) private UUID decisionId;
    @Column(updatable=false) private Instant approvedAt;
    private String lifecycle;
    private String provisioningStatus;
    @Column(updatable=false) private String agreementStatus;
    @Version private long version;
    public static LoanRecord approved(ApplicationRecord app,UUID decision) {
        var l=new LoanRecord(); l.id=UUID.randomUUID(); l.publicReference="LN-"+l.id; l.applicationId=app.getId(); l.approvedRevision=app.getCurrentRevision();
        l.tenant=app.getTenant(); l.applicant=app.getApplicant(); l.decisionId=decision; l.approvedAt=Instant.now();
        l.lifecycle="PENDING_LEDGER_ACCOUNT"; l.provisioningStatus="PENDING"; l.agreementStatus="NOT_ACCEPTED"; return l;
    }
    public void provisioned() { lifecycle="AWAITING_AGREEMENT"; provisioningStatus="COMPLETE"; }
    public void failed() { lifecycle="PROVISIONING_FAILED"; provisioningStatus="FAILED"; }
    public void retrying() { if (!provisioningStatus.equals("COMPLETE")) { lifecycle="PENDING_LEDGER_ACCOUNT"; provisioningStatus="PENDING"; } }
}

