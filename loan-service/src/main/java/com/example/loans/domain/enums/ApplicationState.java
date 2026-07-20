package com.example.loans.domain.enums;

public enum ApplicationState {
    DRAFT, SUBMITTED, ASSESSMENT_PENDING, DOCUMENTS_REQUIRED, MANUAL_REVIEW, ASSESSMENT_FAILED, APPROVED, REJECTED, WITHDRAWN;
    public boolean editable() { return this == DRAFT || this == DOCUMENTS_REQUIRED || this == MANUAL_REVIEW || this == ASSESSMENT_FAILED; }
    public boolean terminal() { return this == APPROVED || this == REJECTED || this == WITHDRAWN; }
}
