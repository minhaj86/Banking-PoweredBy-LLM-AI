package com.example.bff.api;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@RestControllerAdvice
public class ProblemAdvice {
    @ExceptionHandler(ResponseStatusException.class)ResponseEntity<?> expected(ResponseStatusException ex,HttpServletRequest req){
        return ResponseEntity.status(ex.getStatusCode()).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(Map.of("status",ex.getStatusCode().value(),"code",Optional.ofNullable(ex.getReason()).orElse("REQUEST_FAILED"),"detail","Request could not complete","correlationId",String.valueOf(req.getAttribute("correlationId"))));
    }
    @ExceptionHandler(IllegalArgumentException.class)ResponseEntity<?> invalid(HttpServletRequest req){return ResponseEntity.badRequest().body(Map.of("code","INVALID_REQUEST","correlationId",String.valueOf(req.getAttribute("correlationId"))));}
    @ExceptionHandler(Exception.class)ResponseEntity<?> failed(HttpServletRequest req){return ResponseEntity.status(503).body(Map.of("code","BFF_UNAVAILABLE","correlationId",String.valueOf(req.getAttribute("correlationId"))));}
}

