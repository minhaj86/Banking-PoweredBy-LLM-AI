package com.example.ledger.config;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.UUID;

@Component
@Slf4j
public class CorrelationFilter extends OncePerRequestFilter {
    public static final String HEADER = "X-Correlation-ID";
    public static final String ATTRIBUTE = "correlationId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String supplied = request.getHeader(HEADER);
        String id = supplied != null && supplied.matches("[A-Za-z0-9._:-]{1,64}") ? supplied : UUID.randomUUID().toString();
        request.setAttribute(ATTRIBUTE, id);
        response.setHeader(HEADER, id);
        MDC.put(ATTRIBUTE, id);
        long start = System.nanoTime();
        try { chain.doFilter(request, response); }
        finally {
            log.info("event=http_request method={} status={} durationMs={}", request.getMethod(), response.getStatus(),
                    (System.nanoTime() - start) / 1_000_000);
            MDC.remove(ATTRIBUTE);
        }
    }
}
