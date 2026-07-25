package com.example.loans.dto.response;

import com.example.loans.domain.enums.ApplicationState;
import com.example.loans.dto.request.ApplicationInput;
import java.time.Instant;
import java.util.UUID;

public record ApplicationView(UUID id,long version,ApplicationState state,int revision,ApplicationInput application,
        UUID loanId,String decisionExplanation,Instant updatedAt) {}

