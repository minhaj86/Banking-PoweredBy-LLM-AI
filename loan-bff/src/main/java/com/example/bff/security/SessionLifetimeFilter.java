package com.example.bff.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.UUID;

public class SessionLifetimeFilter extends OncePerRequestFilter {
    protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain) throws ServletException,IOException {
        res.setHeader("Cache-Control","no-store");res.setHeader("Pragma","no-cache");
        String id=req.getHeader("X-Correlation-ID");if(id==null||!id.matches("[A-Za-z0-9._:-]{1,100}"))id=UUID.randomUUID().toString();
        req.setAttribute("correlationId",id);res.setHeader("X-Correlation-ID",id);
        chain.doFilter(req,res);
    }
}

