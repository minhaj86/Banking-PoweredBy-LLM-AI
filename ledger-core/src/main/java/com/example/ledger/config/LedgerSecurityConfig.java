package com.example.ledger.config;

import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.*;
import org.springframework.security.web.SecurityFilterChain;
import java.util.Arrays;

/** Only the explicit local development profile preserves the legacy unauthenticated API. */
@Configuration
public class LedgerSecurityConfig {
    @Bean SecurityFilterChain ledgerSecurity(HttpSecurity http,Environment environment) throws Exception {
        http.csrf(c->c.disable()).sessionManagement(c->c.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        if(!environment.acceptsProfiles(org.springframework.core.env.Profiles.of("local"))) {
            var authorities=new JwtGrantedAuthoritiesConverter(); authorities.setAuthoritiesClaimName("roles"); authorities.setAuthorityPrefix("ROLE_");
            var converter=new JwtAuthenticationConverter(); converter.setJwtGrantedAuthoritiesConverter(authorities);
            boolean iam=environment.acceptsProfiles(org.springframework.core.env.Profiles.of("iam"));
            http.authorizeHttpRequests(c->{c.requestMatchers("/actuator/health").permitAll();
                if(iam){c.requestMatchers(org.springframework.http.HttpMethod.GET,"/api/v1/**").hasAnyRole("LEDGER_READER","LEDGER_ADMIN")
                    .requestMatchers(org.springframework.http.HttpMethod.POST,"/api/v1/ledgers/*/accounts/provision").hasAnyRole("ACCOUNT_PROVISIONER","LEDGER_ADMIN")
                    .anyRequest().hasRole("LEDGER_ADMIN");}
                else c.anyRequest().hasRole("SERVICE");})
                .oauth2ResourceServer(c->c.jwt(j->j.jwtAuthenticationConverter(converter)));
        } else http.authorizeHttpRequests(c->c.anyRequest().permitAll());
        return http.build();
    }
}
