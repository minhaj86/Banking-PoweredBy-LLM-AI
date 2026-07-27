package com.example.loans.security;

import com.example.loans.config.LoanProperties;
import jakarta.persistence.*;
import org.springframework.stereotype.Component;
import javax.crypto.*;
import javax.crypto.spec.*;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

@Component @Converter
public class SensitiveTextConverter implements AttributeConverter<String,String> {
    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();
    public SensitiveTextConverter(LoanProperties properties) {
        if (properties.getEncryptionKey() == null) throw new IllegalStateException("LOAN_ENCRYPTION_KEY is required");
        byte[] bytes = Base64.getDecoder().decode(properties.getEncryptionKey());
        if (bytes.length != 32) throw new IllegalStateException("Encryption key must be 32 base64-encoded random bytes");
        key = new SecretKeySpec(bytes,"AES");
    }
    @Override public String convertToDatabaseColumn(String value) {
        if (value == null) return null;
        try {
            byte[] iv = new byte[12]; random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE,key,new GCMParameterSpec(128,iv));
            cipher.updateAAD("loan-origin-v1".getBytes(StandardCharsets.UTF_8));
            return "v1:" + Base64.getEncoder().encodeToString(iv) + ":" + Base64.getEncoder().encodeToString(cipher.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new IllegalStateException("Sensitive data encryption failed"); }
    }
    @Override public String convertToEntityAttribute(String value) {
        if (value == null) return null;
        try {
            String[] parts = value.split(":");
            if (parts.length!=3 || !parts[0].equals("v1")) throw new IllegalArgumentException();
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE,key,new GCMParameterSpec(128,Base64.getDecoder().decode(parts[1])));
            cipher.updateAAD("loan-origin-v1".getBytes(StandardCharsets.UTF_8));
            return new String(cipher.doFinal(Base64.getDecoder().decode(parts[2])),StandardCharsets.UTF_8);
        } catch (Exception e) { throw new IllegalStateException("Sensitive data decryption failed"); }
    }
}

