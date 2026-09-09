package com.example.bff.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.*;
import org.springframework.security.oauth2.client.registration.*;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.*;
import org.springframework.stereotype.Component;
import java.time.*;
import java.util.*;

@Component @RequiredArgsConstructor
public class SharedAuthorizedClients implements OAuth2AuthorizedClientRepository {
    public static final String STORE_KEY="BFF_CLIENT_STORE";
    private final StringRedisTemplate redis;private final StateCipher cipher;private final ObjectMapper json;
    private final ClientRegistrationRepository registrations;
    private record Stored(String registration,String principal,String access,Instant issued,Instant expires,Set<String> scopes,String refresh,Instant refreshIssued,Instant refreshExpires) {}
    public String key(HttpServletRequest request) {
        var s=request.getSession();String key=(String)s.getAttribute(STORE_KEY);
        if(key==null){key=UUID.randomUUID().toString();s.setAttribute(STORE_KEY,key);}return key;
    }
    @SuppressWarnings("unchecked") public <T extends OAuth2AuthorizedClient> T loadAuthorizedClient(String id,Authentication principal,HttpServletRequest request) {
        if(request.getSession(false)==null) return null;
        String key=(String)request.getSession(false).getAttribute(STORE_KEY);if(key==null)return null;
        if(Boolean.TRUE.equals(redis.hasKey("bff:revoked:"+key)))return null;
        String value=redis.opsForValue().get("bff:client:"+key);if(value==null)return null;
        try {
            Stored s=json.readValue(cipher.open(value),Stored.class);
            if(!s.principal().equals(principal.getName()))return null;
            var access=new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER,s.access(),s.issued(),s.expires(),s.scopes());
            var refresh=s.refresh()==null?null:new OAuth2RefreshToken(s.refresh(),s.refreshIssued(),s.refreshExpires());
            return (T)new OAuth2AuthorizedClient(registrations.findByRegistrationId(id),s.principal(),access,refresh);
        }catch(Exception ex){throw new IllegalStateException("Authorised-client state is unavailable");}
    }
    public void saveAuthorizedClient(OAuth2AuthorizedClient client,Authentication principal,HttpServletRequest req,HttpServletResponse res) {
        var a=client.getAccessToken();var r=client.getRefreshToken();
        var stored=new Stored(client.getClientRegistration().getRegistrationId(),principal.getName(),a.getTokenValue(),a.getIssuedAt(),a.getExpiresAt(),a.getScopes(),r==null?null:r.getTokenValue(),r==null?null:r.getIssuedAt(),r==null?null:r.getExpiresAt());
        try {
            String key=key(req);Long saved=redis.execute(new org.springframework.data.redis.core.script.DefaultRedisScript<>("if redis.call('exists',KEYS[2])==1 then return 0 end redis.call('set',KEYS[1],ARGV[1],'EX',28800) return 1",Long.class),List.of("bff:client:"+key,"bff:revoked:"+key),cipher.seal(json.writeValueAsString(stored)));
            if(!Long.valueOf(1).equals(saved))throw new IllegalStateException("Session was revoked");
        }
        catch(Exception ex){throw new IllegalStateException("Cannot persist authorised client");}
    }
    public void removeAuthorizedClient(String id,Authentication auth,HttpServletRequest req,HttpServletResponse res) {
        if(req.getSession(false)!=null)removeKey((String)req.getSession(false).getAttribute(STORE_KEY));
    }
    public void removeKey(String key){if(key!=null)redis.execute(new org.springframework.data.redis.core.script.DefaultRedisScript<>("redis.call('set',KEYS[2],'1','EX',28800) return redis.call('del',KEYS[1])",Long.class),List.of("bff:client:"+key,"bff:revoked:"+key));}
}

