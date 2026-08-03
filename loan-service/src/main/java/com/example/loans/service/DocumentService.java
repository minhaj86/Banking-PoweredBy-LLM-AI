package com.example.loans.service;

import com.example.loans.config.LoanProperties;
import com.example.loans.document.*;
import com.example.loans.domain.entity.DocumentRecord;
import com.example.loans.domain.repository.*;
import com.example.loans.dto.response.DocumentView;
import com.example.loans.exception.LoanException;
import com.example.loans.integration.storage.DocumentStorage;
import com.example.loans.mapper.Json;
import com.example.loans.security.Actor;
import com.example.loans.workflow.JobQueue;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class DocumentService {
    private final DocumentRepository documents;
    private final ResourceAccess access;
    private final DocumentExtractor extractor;
    private final DocumentStorage storage;
    private final LoanProperties config;
    private final JobQueue transactions;
    private final IdempotencyService idempotency;
    private final AuditService audit;
    private final Json json;
    public record UploadMetadata(String category,LocalDate periodStart,LocalDate periodEnd,UUID supersedes) {}
    public DocumentView upload(Actor actor,UUID application,long expected,String key,UploadMetadata metadata,byte[] bytes) {
        actor.require("APPLICANT");
        if(metadata.category()==null || !Set.of("PAYSLIP","TAX_ASSESSMENT","EMPLOYMENT_EVIDENCE","EXISTING_LOAN_STATEMENT","CREDIT_CARD_STATEMENT","OTHER").contains(metadata.category())
            || metadata.periodStart()==null || metadata.periodEnd()==null || metadata.periodStart().isAfter(metadata.periodEnd()) || metadata.periodEnd().isAfter(LocalDate.now(ZoneOffset.UTC))) throw LoanException.invalid("INVALID_DOCUMENT_PERIOD_OR_CATEGORY","Document category and covered period are required");
        String media;
        try { media=extractor.detect(bytes); } catch(IllegalArgumentException ex) { throw LoanException.invalid("INVALID_DOCUMENT","File type, size or image resource limits failed validation"); }
        String checksum=IdempotencyService.hash(bytes);
        UUID id=transactions.transaction(() -> idempotency.execute(actor,"UPLOAD_DOCUMENT",application.toString(),key,Map.of("metadata",metadata,"checksum",checksum,"expectedVersion",expected),() -> {
            var app=access.application(actor,application,true); access.expected(app.getVersion(),expected);
            if(!app.getState().editable() || !app.getApplicant().equals(actor.subject())) throw LoanException.conflict("NOT_EDITABLE","Documents may only be added to an editable application");
            if(metadata.supersedes()!=null) documents.findByIdAndApplicationId(metadata.supersedes(),application).orElseThrow(LoanException::missing);
            app.edit(app.getDraft()); // Invalidates any previous assessment before new evidence arrives.
            var doc=documents.saveAndFlush(DocumentRecord.pending(application,app.getCurrentRevision()+1,metadata.category(),checksum,media,bytes.length,
                metadata.periodStart(),metadata.periodEnd(),metadata.supersedes(),Instant.now().plus(config.getRetentionDays(),java.time.temporal.ChronoUnit.DAYS)));
            audit.record(actor,"DOCUMENT_UPLOAD_PENDING",application,app.getCurrentRevision(),doc.getId()); return doc.getId();
        }));
        var snapshot=transactions.transaction(() -> documents.findById(id).orElseThrow(LoanException::missing));
        if(snapshot.getUploadStatus().equals("COMPLETE")) return view(actor,snapshot);
        // No open transaction or application lock across object storage calls.
        storage.put(snapshot.getObjectKey(),bytes,new DocumentStorage.Metadata(checksum,bytes.length,media));
        var head=storage.head(snapshot.getObjectKey());
        if(!head.checksum().equals(checksum)||head.size()!=bytes.length||!head.mediaType().equals(media)) throw LoanException.unavailable("UPLOAD_VERIFICATION_FAILED");
        return transactions.transaction(() -> {
            access.application(actor,application,true);
            var doc=documents.findById(id).orElseThrow(LoanException::missing);
            if(!doc.getUploadStatus().equals("COMPLETE")) {
                doc.completeUpload(); transactions.enqueue("DOCUMENT",id,doc.getUploadedForRevision());
                audit.record(actor,"DOCUMENT_UPLOADED",application,doc.getUploadedForRevision(),id);
            }
            return view(actor,doc);
        });
    }
    public List<DocumentView> list(Actor actor,UUID app) {
        return transactions.transaction(() -> { access.application(actor,app,false); return documents.findByApplicationIdOrderByUploadedAtAsc(app).stream().map(d->view(actor,d)).toList(); });
    }
    public DocumentView get(Actor actor,UUID app,UUID id) {
        return transactions.transaction(() -> { access.application(actor,app,false); return view(actor,documents.findByIdAndApplicationId(id,app).orElseThrow(LoanException::missing)); });
    }
    public record Content(byte[] bytes,String mediaType) {}
    public Content content(Actor actor,UUID app,UUID id) {
        var doc=transactions.transaction(() -> {
            var application=access.application(actor,app,false); var d=documents.findByIdAndApplicationId(id,app).orElseThrow(LoanException::missing);
            if(!d.getUploadStatus().equals("COMPLETE") || !d.getScanStatus().equals("CLEAN")) throw LoanException.conflict("DOCUMENT_QUARANTINED","Document is not available for download before a successful security scan");
            audit.record(actor,"DOCUMENT_CONTENT_ACCESSED",app,application.getCurrentRevision(),id); return d;
        });
        return new Content(storage.get(doc.getObjectKey()),doc.getMediaType());
    }
    private DocumentView view(Actor actor,DocumentRecord d) {
        return new DocumentView(d.getId(),d.getCategory(),d.getUploadedForRevision(),d.getMediaType(),d.getSizeBytes(),d.getChecksum(),d.getPeriodStart(),d.getPeriodEnd(),
            d.getSupersedes(),d.getUploadStatus(),d.getScanStatus(),d.getProcessingStatus(),actor.has("LOAN_OFFICER")&&d.getEvidence()!=null?json.read(d.getEvidence(),FinancialEvidence.class):null);
    }
}

