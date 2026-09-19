package com.example.bff.security;

import com.example.bff.api.*;
import com.example.bff.config.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.security.oauth2.client.registration.*;
import org.springframework.security.oauth2.core.*;
import org.springframework.session.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers=BffController.class,excludeAutoConfiguration={org.springframework.boot.autoconfigure.security.oauth2.client.OAuth2ClientAutoConfiguration.class,org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration.class,org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientWebSecurityAutoConfiguration.class}) @Import({SecurityConfiguration.class,ProblemAdvice.class,BffHttpSecurityTest.Beans.class})
class BffHttpSecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean TokenRelay relay;
    @MockitoBean BackendProxy proxy;
    @MockitoBean(name="authorizedClientRepository") SharedAuthorizedClients clients;
    @MockitoBean SharedOidcSessions registry;
    @MockitoBean SessionRepository<MapSession> sessions;
    @MockitoBean(name="OAuth2AuthorizedClientManager") org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizedClientManager manager;
    @MockitoBean org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient<org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest> codeTokens;
    @Test void unauthenticatedApiHasJson401AndNoStore()throws Exception{
        mvc.perform(get("/bff/me")).andExpect(status().isUnauthorized()).andExpect(content().contentTypeCompatibleWith("application/problem+json")).andExpect(jsonPath("$.code").value("SESSION_REQUIRED")).andExpect(header().string("Cache-Control","no-store"));
    }
    @Test void codeLoginHasStateNonceAndPkceEvenForConfidentialClient()throws Exception{
        var result=mvc.perform(get("/oauth2/authorization/keycloak")).andExpect(status().is3xxRedirection()).andReturn();String target=result.getResponse().getRedirectedUrl();
        assertThat(target).contains("code_challenge_method=S256","code_challenge=","state=","nonce=","response_type=code").doesNotContain("client_secret");
    }
    @Test void invalidStateCannotCreateAnAuthenticatedSession()throws Exception{
        mvc.perform(get("/login/oauth2/code/keycloak").param("code","untrusted-code").param("state","wrong-state")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("http://localhost:18082/?authentication=failed"));
        verifyNoInteractions(relay,proxy);
    }
    @Test void csrfCoversUploadAndLogoutAndReturnPathsCannotRedirectOffsite()throws Exception{
        mvc.perform(post("/bff/api/loan-applications/11111111-1111-4111-8111-111111111111/documents").with(oauth2Login()).contentType("multipart/form-data; boundary=test").content("--test--")).andExpect(status().isForbidden());
        mvc.perform(post("/bff/logout").with(oauth2Login())).andExpect(status().isForbidden());
        mvc.perform(get("/bff/login").param("returnTo","//evil.example")).andExpect(status().isBadRequest());verifyNoInteractions(proxy);
    }
    @Test void logoutUsesProviderClientIdWithoutAnIdTokenInTheUrl()throws Exception{
        var result=mvc.perform(post("/bff/logout").with(oauth2Login()).with(csrf())).andExpect(status().is3xxRedirection()).andReturn();
        assertThat(result.getResponse().getRedirectedUrl()).contains("client_id=loan-bff","post_logout_redirect_uri=").doesNotContain("id_token_hint","access_token","refresh_token");
    }
    @TestConfiguration static class Beans {
        @Bean ClientRegistrationRepository registrations(){return new InMemoryClientRegistrationRepository(ClientRegistration.withRegistrationId("keycloak").clientId("loan-bff").clientSecret("test-only").clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC).authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE).redirectUri("http://localhost:18082/login/oauth2/code/keycloak").scope("openid","profile","email").authorizationUri("https://issuer.example/auth").tokenUri("https://issuer.example/token").jwkSetUri("https://issuer.example/certs").issuerUri("https://issuer.example").userInfoUri("https://issuer.example/userinfo").userNameAttributeName("sub").build());}
    }
}
