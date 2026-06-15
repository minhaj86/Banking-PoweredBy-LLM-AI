package com.example.ledger.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class LedgerException extends RuntimeException {
    private final String code;
    private final HttpStatus status;

    public LedgerException(String code, HttpStatus status, String message) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public static LedgerException invalid(String code, String message) {
        return new LedgerException(code, HttpStatus.UNPROCESSABLE_ENTITY, message);
    }

    public static LedgerException missing(String resource) {
        return new LedgerException("RESOURCE_NOT_FOUND", HttpStatus.NOT_FOUND, resource + " was not found in this ledger");
    }

    public static LedgerException conflict(String code, String message) {
        return new LedgerException(code, HttpStatus.CONFLICT, message);
    }
}
