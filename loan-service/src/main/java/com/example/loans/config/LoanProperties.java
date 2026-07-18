package com.example.loans.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.*;

@Getter @Setter
@ConfigurationProperties("loan")
public class LoanProperties {
    private String encryptionKey;
    private String localJwtSecret;
    private String audience = "loan-service";
    private String issuer;
    private String jwkSetUri;
    private boolean syntheticVerification = false;
    private boolean workersEnabled = true;
    private boolean retentionEnabled = false;
    private int retentionDays = 365;
    private int maxQueuedJobs = 10000;
    private int maxAttempts = 4;
    private Duration lease = Duration.ofMinutes(5);
    private Duration providerTimeout = Duration.ofSeconds(30);
    private int documentConcurrency = 2;
    private int aiConcurrency = 2;
    private int provisioningConcurrency = 2;
    private long maxFileBytes = 10485760;
    private int maxPages = 20;
    private String scannerHost;
    private int scannerPort = 3310;
    private final Product product = new Product();
    private final Ai ai = new Ai();
    private final Storage storage = new Storage();
    private final Ledger ledger = new Ledger();

    @Getter @Setter public static class Product {
        private String id = "personal-sek";
        private int version = 1;
        private String policyVersion = "demo-1";
        private boolean eligible = true;
        private BigDecimal minAmount = new BigDecimal("1000.00");
        private BigDecimal maxAmount = new BigDecimal("300000.00");
        private int minMonths = 3;
        private int maxMonths = 120;
        private BigDecimal nominalAnnualRate = new BigDecimal("0.080000");
        private BigDecimal stressAnnualRate = new BigDecimal("0.110000");
        private BigDecimal minimumResidual = new BigDecimal("5000.00");
        private BigDecimal maximumDsti = new BigDecimal("0.400000");
        private int evidenceFreshnessDays = 120;
        private int assessmentValidityDays = 30;
        private int minimumAge = 18;
        private int requiredPayslips = 1;
        private Set<String> requiredCategories = new LinkedHashSet<>(List.of("PAYSLIP", "TAX_ASSESSMENT"));
        private Set<String> underwritingInputs = new LinkedHashSet<>(List.of("monthlyNetIncome", "monthlyNonDebtExpenses", "existingMonthlyDebtRepayments", "proposedMonthlyInstalment", "residualIncome", "debtServiceToIncome"));
    }
    @Getter @Setter public static class Ai {
        private String provider = "mock";
        private String model;
        private String endpoint;
        private String apiKey;
        private boolean dataHandlingApproved = false;
        private int requestsPerMinute = 60;
    }
    @Getter @Setter public static class Storage {
        private String endpoint;
        private String region = "us-east-1";
        private String bucket = "loan-evidence";
        private String accessKey;
        private String secretKey;
    }
    @Getter @Setter public static class Ledger {
        private String url = "http://localhost:8080";
        private UUID id;
        private String bearerToken;
        private String tokenUri;
        private String clientId;
        private String clientSecret;
    }
}
