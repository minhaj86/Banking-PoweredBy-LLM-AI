package com.example.bff.security;

import com.example.bff.config.BffProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.*;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.*;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.oauth2.client.*;
import org.springframework.security.oauth2.client.registration.*;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.oidc.session.OidcSessionInformation;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.core.oidc.*;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.jwt.*;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@Testcontainers
class SharedAuthenticationIT {
    @Container static GenericContainer<?> REDIS=new GenericContainer<>("redis:7.4.5-alpine").withExposedPorts(6379);
    private LettuceConnectionFactory connection;private StringRedisTemplate redis;private BffProperties properties;private SharedAuthorizedClients clients;private ClientRegistration registration;private StateCipher cipher;
    @BeforeEach void setup(){connection=new LettuceConnectionFactory(REDIS.getHost(),REDIS.getMappedPort(6379));connection.afterPropertiesSet();redis=new StringRedisTemplate(connection);properties=new BffProperties();properties.setEncryptionKey(Base64.getEncoder().encodeToString(new byte[32]));cipher=new StateCipher(properties);
        registration=ClientRegistration.withRegistrationId("keycloak").clientId("loan-bff").clientSecret("test-only").authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE).redirectUri("http://localhost/callback").authorizationUri("https://issuer/auth").tokenUri("https://issuer/token").jwkSetUri("https://issuer/jwks").build();
        clients=new SharedAuthorizedClients(redis,cipher,new ObjectMapper().registerModule(new JavaTimeModule()),new InMemoryClientRegistrationRepository(registration));}
    @AfterEach void close(){connection.destroy();}
    @Test void concurrentRequestsAcrossReplicasRefreshOnceAndShareEncryptedState()throws Exception{
        var auth=new TestingAuthenticationToken("subject","none","ROLE_APPLICANT");var first=request("shared-"+UUID.randomUUID());var second=request((String)first.getSession().getAttribute(SharedAuthorizedClients.STORE_KEY));
        clients.saveAuthorizedClient(client("old-access",Instant.now().plusSeconds(1)),auth,first,new MockHttpServletResponse());
        assertThat(redis.opsForValue().get("bff:client:"+clients.key(first))).doesNotContain("old-access","refresh-only-server");
        var manager=mock(DefaultOAuth2AuthorizedClientManager.class);var calls=new AtomicInteger();when(manager.authorize(any())).thenAnswer(inv->{calls.incrementAndGet();Thread.sleep(150);var fresh=client("new-access",Instant.now().plusSeconds(60));clients.saveAuthorizedClient(fresh,auth,first,new MockHttpServletResponse());return fresh;});
        JwtDecoder decoder=value->Jwt.withTokenValue(value).header("alg","RS256").issuer("https://issuer").subject("subject").audience(List.of("loan-service")).issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).claim("tenant","demo").claim("roles",List.of("APPLICANT")).claim("acr","loan-basic").build();
        var replicaA=new TokenRelay(clients,manager,redis,decoder,properties);var replicaB=new TokenRelay(clients,manager,redis,decoder,properties);var barrier=new CyclicBarrier(2);
        try(var pool=Executors.newFixedThreadPool(2)){var left=pool.submit(()->{barrier.await();return replicaA.identity(first,new MockHttpServletResponse(),auth);});var right=pool.submit(()->{barrier.await();return replicaB.identity(second,new MockHttpServletResponse(),auth);});assertThat(left.get(15,TimeUnit.SECONDS).jwt().getTokenValue()).isEqualTo("new-access");assertThat(right.get(15,TimeUnit.SECONDS).jwt().getTokenValue()).isEqualTo("new-access");}assertThat(calls.get()).isOne();
    }
    @Test void providerSessionRegistryIsSharedBetweenInstances(){
        var one=new SharedOidcSessions(redis,cipher);var two=new SharedOidcSessions(redis,cipher);var id=UUID.randomUUID().toString();var now=Instant.now();var user=new DefaultOidcUser(Set.of(),new OidcIdToken("id-token-only-server",now,now.plusSeconds(60),Map.of("sub","subject-"+id,"sid","sid-"+id,"iss","https://issuer")));
        one.saveSessionInformation(new OidcSessionInformation(id,Map.of(),user));assertThat(redis.opsForValue().get("bff:oidc:"+id)).doesNotContain("id-token-only-server");assertThat(two.removeSessionInformation(id).getPrincipal().getSubject()).isEqualTo("subject-"+id);assertThat(one.removeSessionInformation(id)).isNull();
    }
    @Test void logoutFencesAnInflightRefreshAndPreventsClientResurrection()throws Exception{
        var auth=new TestingAuthenticationToken("subject","none","ROLE_APPLICANT");var request=request("revocation-"+UUID.randomUUID());String key=clients.key(request);clients.saveAuthorizedClient(client("old-access",Instant.now().plusSeconds(1)),auth,request,new MockHttpServletResponse());
        var manager=mock(DefaultOAuth2AuthorizedClientManager.class);var reached=new CountDownLatch(1);var continueRefresh=new CountDownLatch(1);
        when(manager.authorize(any())).thenAnswer(inv->{reached.countDown();assertThat(continueRefresh.await(10,TimeUnit.SECONDS)).isTrue();var fresh=client("new-access",Instant.now().plusSeconds(60));clients.saveAuthorizedClient(fresh,auth,request,new MockHttpServletResponse());return fresh;});
        var relay=new TokenRelay(clients,manager,redis,mock(JwtDecoder.class),properties);
        try(var pool=Executors.newSingleThreadExecutor()){
            var refresh=pool.submit(()->{try{relay.identity(request,new MockHttpServletResponse(),auth);return 200;}catch(org.springframework.web.server.ResponseStatusException ex){return ex.getStatusCode().value();}});
            assertThat(reached.await(10,TimeUnit.SECONDS)).isTrue();clients.removeKey(key);continueRefresh.countDown();assertThat(refresh.get(15,TimeUnit.SECONDS)).isEqualTo(401);
        }
        assertThat(redis.hasKey("bff:client:"+key)).isFalse();assertThat(redis.hasKey("bff:revoked:"+key)).isTrue();
    }
    @Test void maximumSessionAgeRevokesClientMaterialEvenWhileTokenRemainsValid(){
        var auth=new TestingAuthenticationToken("subject","none","ROLE_APPLICANT");var request=request("expired-"+UUID.randomUUID());String key=clients.key(request);clients.saveAuthorizedClient(client("access",Instant.now().plusSeconds(60)),auth,request,new MockHttpServletResponse());request.getSession().setAttribute("BFF_LOGIN_AT",System.currentTimeMillis()-Duration.ofHours(9).toMillis());
        var relay=new TokenRelay(clients,mock(DefaultOAuth2AuthorizedClientManager.class),redis,mock(JwtDecoder.class),properties);
        assertThatThrownBy(()->relay.identity(request,new MockHttpServletResponse(),auth)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);assertThat(redis.hasKey("bff:client:"+key)).isFalse();
    }
    private MockHttpServletRequest request(String key){var request=new MockHttpServletRequest();request.getSession().setAttribute(SharedAuthorizedClients.STORE_KEY,key);request.getSession().setAttribute("BFF_LOGIN_AT",System.currentTimeMillis());return request;}
    private OAuth2AuthorizedClient client(String access,Instant expiry){return new OAuth2AuthorizedClient(registration,"subject",new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER,access,Instant.now().minusSeconds(60),expiry,Set.of("openid")),new OAuth2RefreshToken("refresh-only-server",Instant.now().minusSeconds(60)));}
}
