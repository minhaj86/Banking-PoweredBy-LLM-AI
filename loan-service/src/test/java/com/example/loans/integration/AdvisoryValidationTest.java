package com.example.loans.integration;

import com.example.loans.integration.ai.*;
import com.example.loans.document.FinancialEvidence.*;
import com.example.loans.mapper.Json;
import com.example.loans.config.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.*;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.prompt.Prompt;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AdvisoryValidationTest {
    @Test void hallucinatedEvidenceAndUnsupportedFavourableOutcomeAreRejected() {
        var source=new Source(UUID.randomUUID(),1,1,"net_income=30000.00 SEK MONTHLY");
        var fact=new Fact("net_income","30000.00","SEK","MONTHLY",LocalDate.now(),LocalDate.now(),Provenance.DOCUMENT_EXTRACTED,source);
        var input=new CreditAssessmentAiService.Input(Map.of(),List.of(fact),List.of("MISSING_INFORMATION"),List.of());
        var output=new CreditAssessmentAiService.Advisory(CreditAssessmentAiService.Outcome.SUPPORTS_REVIEW_FOR_APPROVAL,
            List.of(new CreditAssessmentAiService.Finding(CreditAssessmentAiService.FindingCode.INCOME_SUPPORTED,List.of(new Source(UUID.randomUUID(),1,1,source.excerpt())))),
            List.of(),List.of(),List.of(CreditAssessmentAiService.Limitation.ADVISORY_ONLY,CreditAssessmentAiService.Limitation.EXTRACTION_NOT_AUTHENTICATION),List.of());
        assertThatThrownBy(()->new AdvisoryValidator().validate(input,output)).hasMessageContaining("validation");
    }
    @Test void malformedRefusalAndTimeoutStayTechnicalFailures() {
        var model=mock(ChatModel.class);var p=new LoanProperties();var json=new Json(JsonMapper.builder().findAndAddModules().build());
        var service=new SpringAiAssessmentService(model,"test","operator-selected",json,new AdvisoryValidator(),new ProviderGuard(p));
        var input=new CreditAssessmentAiService.Input(Map.of(),List.of(),List.of(),List.of());
        when(model.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("I refuse")))));
        assertThatThrownBy(()->service.assess(input)).hasMessageContaining("schema");
        when(model.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of()));
        assertThatThrownBy(()->service.assess(input)).hasMessageContaining("no usable");
        when(model.call(any(Prompt.class))).thenThrow(new org.springframework.web.client.ResourceAccessException("timeout"));
        assertThatThrownBy(()->service.assess(input)).isInstanceOf(org.springframework.web.client.ResourceAccessException.class);
    }
}

