package com.example.loans.domain.entity;

import com.example.loans.domain.enums.ApplicationState;
import com.example.loans.security.SensitiveTextConverter;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="application") @Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class ApplicationRecord {
    @Id private UUID id;
    @Column(nullable=false,updatable=false) private String tenant;
    @Column(nullable=false,updatable=false) private String applicant;
    @Version private long version;
    @Enumerated(EnumType.STRING) private ApplicationState state;
    private int currentRevision;
    private boolean legalHold;
    @Convert(converter=SensitiveTextConverter.class) @Column(columnDefinition="text") private String draft;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant purgedAt;
    private String productId;
    private Integer productVersion;
    public void product(String id,int version) { this.productId=id; this.productVersion=version; }
    public static ApplicationRecord create(ActorData owner,String draft) {
        var a=new ApplicationRecord(); a.id=UUID.randomUUID(); a.tenant=owner.tenant(); a.applicant=owner.subject();
        a.draft=draft; a.state=ApplicationState.DRAFT; a.createdAt=Instant.now(); a.updatedAt=a.createdAt; return a;
    }
    public record ActorData(String tenant,String subject) {}
    public void edit(String draft) { this.draft=draft; this.state=ApplicationState.DRAFT; touch(); }
    public void revise() { currentRevision++; state=ApplicationState.SUBMITTED; touch(); }
    public void transition(ApplicationState target) {
        boolean allowed = switch(target) {
            case WITHDRAWN -> !state.terminal();
            case APPROVED, REJECTED -> state==ApplicationState.MANUAL_REVIEW;
            case DOCUMENTS_REQUIRED -> state==ApplicationState.MANUAL_REVIEW || state==ApplicationState.ASSESSMENT_PENDING;
            case ASSESSMENT_PENDING -> state==ApplicationState.SUBMITTED;
            case MANUAL_REVIEW, ASSESSMENT_FAILED -> state==ApplicationState.ASSESSMENT_PENDING;
            default -> false;
        };
        if (!allowed) throw com.example.loans.exception.LoanException.conflict("INVALID_TRANSITION","Application state does not allow this operation");
        state=target; touch();
    }
    public void hold(boolean value) { legalHold=value; touch(); }
    public void reviewed() { touch(); }
    private void touch() { updatedAt=Instant.now(); }
}

