package com.example.loans.integration.ledger;

import com.example.loans.config.LoanProperties;
import org.springframework.security.oauth2.client.*;
import org.springframework.security.oauth2.client.registration.*;
import org.springframework.security.oauth2.core.*;

/** Separate service principal: never used for user-driven loan API calls. */
final class MachineCredentials {
    private final AuthorizedClientServiceOAuth2AuthorizedClientManager manager;
    MachineCredentials(LoanProperties.Ledger p) {
        var registration=ClientRegistration.withRegistrationId("ledger").clientId(p.getClientId()).clientSecret(p.getClientSecret())
            .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS).clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC).tokenUri(p.getTokenUri()).build();
        var registrations=new InMemoryClientRegistrationRepository(registration);
        manager=new AuthorizedClientServiceOAuth2AuthorizedClientManager(registrations,new InMemoryOAuth2AuthorizedClientService(registrations));
        manager.setAuthorizedClientProvider(OAuth2AuthorizedClientProviderBuilder.builder().clientCredentials().build());
    }
    synchronized String token(){var client=manager.authorize(OAuth2AuthorizeRequest.withClientRegistrationId("ledger").principal("loan-service").build());if(client==null)throw new IllegalStateException("Machine authentication unavailable");return client.getAccessToken().getTokenValue();}
}
