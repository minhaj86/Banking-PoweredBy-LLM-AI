package com.example.loans.config;

import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.*;
import org.springframework.context.annotation.*;

@Configuration
public class OpenApiConfig {
    @Bean OpenAPI api() {
        return new OpenAPI().info(new Info().title("Placeholder Bank - Loan Service").version("v1").description(
            "Placeholder Bank loan origination API. JWT roles and tenant are required. Money is a decimal string. If-Match contains the current numeric version. Creation, submission, decisions, uploads and retries require Idempotency-Key. 202 means asynchronous processing is accepted: poll processing-status or loan provisioningStatus. Errors use application/problem+json with stable code and correlationId. Synthetic providers in local profile are demonstrations only."))
            .components(new Components().addSecuritySchemes("bearer",new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
            .addSecurityItem(new SecurityRequirement().addList("bearer"));
    }
}

