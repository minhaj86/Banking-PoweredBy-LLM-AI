package com.example.bff.security;

import com.example.bff.api.RoutePolicy;
import com.example.bff.config.BffProperties;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class SecurityPrimitivesTest {
    @Test void encryptedStateIsRandomisedAndRejectsTamperingAndWrongKeys(){
        var p=new BffProperties();p.setEncryptionKey(Base64.getEncoder().encodeToString(new byte[32]));var cipher=new StateCipher(p);
        String first=cipher.seal("server-only-refresh-token");String second=cipher.seal("server-only-refresh-token");
        assertThat(first).isNotEqualTo(second).doesNotContain("refresh-token");assertThat(cipher.open(first)).isEqualTo("server-only-refresh-token");
        var bytes=Base64.getDecoder().decode(first);bytes[20]^=1;assertThatThrownBy(()->cipher.decrypt(bytes)).isInstanceOf(IllegalStateException.class);
        byte[] other=new byte[32];other[0]=1;p.setEncryptionKey(Base64.getEncoder().encodeToString(other));assertThatThrownBy(()->new StateCipher(p).open(first)).isInstanceOf(IllegalStateException.class);
    }
    @Test void onlyValidatedPortalReturnPathsAreAccepted(){
        assertThat(SafeReturnPath.validate("/app/applications/123/edit")).isEqualTo("/app/applications/123/edit");
        for(String path:List.of("https://evil.example","//evil.example","/app/../staff","/app?token=one","/staff%2f..%2f","/app\\evil","/other"))assertThatThrownBy(()->SafeReturnPath.validate(path)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void proxyDenylistCannotBeBypassedAndStaffPermissionsNeedAssurance(){
        var policy=new RoutePolicy();var basic=identity(Set.of("APPLICANT","LOAN_OFFICER"),"loan-basic");var strong=identity(Set.of("APPLICANT","LOAN_OFFICER"),"loan-staff");
        String id="12345678-1234-4234-8234-123456789012";
        assertThatThrownBy(()->policy.authorise("POST","/loan-applications/"+id+"/decisions",basic)).isInstanceOf(ResponseStatusException.class);
        policy.authorise("POST","/loan-applications/"+id+"/decisions",strong);
        assertThatThrownBy(()->policy.authorise("GET","/operations/jobs",strong)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(()->policy.authorise("GET","/http://evil.example",strong)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(()->policy.authorise("DELETE","/loan-applications/"+id,strong)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(()->policy.authorise("GET","/loan-applications/"+id+"/../../operations",strong)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(()->policy.authorise("GET","/loan-applications",identity(Set.of("SERVICE"),"loan-staff"))).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(()->policy.authorise("GET","/loan-applications",identity(Set.of("PLATFORM_ADMIN"),"loan-staff"))).isInstanceOf(ResponseStatusException.class);
        assertThat(TokenRelay.identityKey("https://issuer-a","subject")).isNotEqualTo(TokenRelay.identityKey("https://issuer-b","subject"));
    }
    @Test void queryValidationNeverParsesOrConsumesAMultipartBody(){
        var policy=new RoutePolicy();var request=new org.springframework.mock.web.MockHttpServletRequest();request.setMethod("POST");request.setContentType("multipart/form-data; boundary=evidence");byte[] body="--evidence--".getBytes(java.nio.charset.StandardCharsets.US_ASCII);request.setContent(body);request.setQueryString("page=0&sort=oldest");assertThat(policy.query(request)).containsEntry("page","0");assertThat(request.getContentAsByteArray()).isEqualTo(body);
        request.setQueryString("page=0&page=1");assertThatThrownBy(()->policy.query(request)).isInstanceOf(ResponseStatusException.class);
        request.setQueryString("destination=https://evil.example");assertThatThrownBy(()->policy.query(request)).isInstanceOf(ResponseStatusException.class);
    }
    private TokenRelay.Identity identity(Set<String> roles,String acr){return new TokenRelay.Identity("key","demo","User",roles,true,acr,null);}
}
