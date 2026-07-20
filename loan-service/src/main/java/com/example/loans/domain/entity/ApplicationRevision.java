package com.example.loans.domain.entity;

import com.example.loans.security.SensitiveTextConverter;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="application_revision") @Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class ApplicationRevision {
    @Id private UUID id;
    @Column(updatable=false) private UUID applicationId;
    @Column(updatable=false) private int revision;
    @Column(updatable=false) private String productId;
    @Column(updatable=false) private int productVersion;
    @Column(updatable=false) private String policyVersion;
    @Convert(converter=SensitiveTextConverter.class) @Column(columnDefinition="text",updatable=false) private String payload;
    @Column(columnDefinition="text",updatable=false) private String documentIds;
    @Column(columnDefinition="text",updatable=false) private String policySnapshot;
    @Column(updatable=false) private Instant submittedAt;
    public static ApplicationRevision create(UUID app,int revision,String product,int productVersion,String policy,String payload,String documents,String policySnapshot) {
        var r=new ApplicationRevision(); r.id=UUID.randomUUID(); r.applicationId=app; r.revision=revision; r.productId=product; r.productVersion=productVersion;
        r.policyVersion=policy; r.payload=payload; r.documentIds=documents; r.policySnapshot=policySnapshot; r.submittedAt=Instant.now(); return r;
    }
}

