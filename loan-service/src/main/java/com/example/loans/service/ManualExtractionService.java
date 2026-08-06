package com.example.loans.service;

import com.example.loans.config.LoanProperties;
import com.example.loans.document.FinancialEvidence;
import com.example.loans.domain.entity.DocumentRecord;
import com.example.loans.domain.repository.*;
import com.example.loans.domain.valueobject.Decimals;
import com.example.loans.dto.request.ManualExtractionRequest;
import com.example.loans.exception.LoanException;
import com.example.loans.integration.storage.DocumentStorage;
import com.example.loans.mapper.Json;
import com.example.loans.security.Actor;
import com.example.loans.workflow.JobQueue;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;
import java.time.Instant;

@Service @RequiredArgsConstructor
public class ManualExtractionService {
    private final ResourceAccess access;
    private final DocumentRepository documents;
    private final DocumentStorage storage;
    private final IdempotencyService idempotency;
    private final AuditService audit;
    private final JobQueue jobs;
    private final LoanProperties config;
    private final Json json;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    private final com.example.loans.security.SensitiveTextConverter encryption;
    public UUID extract(Actor actor,UUID application,UUID source,long expected,String key,ManualExtractionRequest request) {
        actor.require("LOAN_OFFICER");
        UUID id=jobs.transaction(()->idempotency.execute(actor,"MANUAL_EXTRACTION",source.toString(),key,Map.of("request",request,"version",expected),()->{
            var app=access.application(actor,application,true); access.expected(app.getVersion(),expected);
            access.independentReviewer(actor,app);
            if(!app.getState().editable()) throw LoanException.conflict("NOT_EDITABLE","Manual evidence requires an editable application and a new submission");
            var original=documents.findByIdAndApplicationId(source,application).orElseThrow(LoanException::missing);
            if(!original.getScanStatus().equals("CLEAN")||!original.getUploadStatus().equals("COMPLETE")) throw LoanException.conflict("DOCUMENT_QUARANTINED","Manual extraction cannot bypass the malware gate");
            request.facts().forEach(f->{Decimals.money(f.amount()); if(f.page()>config.getMaxPages()||(!original.getMediaType().equals("application/pdf")&&f.page()!=1)) throw LoanException.invalid("INVALID_EVIDENCE_PAGE","Manual evidence page is outside document limits");});
            app.edit(app.getDraft());
            var replacement=DocumentRecord.pending(application,app.getCurrentRevision()+1,original.getCategory(),original.getChecksum(),original.getMediaType(),
                original.getSizeBytes(),original.getPeriodStart(),original.getPeriodEnd(),source,original.getExpiresAt());
            documents.saveAndFlush(replacement);
            jdbc.update("INSERT INTO origination.manual_extraction_review(id,application_id,revision,source_document,output_document,reviewer,explanation) VALUES(?,?,?,?,?,?,?)",
                UUID.randomUUID(),application,replacement.getUploadedForRevision(),source,replacement.getId(),actor.subject(),encryption.convertToDatabaseColumn(request.reason()));
            audit.record(actor,"MANUAL_EXTRACTION_PENDING",application,app.getCurrentRevision()+1,replacement.getId()); return replacement.getId();
        }));
        var replacement=jobs.transaction(()->documents.findById(id).orElseThrow());
        if(replacement.getUploadStatus().equals("COMPLETE")) return id;
        var original=jobs.transaction(()->documents.findByIdAndApplicationId(source,application).orElseThrow());
        byte[] bytes=storage.get(original.getObjectKey());
        if(!IdempotencyService.hash(bytes).equals(original.getChecksum())) throw LoanException.invalid("OBJECT_CHECKSUM_MISMATCH","Manual evidence source changed");
        if(original.getMediaType().equals("application/pdf")) {
            try(var pdf=org.apache.pdfbox.Loader.loadPDF(bytes)) {
                if(request.facts().stream().anyMatch(f->f.page()>pdf.getNumberOfPages())) throw LoanException.invalid("INVALID_EVIDENCE_PAGE","Manual page is absent from source document");
            } catch(java.io.IOException ex) { throw LoanException.invalid("MALFORMED_DOCUMENT","Manual evidence requires a readable document"); }
        }
        storage.put(replacement.getObjectKey(),bytes,new DocumentStorage.Metadata(original.getChecksum(),bytes.length,original.getMediaType()));
        var metadata=storage.head(replacement.getObjectKey());
        if(!metadata.checksum().equals(original.getChecksum())||metadata.size()!=bytes.length) throw LoanException.unavailable("UPLOAD_VERIFICATION_FAILED");
        jobs.transaction(()->{
            access.application(actor,application,true); var current=documents.findById(id).orElseThrow();
            if(!current.getUploadStatus().equals("COMPLETE")) {
                var facts=request.facts().stream().map(f->new FinancialEvidence.Fact(f.field(),Decimals.money(f.amount()).toPlainString(),f.currency(),f.frequency(),
                    original.getPeriodStart(),original.getPeriodEnd(),FinancialEvidence.Provenance.REVIEWER_CONFIRMED,
                    new FinancialEvidence.Source(id,f.page(),f.line(),f.field()+"="+Decimals.money(f.amount()).toPlainString()+" "+f.currency()+" "+f.frequency()))).toList();
                current.completeUpload(); current.processed("CLEAN","EXTRACTED",json.write(new FinancialEvidence(facts,List.of("REVIEWER_ENTERED_FINANCIAL_FACTS","EXTRACTION_DOES_NOT_VERIFY_AUTHENTICITY"))));
                audit.record(actor,"MANUAL_EXTRACTION_CONFIRMED",application,current.getUploadedForRevision(),id);
            }
            return null;
        });
        return id;
    }
}

