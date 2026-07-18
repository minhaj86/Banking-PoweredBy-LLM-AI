package com.example.loans.config;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.UUID;

@Component @Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationFilter extends OncePerRequestFilter {
    @Override protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws IOException, ServletException {
        String id = req.getHeader("X-Correlation-ID");
        if (id == null || !id.matches("[A-Za-z0-9._:-]{1,100}")) id = UUID.randomUUID().toString();
        req.setAttribute("correlationId", id); res.setHeader("X-Correlation-ID", id);
        org.slf4j.MDC.put("correlationId",id);
        try {chain.doFilter(req, res);} finally {org.slf4j.MDC.remove("correlationId");}
    }
}
