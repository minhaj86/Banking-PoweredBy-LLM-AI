package com.example.loans;

import com.example.loans.config.LoanProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(LoanProperties.class)
public class LoanApplication {
    public static void main(String[] args) { SpringApplication.run(LoanApplication.class, args); }
}
