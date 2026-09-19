package com.example.bff.security;

import com.example.bff.config.*;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.*;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.springframework.security.oauth2.jwt.*;
import java.net.*;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class JwtValidationTest {
    private HttpServer server;private RSAKey key;private JwtDecoder decoder;
    @BeforeEach void setup()throws Exception{
        key=new RSAKeyGenerator(2048).keyID("test-key").generate();server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/protocol/openid-connect/certs",e->{byte[] bytes=new JWKSet(key.toPublicJWK()).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);e.getResponseHeaders().set("Content-Type","application/json");e.sendResponseHeaders(200,bytes.length);e.getResponseBody().write(bytes);e.close();});server.start();
        var properties=new BffProperties();properties.setIssuer("https://canonical.example/auth/realms/loans");properties.setInternalIssuer("http://127.0.0.1:"+server.getAddress().getPort());decoder=new OidcConfiguration().accessTokenDecoder(properties);
    }
    @AfterEach void stop(){server.stop(0);}
    @Test void validatesSignatureCanonicalIssuerAudienceAndExpiry()throws Exception{
        String issuer="https://canonical.example/auth/realms/loans";assertThat(decoder.decode(token(key,issuer,"loan-service",Instant.now().plusSeconds(60))).getSubject()).isEqualTo("subject");
        assertThatThrownBy(()->decoder.decode(token(key,"https://wrong.example","loan-service",Instant.now().plusSeconds(60)))).isInstanceOf(JwtValidationException.class);
        assertThatThrownBy(()->decoder.decode(token(key,issuer,"loan-bff",Instant.now().plusSeconds(60)))).isInstanceOf(JwtValidationException.class);
        assertThatThrownBy(()->decoder.decode(token(key,issuer,"loan-service",Instant.now().minusSeconds(30)))).isInstanceOf(JwtValidationException.class);
        RSAKey rogue=new RSAKeyGenerator(2048).keyID("test-key").generate();assertThatThrownBy(()->decoder.decode(token(rogue,issuer,"loan-service",Instant.now().plusSeconds(60)))).isInstanceOf(JwtException.class);
    }
    private String token(RSAKey signing,String issuer,String audience,Instant expiry)throws Exception{
        var claims=new JWTClaimsSet.Builder().issuer(issuer).subject("subject").audience(audience).issueTime(Date.from(Instant.now().minusSeconds(120))).expirationTime(Date.from(expiry)).build();
        var jwt=new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("test-key").build(),claims);jwt.sign(new RSASSASigner(signing));return jwt.serialize();
    }
}
