package com.example.ledger.integration;

import com.example.ledger.domain.enums.*;
import com.example.ledger.dto.request.*;
import com.example.ledger.dto.response.*;
import com.example.ledger.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import java.util.List;
import java.util.UUID;

@SpringBootTest
@AutoConfigureMockMvc
abstract class PostgresIntegrationTest {
    // Shared for this test JVM; Ryuk owns cleanup. No disabledWithoutDocker or H2 fallback.
    @SuppressWarnings("resource") // Ryuk owns the shared container for the lifetime of this test JVM.
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.10-alpine")
            .withDatabaseName("ledger").withUsername("bootstrap").withPassword("test-bootstrap-only")
            .withInitScript("init-test.sql");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        POSTGRES.start();
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> "ledger_app");
        registry.add("spring.datasource.password", () -> "test-app-only");
        registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user", () -> "ledger_migrator");
        registry.add("spring.flyway.password", () -> "test-migration-only");
        registry.add("logging.structured.format.console", () -> "");
    }

    @Autowired LedgerService ledgers;
    @Autowired AccountService accounts;
    @Autowired JournalService journals;
    @Autowired ReportingService reports;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired com.fasterxml.jackson.databind.ObjectMapper json;

    LedgerResponse ledger() {
        return ledgers.create(new CreateLedgerRequest("L-" + UUID.randomUUID(), "Test ledger", LedgerCurrency.SEK));
    }

    AccountResponse account(UUID ledgerId, AccountType type, boolean protectedBalance) {
        return accounts.create(ledgerId, new CreateAccountRequest("A-" + UUID.randomUUID(), type.name(), type, protectedBalance));
    }

    EntryRequest entry(UUID account, Side side, String amount) { return new EntryRequest(account, side, amount, null); }

    PostTransactionRequest transfer(UUID debit, UUID credit, String amount) {
        return new PostTransactionRequest(null, "Transfer", List.of(entry(debit, Side.DEBIT, amount), entry(credit, Side.CREDIT, amount)));
    }

    TransactionResponse post(UUID ledger, PostTransactionRequest request) { return journals.post(ledger, UUID.randomUUID().toString(), request); }

    void balance(UUID ledger, UUID account, String expected) {
        org.assertj.core.api.Assertions.assertThat(reports.balance(ledger, account).balance()).isEqualTo(expected);
    }

    long transactions(UUID ledger) {
        return jdbc.queryForObject("SELECT count(*) FROM ledger.journal_transaction WHERE ledger_id = ?", Long.class, ledger);
    }
}
