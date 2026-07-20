package com.example.loans.domain.entity;

import com.example.loans.security.SensitiveTextConverter;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="assessment") @Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class AssessmentRecord {
    @Id private UUID id;
    @Column(updatable=false) private UUID applicationId;
    @Column(updatable=false) private int revision;
    @Column(updatable=false) private String policyVersion;
    @Column(updatable=false) private int productVersion;
    @Convert(converter=SensitiveTextConverter.class) @Column(columnDefinition="text",updatable=false) private String payload;
    @Column(updatable=false) private Instant createdAt;
    public static AssessmentRecord create(UUID app,int rev,String policy,int product,String payload) {
        var a=new AssessmentRecord(); a.id=UUID.randomUUID(); a.applicationId=app; a.revision=rev; a.policyVersion=policy; a.productVersion=product; a.payload=payload; a.createdAt=Instant.now(); return a;
    }
}

