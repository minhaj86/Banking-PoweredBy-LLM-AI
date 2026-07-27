package com.example.loans.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class LoanException extends RuntimeException {
    private final String code;
    private final HttpStatus status;
    public LoanException(String code, HttpStatus status, String message) { super(message); this.code = code; this.status = status; }
    public static LoanException invalid(String code, String message) { return new LoanException(code, HttpStatus.UNPROCESSABLE_ENTITY, message); }
    public static LoanException conflict(String code, String message) { return new LoanException(code, HttpStatus.CONFLICT, message); }
    public static LoanException missing() { return new LoanException("NOT_FOUND", HttpStatus.NOT_FOUND, "Resource was not found"); }
    public static LoanException unavailable(String code) { return new LoanException(code, HttpStatus.SERVICE_UNAVAILABLE, "Required processing capability is unavailable"); }
}
