package com.example.bff.config;
import lombok.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import java.time.Duration;
@Getter @Setter @ConfigurationProperties("bff")
public class BffProperties {
    private String origin="http://localhost:18082";
    private String issuer="http://localhost:18082/auth/realms/loans";
    private String internalIssuer="http://keycloak:8080/auth/realms/loans";
    private String clientId="loan-bff";
    private String clientSecret;
    private String encryptionKey;
    private String loanUrl="http://loans:8081";
    private String staffAcr="loan-staff";
    private Duration maximumLifetime=Duration.ofHours(8);
    private Duration downstreamTimeout=Duration.ofSeconds(35);
    private boolean secureCookie=true;
    private String instanceId="bff";
}

