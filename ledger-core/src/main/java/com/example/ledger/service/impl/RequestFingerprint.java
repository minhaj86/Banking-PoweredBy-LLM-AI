package com.example.ledger.service.impl;

import com.example.ledger.domain.valueobject.Posting;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.HexFormat;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class RequestFingerprint {
    private final ObjectMapper mapper;

    public String posting(Posting posting) {
        // Posting contains canonical two-decimal Money, ordered lines, and only business fields.
        return hash("v1:POST:" + json(posting));
    }

    public String reversal(UUID originalId, String reason) {
        return hash("v1:REVERSE:" + json(new ReversalFingerprint(originalId, reason)));
    }

    private record ReversalFingerprint(UUID originalId, String reason) {}

    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("Cannot fingerprint validated request", ex); }
    }

    private String hash(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
}
