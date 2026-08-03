package com.example.loans.controller;

import com.example.loans.dto.response.DocumentView;
import com.example.loans.security.Actor;
import com.example.loans.service.DocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.time.LocalDate;
import java.util.*;

@RestController @RequestMapping("/api/v1/loan-applications/{id}/documents") @RequiredArgsConstructor
@org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('APPLICANT','LOAN_OFFICER','COMPLIANCE_REVIEWER','OPERATIONS')")
public class DocumentController {
    private final DocumentService documents;
    private final com.example.loans.service.ManualExtractionService manual;
    @PostMapping("/{documentId}/manual-extraction")
    public ResponseEntity<Map<String,UUID>> manual(@PathVariable UUID id,@PathVariable UUID documentId,
        @RequestHeader("If-Match") long version,@RequestHeader("Idempotency-Key") String key,
        @jakarta.validation.Valid @RequestBody com.example.loans.dto.request.ManualExtractionRequest request) {
        return ResponseEntity.accepted().body(Map.of("documentId",manual.extract(Actor.current(),id,documentId,version,key,request)));
    }
    @PostMapping(consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentView> upload(@PathVariable UUID id,@RequestHeader("If-Match") long version,@RequestHeader("Idempotency-Key") String key,
        @RequestParam String category,@RequestParam LocalDate periodStart,@RequestParam LocalDate periodEnd,@RequestParam(required=false) UUID supersedes,
        @RequestPart MultipartFile file) throws IOException {
        return ResponseEntity.accepted().body(documents.upload(Actor.current(),id,version,key,new DocumentService.UploadMetadata(category,periodStart,periodEnd,supersedes),file.getBytes()));
    }
    @GetMapping public List<DocumentView> list(@PathVariable UUID id) { return documents.list(Actor.current(),id); }
    @GetMapping("/{documentId}") public DocumentView get(@PathVariable UUID id,@PathVariable UUID documentId) { return documents.get(Actor.current(),id,documentId); }
    @GetMapping("/{documentId}/content") public ResponseEntity<byte[]> content(@PathVariable UUID id,@PathVariable UUID documentId) {
        var result=documents.content(Actor.current(),id,documentId);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(result.mediaType())).header("Content-Disposition","attachment; filename=evidence")
            .header("Cache-Control","no-store").header("X-Content-Type-Options","nosniff").body(result.bytes());
    }
}

