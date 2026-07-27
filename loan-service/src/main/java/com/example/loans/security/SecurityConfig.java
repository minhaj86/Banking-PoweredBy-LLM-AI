package com.example.loans.security;

import com.example.loans.config.LoanProperties;
import com.example.loans.exception.ProblemHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.*;
import org.springframework.security.web.SecurityFilterChain;
import javax.crypto.spec.SecretKeySpec;
import java.util.*;

@Configuration @EnableMethodSecurity
public class SecurityConfig {
    @Bean SecurityFilterChain security(HttpSecurity http, ObjectMapper json) throws Exception {
        http.csrf(c -> c.disable()).sessionManagement(c -> c.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(c -> c.requestMatchers("/actuator/health","/api/v1/products/current","/v3/api-docs/**","/swagger-ui/**").permitAll().anyRequest().authenticated())
            .oauth2ResourceServer(c -> c.jwt(j -> j.jwtAuthenticationConverter(converter()))
                .authenticationEntryPoint((req,res,e) -> {
                    res.setStatus(401); res.setContentType("application/problem+json");
                    json.writeValue(res.getOutputStream(),ProblemHandler.problem(HttpStatus.UNAUTHORIZED,"UNAUTHENTICATED","Verified bearer token required",req.getAttribute("correlationId")).getBody());
                }))
            .exceptionHandling(c -> c.accessDeniedHandler((req,res,e) -> {
                res.setStatus(403); res.setContentType("application/problem+json");
                json.writeValue(res.getOutputStream(),ProblemHandler.problem(HttpStatus.FORBIDDEN,"FORBIDDEN","Caller is not authorised",req.getAttribute("correlationId")).getBody());
            }));
        return http.build();
    }
    private JwtAuthenticationConverter converter() {
        var roles = new JwtGrantedAuthoritiesConverter(); roles.setAuthoritiesClaimName("roles"); roles.setAuthorityPrefix("ROLE_");
        var converter = new JwtAuthenticationConverter(); converter.setJwtGrantedAuthoritiesConverter(roles); return converter;
    }
    @Bean JwtDecoder decoder(LoanProperties p, Environment env) {
        NimbusJwtDecoder decoder;
        boolean local = Arrays.asList(env.getActiveProfiles()).contains("local") && !Arrays.asList(env.getActiveProfiles()).contains("iam");
        if (local) {
            if (p.getLocalJwtSecret()==null) throw new IllegalStateException("Local JWT signing key is required");
            decoder = NimbusJwtDecoder.withSecretKey(new SecretKeySpec(Base64.getDecoder().decode(p.getLocalJwtSecret()),"HmacSHA256")).macAlgorithm(MacAlgorithm.HS256).build();
        } else {
            if (p.getIssuer()==null) throw new IllegalStateException("OIDC issuer is required outside local profile");
            decoder = p.getJwkSetUri()==null || p.getJwkSetUri().isBlank()?JwtDecoders.fromIssuerLocation(p.getIssuer()):NimbusJwtDecoder.withJwkSetUri(p.getJwkSetUri()).build();
        }
        OAuth2TokenValidator<Jwt> audience = jwt -> jwt.getAudience().contains(p.getAudience()) ? OAuth2TokenValidatorResult.success() :
                OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token","Audience does not match",null));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(new JwtTimestampValidator(java.time.Duration.ofSeconds(5)),new JwtIssuerValidator(local ? "loan-local" : p.getIssuer()),audience));
        return decoder;
    }
}

