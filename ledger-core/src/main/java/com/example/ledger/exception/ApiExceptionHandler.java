package com.example.ledger.exception;

import com.example.ledger.config.CorrelationFilter;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.*;
import org.springframework.http.*;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import java.net.URI;
import java.sql.SQLException;
import java.util.UUID;

@RestControllerAdvice
@Slf4j
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {
    @ExceptionHandler(LedgerException.class)
    ResponseEntity<Object> ledger(LedgerException ex, WebRequest request) {
        return problem(ex.getStatus(), ex.getCode(), ex.getMessage(), HttpHeaders.EMPTY, request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<Object> validation(ConstraintViolationException ex, WebRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "Request failed validation", HttpHeaders.EMPTY, request);
    }

    @ExceptionHandler({DataAccessException.class, TransactionException.class})
    ResponseEntity<Object> database(RuntimeException ex, WebRequest request) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && sql.getSQLState() != null && sql.getSQLState().startsWith("23")) {
                return problem(HttpStatus.CONFLICT, "DATABASE_CONSTRAINT",
                        "The request conflicts with a ledger constraint or an existing code", HttpHeaders.EMPTY, request);
            }
        }
        log.warn("event=database_failure cause={}", ex.getClass().getSimpleName());
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "DATABASE_UNAVAILABLE",
                "Database operation could not complete; retry posting with the same idempotency key", HttpHeaders.EMPTY, request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> unexpected(Exception ex, WebRequest request) {
        log.error("event=unexpected_failure cause={}", ex.getClass().getSimpleName());
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Unexpected server error", HttpHeaders.EMPTY, request);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        String code = status.value() == 404 ? "RESOURCE_NOT_FOUND" : "MALFORMED_REQUEST";
        String message = status.value() == 404 ? "Resource was not found" : "Request format or parameters are invalid";
        return problem(status, code, message, headers, request);
    }

    private ResponseEntity<Object> problem(HttpStatusCode status, String code, String message, HttpHeaders headers, WebRequest request) {
        Object correlation = request.getAttribute(CorrelationFilter.ATTRIBUTE, WebRequest.SCOPE_REQUEST);
        var detail = ProblemDetail.forStatusAndDetail(status, message);
        detail.setType(URI.create("urn:ledger:error:" + code.toLowerCase(java.util.Locale.ROOT)));
        detail.setProperty("code", code);
        detail.setProperty("message", message);
        detail.setProperty("correlationId", correlation == null ? UUID.randomUUID().toString() : correlation);
        return new ResponseEntity<>(detail, headers, status);
    }
}
