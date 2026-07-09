package com.example.ledger.integration;

import com.example.ledger.domain.enums.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ApiIT extends PostgresIntegrationTest {
    @Test
    void healthAndOpenApiAreAvailableWithMoneyAndErrorExamples() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
        var result = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn();
        var document = json.readTree(result.getResponse().getContentAsString());
        assertThat(document.path("paths").size()).isEqualTo(13);
        assertThat(document.path("paths").has("/api/v1/ledgers/{ledgerId}/accounts/provision")).isTrue();
        assertThat(document.path("paths").has("/api/v1/ledgers/{ledgerId}/accounts/by-external-reference")).isTrue();
        var example = document.at("/paths/~1api~1v1~1ledgers~1{ledgerId}~1transactions/post/requestBody/content/application~1json/examples/deposit/value");
        assertThat(example.isObject()).isTrue();
        assertThat(example.at("/entries/0/amount").textValue()).isEqualTo("1000.00");
        assertThat(document.at("/paths/~1api~1v1~1ledgers/post/responses/201").isMissingNode()).isFalse();
        assertThat(document.at("/paths/~1api~1v1~1ledgers~1{ledgerId}~1transactions/post/responses/201/content/application~1json/examples/deposit").isMissingNode()).isFalse();
        assertThat(document.at("/paths/~1api~1v1~1ledgers~1{ledgerId}~1transactions/post/responses/409/content/application~1problem+json/example/code").asText())
                .isEqualTo("IDEMPOTENCY_CONFLICT");
    }

    @Test
    void httpPostingReplayConflictAndCorrelationUseConsistentStatuses() throws Exception {
        var ledger = ledger();
        var a = account(ledger.id(), AccountType.ASSET, false);
        var b = account(ledger.id(), AccountType.LIABILITY, true);
        var path = "/api/v1/ledgers/" + ledger.id() + "/transactions";
        var body = json.writeValueAsString(transfer(a.id(), b.id(), "100"));
        var first = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path).contentType("application/json").header("Idempotency-Key", "http")
                        .header("X-Correlation-ID", "test-correlation").content(body))
                .andExpect(status().isCreated()).andExpect(header().string("X-Correlation-ID", "test-correlation"))
                .andExpect(jsonPath("$.entries[0].amount").value("100.00")).andReturn();
        var replay = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path).contentType("application/json").header("Idempotency-Key", "http").content(body))
                .andExpect(status().isCreated()).andReturn();
        assertThat(replay.getResponse().getContentAsString()).isEqualTo(first.getResponse().getContentAsString());
        assertThat(replay.getResponse().getHeader("Location")).isEqualTo(first.getResponse().getHeader("Location"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path).contentType("application/json").header("Idempotency-Key", "http")
                        .header("X-Correlation-ID", "conflict-id").content(json.writeValueAsString(transfer(a.id(), b.id(), "101"))))
                .andExpect(status().isConflict()).andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"))
                .andExpect(jsonPath("$.correlationId").value("conflict-id")).andExpect(jsonPath("$.message").isString());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path).contentType("application/json").content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path).contentType("application/json").header("Idempotency-Key", "not valid").content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_IDEMPOTENCY_KEY"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"12.5", "null", "true", "{}"})
    void amountMustBeAJsonString(String token) throws Exception {
        var ledger = ledger();
        var a = account(ledger.id(), AccountType.ASSET, false);
        var b = account(ledger.id(), AccountType.LIABILITY, false);
        String body = """
                {"description":"Bad amount","entries":[
                  {"accountId":"%s","side":"DEBIT","amount":%s},
                  {"accountId":"%s","side":"CREDIT","amount":"12.50"}]}
                """.formatted(a.id(), token, b.id());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/ledgers/" + ledger.id() + "/transactions").contentType("application/json")
                        .header("Idempotency-Key", "money").content(body))
                .andExpect(status().isBadRequest());
        assertThat(transactions(ledger.id())).isZero();
    }

    @Test
    void malformedCurrencyMoneyAndUnknownFieldsAreRejected() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/ledgers").contentType("application/json")
                .content("{\"code\":\"GBP\",\"name\":\"Unsupported\",\"currency\":\"GBP\"}")).andExpect(status().isBadRequest());
        var ledger = ledger();
        var a = account(ledger.id(), AccountType.ASSET, false);
        var b = account(ledger.id(), AccountType.LIABILITY, false);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/ledgers/" + ledger.id() + "/transactions").contentType("application/json")
                        .header("Idempotency-Key", "bad-precision").content(json.writeValueAsString(transfer(a.id(), b.id(), "1.001"))))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("INVALID_AMOUNT"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/ledgers").contentType("application/json")
                .content("{\"code\":\"X\",\"name\":\"X\",\"currency\":\"SEK\",\"balance\":\"10.00\"}")).andExpect(status().isBadRequest());
    }

    @Test
    void everyNestedLookupChecksLedgerOwnership() throws Exception {
        var ledger = ledger();
        var other = ledger();
        var a = account(ledger.id(), AccountType.ASSET, false);
        var b = account(ledger.id(), AccountType.LIABILITY, false);
        var tx = post(ledger.id(), transfer(a.id(), b.id(), "1"));
        String path = "/api/v1/ledgers/" + other.id() + "/accounts/" + a.id();
        for (String suffix : new String[]{"", "/balance", "/entries"}) {
            mvc.perform(get(path + suffix)).andExpect(status().isNotFound());
        }
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path + "/close")).andExpect(status().isNotFound());
        String transactions = "/api/v1/ledgers/" + other.id() + "/transactions/" + tx.id();
        mvc.perform(get(transactions)).andExpect(status().isNotFound());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(transactions + "/reversals").contentType("application/json")
                .header("Idempotency-Key", "wrong-ledger").content("{\"reason\":\"Correction\"}")).andExpect(status().isNotFound());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/ledgers/" + UUID.randomUUID() + "/accounts").contentType("application/json")
                .content("{\"code\":\"X\",\"name\":\"X\",\"type\":\"ASSET\"}")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/ledgers/" + UUID.randomUUID() + "/trial-balance")).andExpect(status().isNotFound());
    }

    @Test
    void rejectsInvalidHistoryRangesAndReversalPayloads() throws Exception {
        var ledger = ledger();
        var account = account(ledger.id(), AccountType.ASSET, false);
        var path = "/api/v1/ledgers/" + ledger.id() + "/accounts/" + account.id() + "/entries";
        mvc.perform(get(path).param("size", "201")).andExpect(status().isBadRequest());
        mvc.perform(get(path).param("page", "-1")).andExpect(status().isBadRequest());
        mvc.perform(get(path).param("from", "2026-01-01T00:00:00Z").param("to", "2026-01-01T00:00:00Z"))
                .andExpect(status().isBadRequest());
        mvc.perform(get(path).param("from", "not-a-timestamp")).andExpect(status().isBadRequest());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/ledgers/" + ledger.id() + "/transactions/" + UUID.randomUUID() + "/reversals")
                .contentType("application/json").header("Idempotency-Key", "partial")
                .content("{\"reason\":\"partial\",\"amount\":\"1.00\"}")).andExpect(status().isBadRequest());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/ledgers/" + ledger.id() + "/transactions/" + UUID.randomUUID() + "/reversals")
                .contentType("application/json").header("Idempotency-Key", "blank")
                .content("{\"reason\":\" \"}")).andExpect(status().isBadRequest());
    }
}
