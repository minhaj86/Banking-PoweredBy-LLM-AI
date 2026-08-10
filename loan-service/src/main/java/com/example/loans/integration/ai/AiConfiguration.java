package com.example.loans.integration.ai;

import com.example.loans.config.*;
import com.example.loans.exception.LoanException;
import com.example.loans.mapper.Json;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.openai.*;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.ollama.*;
import org.springframework.ai.ollama.api.*;
import org.springframework.context.annotation.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.RestClient;
import java.net.http.HttpClient;
import java.util.*;

@Configuration
public class AiConfiguration {
    @Bean CreditAssessmentAiService ai(LoanProperties p,Json json,AdvisoryValidator validator,ProviderGuard guard) {
        String provider=p.getAi().getProvider();
        if(provider.equals("mock")) return new MockCreditAssessmentAiService(validator);
        var factory=new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(p.getProviderTimeout()).build());
        factory.setReadTimeout(p.getProviderTimeout());
        var rest=RestClient.builder().requestFactory(factory);
        ChatModel model;
        var retry=RetryTemplate.builder().maxAttempts(1).build(); // Durable jobs own the bounded retry budget.
        if(provider.equals("openai")) {
            if(p.getAi().getApiKey()==null) throw new IllegalStateException("AI API key is required for OpenAI");
            model=OpenAiChatModel.builder().openAiApi(OpenAiApi.builder().baseUrl(p.getAi().getEndpoint()).apiKey(p.getAi().getApiKey()).restClientBuilder(rest).build())
                .defaultOptions(OpenAiChatOptions.builder().model(p.getAi().getModel()).internalToolExecutionEnabled(false).build()).retryTemplate(retry).build();
        } else if(provider.equals("ollama")) {
            model=OllamaChatModel.builder().ollamaApi(OllamaApi.builder().baseUrl(p.getAi().getEndpoint()).restClientBuilder(rest).build())
                .defaultOptions(OllamaChatOptions.builder().model(p.getAi().getModel()).internalToolExecutionEnabled(false).build()).retryTemplate(retry).build();
        } else throw new IllegalStateException("Unsupported AI provider");
        return new SpringAiAssessmentService(model,provider,p.getAi().getModel(),json,validator,guard);
    }
}

