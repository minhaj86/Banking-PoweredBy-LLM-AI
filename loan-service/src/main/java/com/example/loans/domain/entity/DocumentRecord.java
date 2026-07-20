package com.example.loans.domain.entity;

import com.example.loans.security.SensitiveTextConverter;
import jakarta.persistence.*;
import lombok.*;
import java.time.*;
import java.util.UUID;

@Entity @Table(name="document") @Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class DocumentRecord {
    @Id private UUID id;
    private UUID applicationId;
    private int uploadedForRevision;
    private String category;
    private String objectKey;
    private String checksum;
    private String mediaType;
    private long sizeBytes;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private UUID supersedes;
    private Instant uploadedAt;
    private Instant expiresAt;
    private Instant purgedAt;
    private String uploadStatus;
    private String scanStatus;
    private String processingStatus;
    private String extractionVersion;
    @Convert(converter=SensitiveTextConverter.class) @Column(columnDefinition="text") private String evidence;
    @Version private long version;
    public static DocumentRecord pending(UUID app,int revision,String category,String checksum,String media,long size,LocalDate start,LocalDate end,UUID replaces,Instant expires) {
        var d=new DocumentRecord(); d.id=UUID.randomUUID(); d.applicationId=app; d.uploadedForRevision=revision;
        d.category=category; d.checksum=checksum; d.mediaType=media; d.sizeBytes=size; d.periodStart=start; d.periodEnd=end; d.supersedes=replaces;
        d.uploadedAt=Instant.now(); d.expiresAt=expires; d.objectKey="quarantine/"+UUID.randomUUID()+"/"+d.id;
        d.uploadStatus="PENDING"; d.scanStatus="NOT_SCANNED"; d.processingStatus="WAITING_UPLOAD"; return d;
    }
    public void completeUpload() { uploadStatus="COMPLETE"; processingStatus="QUEUED"; }
    public void processed(String scan,String status,String evidence) { this.scanStatus=scan; this.processingStatus=status; this.evidence=evidence; this.extractionVersion="pdfbox-lines-v1"; }
    public void purged() { purgedAt=Instant.now(); evidence=null; uploadStatus="PURGED"; }
    public void purging() { processingStatus="PURGING"; }
}

