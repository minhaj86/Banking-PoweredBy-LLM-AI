package com.example.loans.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.*;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import java.net.URI;

@RestControllerAdvice
public class ProblemHandler extends ResponseEntityExceptionHandler {
    @ExceptionHandler(LoanException.class) ResponseEntity<Object> domain(LoanException ex, HttpServletRequest req) { return problem(ex.getStatus(), ex.getCode(), ex.getMessage(), req.getAttribute("correlationId")); }
    @ExceptionHandler(AccessDeniedException.class) ResponseEntity<Object> denied(AccessDeniedException ex,HttpServletRequest req) {
        // Fixed policy reason only: no principal, token, form or document content.
        logger.warn("event=authorisation_denied reason="+ex.getMessage());
        return problem(HttpStatus.FORBIDDEN,"FORBIDDEN","Caller is not authorised", req.getAttribute("correlationId"));
    }
    @ExceptionHandler({OptimisticLockingFailureException.class, jakarta.persistence.OptimisticLockException.class}) ResponseEntity<Object> version(HttpServletRequest req) { return problem(HttpStatus.CONFLICT,"VERSION_CONFLICT","Resource changed; reload before retrying", req.getAttribute("correlationId")); }
    @ExceptionHandler(DataIntegrityViolationException.class) ResponseEntity<Object> constraint(HttpServletRequest req) { return problem(HttpStatus.CONFLICT,"CONSTRAINT_CONFLICT","Request conflicts with an existing resource", req.getAttribute("correlationId")); }
    @ExceptionHandler(DataAccessException.class) ResponseEntity<Object> database(HttpServletRequest req) { return problem(HttpStatus.SERVICE_UNAVAILABLE,"DATABASE_UNAVAILABLE","Database request could not complete", req.getAttribute("correlationId")); }
    @ExceptionHandler(Exception.class) ResponseEntity<Object> unexpected(HttpServletRequest req) { return problem(HttpStatus.INTERNAL_SERVER_ERROR,"INTERNAL_ERROR","Request could not complete", req.getAttribute("correlationId")); }
    @Override protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers, HttpStatusCode status, WebRequest req) {
        return problem(status,"INVALID_REQUEST","Invalid request format or parameters",req.getAttribute("correlationId",WebRequest.SCOPE_REQUEST));
    }
    public static ResponseEntity<Object> problem(HttpStatusCode status, String code, String message, Object correlation) {
        var p = ProblemDetail.forStatusAndDetail(status,message); p.setType(URI.create("urn:loan:error:" + code.toLowerCase(java.util.Locale.ROOT)));
        p.setProperty("code",code); p.setProperty("correlationId",correlation);
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(p);
    }
}
