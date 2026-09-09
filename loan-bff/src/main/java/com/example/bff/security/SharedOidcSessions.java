package com.example.bff.security;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.oauth2.client.oidc.session.*;
import org.springframework.security.oauth2.client.oidc.authentication.logout.OidcLogoutToken;
import org.springframework.stereotype.Component;
import org.springframework.util.SerializationUtils;
import java.time.Duration;
import java.util.*;

@Component @RequiredArgsConstructor
public class SharedOidcSessions implements OidcSessionRegistry {
    private final StringRedisTemplate redis; private final StateCipher cipher;
    public void saveSessionInformation(OidcSessionInformation info) {
        String id=info.getSessionId();var user=info.getPrincipal();String sid=user.getIdToken().getClaimAsString("sid");
        redis.opsForValue().set("bff:oidc:"+id,Base64.getEncoder().encodeToString(cipher.encrypt(SerializationUtils.serialize(info))),Duration.ofHours(8));
        index("sub:"+user.getSubject(),id); if(sid!=null) index("sid:"+sid,id);
    }
    private void index(String key,String id) {redis.opsForSet().add("bff:oidc-index:"+key,id);redis.expire("bff:oidc-index:"+key,Duration.ofHours(8));}
    public OidcSessionInformation removeSessionInformation(String id) {
        String value=redis.opsForValue().getAndDelete("bff:oidc:"+id);
        if(value==null)return null;
        return (OidcSessionInformation)SerializationUtils.deserialize(cipher.decrypt(Base64.getDecoder().decode(value)));
    }
    public Iterable<OidcSessionInformation> removeSessionInformation(OidcLogoutToken token) {
        String sid=token.getSessionId();String index=sid==null?"sub:"+token.getSubject():"sid:"+sid;
        Set<String> ids=redis.opsForSet().members("bff:oidc-index:"+index);var result=new ArrayList<OidcSessionInformation>();
        if(ids!=null) for(String id:ids) {var info=removeSessionInformation(id);if(info!=null)result.add(info);}
        redis.delete("bff:oidc-index:"+index);return result;
    }
}

