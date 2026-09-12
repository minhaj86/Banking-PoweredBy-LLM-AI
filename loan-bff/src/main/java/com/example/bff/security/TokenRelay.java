package com.example.bff.security;

import com.example.bff.config.BffProperties;
import jakarta.servlet.http.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.*;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.time.*;
import java.util.*;

@Component @RequiredArgsConstructor
public class TokenRelay {
    private final SharedAuthorizedClients clients; private final DefaultOAuth2AuthorizedClientManager manager;
    private final StringRedisTemplate redis; private final JwtDecoder decoder;private final BffProperties config;
    public record Identity(String key,String tenant,String name,Set<String> roles,boolean emailVerified,String assurance,Jwt jwt) {
        public boolean staff(){return roles.stream().anyMatch(Set.of("LOAN_OFFICER","COMPLIANCE_REVIEWER","OPERATIONS","PLATFORM_ADMIN")::contains);}
        public boolean strong(){return "loan-staff".equals(assurance);}
    }
    public Identity identity(HttpServletRequest request,HttpServletResponse response,Authentication auth) {
        if(auth==null||request.getSession(false)==null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"SESSION_REQUIRED");
        var session=request.getSession(false);
        Long started=(Long)session.getAttribute("BFF_LOGIN_AT");
        if(started==null||Instant.ofEpochMilli(started).plus(config.getMaximumLifetime()).isBefore(Instant.now())) {clients.removeAuthorizedClient("keycloak",auth,request,response);session.invalidate();throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"SESSION_EXPIRED");}
        String storageKey=clients.key(request),lockKey="bff:refresh-lock:"+storageKey,owner=UUID.randomUUID().toString();
        boolean locked=false;
        try {
            var client=clients.loadAuthorizedClient("keycloak",auth,request);
            if(client==null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"SESSION_EXPIRED");
            if(client.getAccessToken().getExpiresAt().isBefore(Instant.now().plusSeconds(15))) {
                long until=System.nanoTime()+Duration.ofSeconds(10).toNanos();
                while(!(locked=Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(lockKey,owner,Duration.ofSeconds(30))))) {
                    if(System.nanoTime()>until) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"REFRESH_BUSY");
                    Thread.sleep(40);
                }
                client=clients.loadAuthorizedClient("keycloak",auth,request);
                if(client==null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"SESSION_EXPIRED");
                if(client.getAccessToken().getExpiresAt().isBefore(Instant.now().plusSeconds(15))) {
                    client=manager.authorize(OAuth2AuthorizeRequest.withClientRegistrationId("keycloak").principal(auth).attribute(HttpServletRequest.class.getName(),request).attribute(HttpServletResponse.class.getName(),response).build());
                }
            }
            if(locked){release(lockKey,owner);locked=false;}
            if(client==null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"SESSION_EXPIRED");
            Jwt jwt=decoder.decode(client.getAccessToken().getTokenValue());
            if(jwt.getSubject()==null||jwt.getSubject().isBlank())throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"SUBJECT_REQUIRED");
            if(!jwt.getSubject().equals(auth.getName()))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"IDENTITY_CHANGED");
            String tenant=jwt.getClaimAsString("tenant");
            if(tenant==null||!tenant.matches("[A-Za-z0-9_-]{1,80}"))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"TENANT_REQUIRED");
            var list=jwt.getClaimAsStringList("roles");Set<String> roles=list==null?Set.of():Set.copyOf(list);
            String key=identityKey(jwt.getIssuer().toString(),jwt.getSubject());
            return new Identity(key,tenant,Optional.ofNullable(jwt.getClaimAsString("preferred_username")).orElse("Member"),roles,Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified")),jwt.getClaimAsString("acr"),jwt);
        }catch(ResponseStatusException ex){throw ex;}
        catch(InterruptedException ex){Thread.currentThread().interrupt();throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"AUTH_UNAVAILABLE");}
        catch(org.springframework.data.redis.RedisConnectionFailureException ex){throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"SESSION_STORE_UNAVAILABLE");}
        catch(RuntimeException ex){clients.removeAuthorizedClient("keycloak",auth,request,response);session.invalidate();throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"SESSION_EXPIRED");}
        finally {if(locked)release(lockKey,owner);}
    }
    private void release(String key,String owner){redis.execute(new DefaultRedisScript<>("if redis.call('get',KEYS[1])==ARGV[1] then return redis.call('del',KEYS[1]) else return 0 end",Long.class),List.of(key),owner);}
    public static String identityKey(String issuer,String subject) {
        try {return "oidc:"+java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest((issuer+"\n"+subject).getBytes(java.nio.charset.StandardCharsets.UTF_8)));}
        catch(Exception ex){throw new IllegalStateException("Identity hash unavailable");}
    }
}

