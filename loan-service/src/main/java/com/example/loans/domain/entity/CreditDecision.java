package com.example.loans.domain.entity;

import com.example.loans.security.SensitiveTextConverter;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="credit_decision") @Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class CreditDecision {
    @Id private UUID id;
    @Column(updatable=false) private UUID applicationId;
    @Column(updatable=false) private int revision;
    @Column(updatable=false) private UUID assessmentId;
    @Column(updatable=false) private String reviewer;
    @Column(updatable=false) private String outcome;
    @Convert(converter=SensitiveTextConverter.class) @Column(columnDefinition="text",updatable=false) private String payload;
    @Convert(converter=SensitiveTextConverter.class) @Column(columnDefinition="text",updatable=false) private String applicantExplanation;
    @Column(updatable=false) private Instant createdAt;
    public static CreditDecision create(UUID app,int rev,UUID assessment,String reviewer,String outcome,String payload,String publicText) {
        var d=new CreditDecision(); d.id=UUID.randomUUID(); d.applicationId=app; d.revision=rev; d.assessmentId=assessment; d.reviewer=reviewer; d.outcome=outcome;
        d.payload=payload; d.applicantExplanation=publicText; d.createdAt=Instant.now(); return d;
    }
}

