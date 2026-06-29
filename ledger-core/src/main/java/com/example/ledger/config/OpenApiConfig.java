package com.example.ledger.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.responses.ApiResponse;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.*;
import java.util.*;

@Configuration
public class OpenApiConfig {
    private static final String POST_EXAMPLE = """
            {"externalReference":"customer-deposit-001","description":"Customer deposits 1000 SEK",
             "entries":[{"accountId":"00000000-0000-0000-0000-000000000001","side":"DEBIT","amount":"1000.00"},
                        {"accountId":"00000000-0000-0000-0000-000000000002","side":"CREDIT","amount":"1000.00"}]}
            """;
    private static final String RESPONSE_EXAMPLE = """
            {"id":"00000000-0000-0000-0000-000000000003","ledgerId":"00000000-0000-0000-0000-000000000004",
             "currency":"SEK","externalReference":"customer-deposit-001","description":"Customer deposits 1000 SEK",
             "recordedAt":"2026-10-27T12:00:00Z","reversalOfTransactionId":null,"reversalReason":null,
             "entries":[{"id":"00000000-0000-0000-0000-000000000005","accountId":"00000000-0000-0000-0000-000000000001",
                         "lineNumber":1,"side":"DEBIT","amount":"1000.00","description":null},
                        {"id":"00000000-0000-0000-0000-000000000006","accountId":"00000000-0000-0000-0000-000000000002",
                         "lineNumber":2,"side":"CREDIT","amount":"1000.00","description":null}]}
            """;

    @Bean
    OpenAPI ledgerOpenApi() {
        return new OpenAPI().info(new Info().title("Placeholder Bank - Ledger Core").version("v1")
                .description("Placeholder Bank posted double-entry journals from the bank/business perspective. "
                        + "SEK/EUR/USD; immutable journal; the IAM profile validates JWTs and the legacy local profile is unauthenticated. "
                        + "All money uses decimal strings. No pending payments, holds or FX."));
    }

    @Bean
    OpenApiCustomizer examplesAndErrors(ObjectMapper mapper) throws JsonProcessingException {
        var requestExample = mapper.readTree(POST_EXAMPLE);
        var responseExample = mapper.readTree(RESPONSE_EXAMPLE);
        return api -> api.getPaths().forEach((path, item) -> item.readOperationsMap().forEach((method, operation) -> {
            if (method == PathItem.HttpMethod.POST && !path.endsWith("/close")) {
                var generatedResponse = operation.getResponses().remove("200");
                if (generatedResponse != null) {
                    operation.getResponses().addApiResponse("201", generatedResponse.description("Created; successful journal replays also return 201"));
                }
            }
            Map<String, String> errors = Map.of(
                    "400", "MALFORMED_REQUEST", "404", "RESOURCE_NOT_FOUND",
                    "409", "IDEMPOTENCY_CONFLICT", "422", "UNBALANCED_TRANSACTION",
                    "503", "DATABASE_RETRY_EXHAUSTED");
            errors.forEach((status, code) -> operation.getResponses().addApiResponse(status,
                    new ApiResponse().description(switch (status) {
                        case "400" -> "Malformed input or invalid parameter";
                        case "404" -> "Resource missing in the requested ledger";
                        case "409" -> "Idempotency, duplicate code, reversal, or closure conflict";
                        case "422" -> "Accounting rule, account status, money, or protected balance violation";
                        default -> "Database unavailable or transient retry budget exhausted";
                    }).content(new Content().addMediaType("application/problem+json", new MediaType()
                            .example(Map.of("type", "urn:ledger:error:" + code.toLowerCase(Locale.ROOT),
                                    "title", "Request failed", "status", Integer.parseInt(status), "code", code,
                                    "message", "The request could not be completed",
                                    "correlationId", "e6474d7a-7d2b-4cb6-820f-8b40f526d8a2"))))));
            if (method == PathItem.HttpMethod.POST && path.endsWith("/transactions")) {
                operation.getRequestBody().getContent().get("application/json")
                        .addExamples("deposit", new Example().value(requestExample));
                operation.getResponses().addApiResponse("201", new ApiResponse().description("Committed result, including replays")
                        .content(new Content().addMediaType("application/json", new MediaType()
                                .schema(new Schema<>().$ref("#/components/schemas/TransactionResponse"))
                                .addExamples("deposit", new Example().value(responseExample)))));
            }
        }));
    }
}
