package com.example.ledger.service.impl;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RequestValidation {
    private final Validator validator;

    public void validate(Object request) {
        var violations = validator.validate(request);
        if (!violations.isEmpty()) { throw new ConstraintViolationException(violations); }
    }
}
