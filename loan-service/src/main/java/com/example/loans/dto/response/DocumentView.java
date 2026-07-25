package com.example.loans.dto.response;
import com.example.loans.document.FinancialEvidence;
import java.time.*;
import java.util.UUID;
public record DocumentView(UUID id,String category,int uploadedForRevision,String mediaType,long sizeBytes,String checksum,
    LocalDate periodStart,LocalDate periodEnd,UUID supersedes,String uploadStatus,String scanStatus,String processingStatus,FinancialEvidence evidence) {}

