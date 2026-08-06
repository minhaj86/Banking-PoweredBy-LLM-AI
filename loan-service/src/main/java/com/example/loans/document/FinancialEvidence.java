package com.example.loans.document;

import java.time.LocalDate;
import java.util.*;

public record FinancialEvidence(List<Fact> facts,List<String> limitations) {
    public enum Provenance { APPLICANT_DECLARED, DOCUMENT_EXTRACTED, REVIEWER_CONFIRMED, EXTERNALLY_VERIFIED }
    public record Source(UUID documentId,int page,int line,String excerpt) {}
    public record Fact(String field,String amount,String currency,String frequency,LocalDate periodStart,LocalDate periodEnd,Provenance provenance,Source source) {}
}

