package com.example.bff.config;

import com.example.bff.security.SharedAuthorizedClients;
import org.springframework.context.annotation.*;
import org.springframework.security.oauth2.client.*;
import org.springframework.security.oauth2.client.registration.*;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.endpoint.*;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;
import java.time.Duration;
import java.util.*;

@Configuration
public class OidcConfiguration {
    @Bean ClientRegistrationRepository registrations(BffProperties p) {
        // Public issuer is validated even when discovery/token/JWK traffic uses the private network.
        var metadata=RestClient.builder().requestFactory(TokenHttp.factory()).build().get().uri(p.getInternalIssuer()+"/.well-known/openid-configuration").retrieve().body(Map.class);
        if(metadata==null||!p.getIssuer().equals(metadata.get("issuer"))) throw new IllegalStateException("OIDC discovery issuer mismatch");
        var registration=ClientRegistration.withRegistrationId("keycloak").clientId(p.getClientId()).clientSecret(p.getClientSecret())
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC).authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri(p.getOrigin()+"/login/oauth2/code/keycloak").scope("openid","profile","email")
            .authorizationUri(p.getIssuer()+"/protocol/openid-connect/auth").tokenUri(p.getInternalIssuer()+"/protocol/openid-connect/token")
            .jwkSetUri(p.getInternalIssuer()+"/protocol/openid-connect/certs").userInfoUri(p.getInternalIssuer()+"/protocol/openid-connect/userinfo")
            .userNameAttributeName("sub").issuerUri(p.getIssuer()).providerConfigurationMetadata(metadata).clientName("Loan account").build();
        return new InMemoryClientRegistrationRepository(registration);
    }
    @Bean public JwtDecoder accessTokenDecoder(BffProperties p) {
        var decoder=NimbusJwtDecoder.withJwkSetUri(p.getInternalIssuer()+"/protocol/openid-connect/certs").restOperations(new RestTemplate(TokenHttp.factory())).build();
        OAuth2TokenValidator<Jwt> audience=jwt->jwt.getAudience().contains("loan-service")?OAuth2TokenValidatorResult.success():OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(new JwtTimestampValidator(Duration.ofSeconds(5)),new JwtIssuerValidator(p.getIssuer()),audience));
        return decoder;
    }
    @Bean OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> codeTokens(){var client=new RestClientAuthorizationCodeTokenResponseClient();client.setRestClient(TokenHttp.tokens());return client;}
    @Bean DefaultOAuth2AuthorizedClientManager manager(ClientRegistrationRepository registrations,SharedAuthorizedClients repository) {
        var manager=new DefaultOAuth2AuthorizedClientManager(registrations,repository);
        var provider=new RefreshTokenOAuth2AuthorizedClientProvider();provider.setClockSkew(Duration.ofSeconds(15));
        var client=new RestClientRefreshTokenTokenResponseClient();client.setRestClient(TokenHttp.tokens());provider.setAccessTokenResponseClient(client);
        manager.setAuthorizedClientProvider(provider);return manager;
    }
}

