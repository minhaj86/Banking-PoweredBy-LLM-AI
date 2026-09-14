package com.example.bff.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

/** Minimal callback outcome audit; never logs the signed token, claims or identifiers. */
@Component @Order(Ordered.HIGHEST_PRECEDENCE+30)
public class ProviderLogoutAuditFilter extends OncePerRequestFilter {
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException {
        if(!request.getRequestURI().equals("/logout/connect/back-channel/keycloak")){chain.doFilter(request,response);return;}
        try{chain.doFilter(request,response);}finally{org.slf4j.LoggerFactory.getLogger(ProviderLogoutAuditFilter.class).info("event=provider_logout_http status={}",response.getStatus());}
    }
}
