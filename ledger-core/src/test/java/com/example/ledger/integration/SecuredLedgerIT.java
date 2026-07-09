package com.example.ledger.integration;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

@ActiveProfiles("secured")
@org.springframework.test.context.TestPropertySource(properties="spring.security.oauth2.resourceserver.jwt.issuer-uri=https://issuer.invalid")
class SecuredLedgerIT extends PostgresIntegrationTest {
    @MockitoBean JwtDecoder decoder;
    @Test void onlyServiceTokensCanUseTheSecuredLedgerApi() throws Exception {
        var ledger=ledger(); var path="/api/v1/ledgers/"+ledger.id();
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        mvc.perform(get(path).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_APPLICANT")))).andExpect(status().isForbidden());
        mvc.perform(get(path).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_SERVICE")))).andExpect(status().isOk());
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}
