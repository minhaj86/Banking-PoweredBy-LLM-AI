package com.example.bff.security;

import com.example.bff.config.BffProperties;
import org.springframework.stereotype.Component;
import javax.crypto.*;
import javax.crypto.spec.*;
import java.security.SecureRandom;
import java.util.*;

@Component
public class StateCipher {
    private final SecretKeySpec key; private final SecureRandom random=new SecureRandom();
    public StateCipher(BffProperties config) {
        byte[] bytes=Base64.getDecoder().decode(Objects.requireNonNull(config.getEncryptionKey(),"BFF encryption key required"));
        if(bytes.length!=32) throw new IllegalStateException("BFF key requires 32 bytes");
        key=new SecretKeySpec(bytes,"AES");
    }
    public byte[] encrypt(byte[] plain) {
        if(plain==null) return null;
        try {
            byte[] iv=new byte[12]; random.nextBytes(iv); Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE,key,new GCMParameterSpec(128,iv));cipher.updateAAD("bff-state-v1".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            byte[] encrypted=cipher.doFinal(plain); byte[] out=new byte[iv.length+encrypted.length];System.arraycopy(iv,0,out,0,iv.length);System.arraycopy(encrypted,0,out,iv.length,encrypted.length);return out;
        } catch(Exception ex){throw new IllegalStateException("Authentication state encryption failed");}
    }
    public byte[] decrypt(byte[] bytes) {
        if(bytes==null) return null;
        try {
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key,new GCMParameterSpec(128,Arrays.copyOfRange(bytes,0,12)));
            cipher.updateAAD("bff-state-v1".getBytes(java.nio.charset.StandardCharsets.US_ASCII));return cipher.doFinal(Arrays.copyOfRange(bytes,12,bytes.length));
        } catch(Exception ex){throw new IllegalStateException("Authentication state authentication failed");}
    }
    public String seal(String text) {return Base64.getEncoder().encodeToString(encrypt(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}
    public String open(String text) {return new String(decrypt(Base64.getDecoder().decode(text)),java.nio.charset.StandardCharsets.UTF_8);}
}

