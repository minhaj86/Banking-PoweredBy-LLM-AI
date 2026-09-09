package com.example.bff.config;

import com.example.bff.security.StateCipher;
import org.springframework.context.annotation.*;
import org.springframework.data.redis.serializer.*;
import org.springframework.session.web.http.*;

@Configuration
public class RedisSessionConfig {
    @Bean RedisSerializer<Object> springSessionDefaultRedisSerializer(StateCipher cipher) {
        var jdk=new JdkSerializationRedisSerializer();
        return new RedisSerializer<>() {
            public byte[] serialize(Object value) {return cipher.encrypt(jdk.serialize(value));}
            public Object deserialize(byte[] value) {return jdk.deserialize(cipher.decrypt(value));}
        };
    }
    @Bean CookieSerializer cookieSerializer(BffProperties p) {
        var c=new DefaultCookieSerializer();c.setCookieName("LOAN_SESSION");c.setCookiePath("/");c.setUseHttpOnlyCookie(true);c.setUseSecureCookie(p.isSecureCookie());c.setSameSite("Lax");return c;
    }
}

