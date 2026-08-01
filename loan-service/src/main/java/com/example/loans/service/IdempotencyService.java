package com.example.loans.service;

import com.example.loans.exception.LoanException;
import com.example.loans.mapper.Json;
import com.example.loans.security.Actor;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.Supplier;

@Service @RequiredArgsConstructor
public class IdempotencyService {
    private final JdbcTemplate jdbc;
    private final Json json;
    @Transactional(propagation=Propagation.MANDATORY)
    public UUID execute(Actor a,String operation,String target,String key,Object payload,Supplier<UUID> work) {
        if (key==null || !key.matches("[A-Za-z0-9._:-]{1,128}")) throw new LoanException("INVALID_IDEMPOTENCY_KEY",org.springframework.http.HttpStatus.BAD_REQUEST,"Idempotency-Key requires 1..128 safe ASCII characters");
        String fingerprint=hash(json.write(payload).getBytes(StandardCharsets.UTF_8));
        int inserted=jdbc.update("INSERT INTO origination.request_claim(tenant,caller,operation,target,key,fingerprint) VALUES(?,?,?,?,?,?) ON CONFLICT DO NOTHING",a.tenant(),a.subject(),operation,target,key,fingerprint);
        if(inserted==0) {
            var result=jdbc.queryForMap("SELECT fingerprint,result_id FROM origination.request_claim WHERE tenant=? AND caller=? AND operation=? AND target=? AND key=?",a.tenant(),a.subject(),operation,target,key);
            if(!fingerprint.equals(result.get("fingerprint"))) throw LoanException.conflict("IDEMPOTENCY_CONFLICT","Key was used with a different business payload");
            return (UUID)result.get("result_id");
        }
        UUID result=work.get();
        jdbc.update("UPDATE origination.request_claim SET result_id=? WHERE tenant=? AND caller=? AND operation=? AND target=? AND key=?",result,a.tenant(),a.subject(),operation,target,key);
        return result;
    }
    public static String hash(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch(Exception e) { throw new IllegalStateException("SHA-256 unavailable"); }
    }
}

