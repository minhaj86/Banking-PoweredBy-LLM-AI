package com.example.bff.config;

import com.example.bff.security.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.*;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.*;
import org.springframework.security.oauth2.client.oidc.session.OidcSessionRegistry;
import org.springframework.security.oauth2.client.oidc.authentication.logout.OidcLogoutToken;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.web.*;
import org.springframework.security.web.csrf.*;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.session.*;
import java.util.*;

@Configuration
public class SecurityConfiguration {
    @Bean SecurityFilterChain security(HttpSecurity http,ClientRegistrationRepository registrations,SharedAuthorizedClients clients,
            SharedOidcSessions registry,SessionRepository<? extends Session> sessions,BffProperties p,ObjectMapper json,
            org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient<org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest> codeTokens) throws Exception {
        var resolver=new DefaultOAuth2AuthorizationRequestResolver(registrations,"/oauth2/authorization");
        resolver.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce());
        var csrf=new HttpSessionCsrfTokenRepository();csrf.setHeaderName("X-CSRF-TOKEN");
        var handler=new CsrfTokenRequestAttributeHandler();
        var logout=new OidcClientInitiatedLogoutSuccessHandler(registrations);
        // OIDC's client_id logout variant keeps ID tokens out of browser URLs and HTML.
        logout.setDefaultTargetUrl(p.getIssuer()+"/protocol/openid-connect/logout?client_id="+p.getClientId()+"&post_logout_redirect_uri="+java.net.URLEncoder.encode(p.getOrigin()+"/",java.nio.charset.StandardCharsets.UTF_8));
        http.addFilterBefore(new SessionLifetimeFilter(),UsernamePasswordAuthenticationFilter.class)
            .csrf(c->c.csrfTokenRepository(csrf).csrfTokenRequestHandler(handler).ignoringRequestMatchers("/logout/connect/back-channel/keycloak"))
            .authorizeHttpRequests(a->a.requestMatchers("/actuator/health","/bff/csrf","/bff/login","/bff/product","/oauth2/**","/login/**","/logout/connect/back-channel/keycloak").permitAll().anyRequest().authenticated())
            .exceptionHandling(e->e.authenticationEntryPoint((req,res,ex)->problem(json,req,res,401,"SESSION_REQUIRED"))
                .accessDeniedHandler((req,res,ex)->problem(json,req,res,403,"CSRF_OR_PERMISSION_DENIED")))
            .requestCache(c->c.disable())
            .oauth2Login(o->o.oidcSessionRegistry(registry).tokenEndpoint(t->t.accessTokenResponseClient(codeTokens)).authorizedClientRepository(clients).authorizationEndpoint(a->a.authorizationRequestResolver(new StaffAuthorizationResolver(resolver)))
                .successHandler((req,res,auth)->{
                    req.getSession().setAttribute("BFF_LOGIN_AT",System.currentTimeMillis());
                    String target=(String)req.getSession().getAttribute("BFF_RETURN_PATH");req.getSession().removeAttribute("BFF_RETURN_PATH");
                    res.sendRedirect(p.getOrigin()+SafeReturnPath.validate(target));
                }).failureHandler((req,res,ex)->res.sendRedirect(p.getOrigin()+"/?authentication=failed")))
            .logout(l->l.logoutUrl("/bff/logout").addLogoutHandler((req,res,auth)->clients.removeAuthorizedClient("keycloak",auth,req,res))
                .logoutSuccessHandler((req,res,auth)->logout.onLogoutSuccess(req,res,null)).invalidateHttpSession(true).clearAuthentication(true).deleteCookies("LOAN_SESSION"))
            .oidcLogout(o->o.backChannel(b->b.logoutHandler((req,res,auth)->{
                int matched=0,deleted=0;
                if(auth.getPrincipal() instanceof OidcLogoutToken token) for(var info:registry.removeSessionInformation(token)) {
                    matched++;
                    var session=sessions.findById(info.getSessionId());
                    if(session!=null){clients.removeKey((String)session.getAttribute(SharedAuthorizedClients.STORE_KEY));sessions.deleteById(info.getSessionId());deleted++;}
                }
                org.slf4j.LoggerFactory.getLogger(SecurityConfiguration.class).info("event=provider_logout matched={} deleted={}",matched,deleted);
            })));
        return http.build();
    }
    private static void problem(ObjectMapper json,HttpServletRequest req,HttpServletResponse res,int status,String code)throws java.io.IOException {
        res.setStatus(status);res.setContentType("application/problem+json");json.writeValue(res.getOutputStream(),Map.of("status",status,"code",code,"detail","Session or request permission is not valid","correlationId",String.valueOf(req.getAttribute("correlationId"))));
    }
    private static class StaffAuthorizationResolver implements OAuth2AuthorizationRequestResolver {
        private final OAuth2AuthorizationRequestResolver delegate;
        StaffAuthorizationResolver(OAuth2AuthorizationRequestResolver delegate){this.delegate=delegate;}
        public org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest resolve(HttpServletRequest req){return customise(delegate.resolve(req),req);}
        public org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest resolve(HttpServletRequest req,String id){return customise(delegate.resolve(req,id),req);}
        private org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest customise(org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest result,HttpServletRequest req){
            if(result==null)return null;
            if(Boolean.TRUE.equals(req.getSession().getAttribute("BFF_REGISTER"))) {
                req.getSession().removeAttribute("BFF_REGISTER");
                return org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest.from(result)
                    .authorizationUri(result.getAuthorizationUri().replace("/protocol/openid-connect/auth","/protocol/openid-connect/registrations"))
                    .authorizationRequestUri(result.getAuthorizationRequestUri().replace("/protocol/openid-connect/auth?","/protocol/openid-connect/registrations?")).build();
            }
            if(Boolean.TRUE.equals(req.getSession().getAttribute("BFF_STAFF_LOGIN"))) {
                req.getSession().removeAttribute("BFF_STAFF_LOGIN");
                return org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest.from(result).additionalParameters(m->{m.put("acr_values","loan-staff");m.put("claims","{\"id_token\":{\"acr\":{\"essential\":true,\"values\":[\"loan-staff\"]}}}");}).build();
            }
            return result;
        }
    }
}

