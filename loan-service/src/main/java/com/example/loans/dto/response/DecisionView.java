package com.example.loans.dto.response;
import java.util.UUID;
public record DecisionView(UUID decisionId,String outcome,UUID loanId,String applicantExplanation) {}

