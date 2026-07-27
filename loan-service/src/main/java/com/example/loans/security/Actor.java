package com.example.loans.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import java.util.Set;
import java.util.stream.Collectors;

public record Actor(String tenant, String subject, Set<String> roles) {
    public static Actor current() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) throw new AccessDeniedException("JWT required");
        String tenant = jwt.getClaimAsString("tenant");
        if (tenant == null || !tenant.matches("[A-Za-z0-9_-]{1,80}") || jwt.getSubject()==null) throw new AccessDeniedException("Tenant and subject required");
        String issuer=jwt.getClaimAsString("iss");
        String subject="loan-local".equals(issuer)?jwt.getSubject():identityKey(issuer,jwt.getSubject());
        return new Actor(tenant,subject,auth.getAuthorities().stream().map(a -> a.getAuthority().replace("ROLE_","")).collect(Collectors.toUnmodifiableSet()));
    }
    public boolean has(String role) { return roles.contains(role); }
    public void require(String... allowed) {
        for (String role:allowed) if (has(role)) { if(!role.equals("APPLICANT")) requireStaffAssurance(); return; }
        throw new AccessDeniedException("Role not authorised");
    }
    public boolean reviewer() { return has("LOAN_OFFICER") || has("COMPLIANCE_REVIEWER") || has("OPERATIONS"); }
    public static String identityKey(String issuer,String subject) {
        if(issuer==null||issuer.isBlank()) throw new AccessDeniedException("Issuer required");
        try { return "oidc:"+java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest((issuer+"\n"+subject).getBytes(java.nio.charset.StandardCharsets.UTF_8))); }
        catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
    public static void requireStaffAssurance() {
        var auth=SecurityContextHolder.getContext().getAuthentication();
        if(auth!=null && auth.getPrincipal() instanceof Jwt jwt && !"loan-local".equals(jwt.getClaimAsString("iss")) && !"loan-staff".equals(jwt.getClaimAsString("acr")))
            throw new AccessDeniedException("Staff step-up authentication required");
    }
    public static void requireVerifiedEmail() {
        var auth=SecurityContextHolder.getContext().getAuthentication();
        if(auth!=null && auth.getPrincipal() instanceof Jwt jwt && !"loan-local".equals(jwt.getClaimAsString("iss")) && !Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified")))
            throw new AccessDeniedException("Verified email required before submission");
    }
}

