package com.example.loans.service;

import com.example.loans.security.Actor;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.UUID;

@Service @RequiredArgsConstructor
public class AuditService {
    private final JdbcTemplate jdbc;
    public void record(Actor actor,String action,UUID application,int revision,UUID resource) {
        record(actor.tenant(),actor.subject(),action,application,revision,resource);
    }
    public void record(String tenant,String actor,String action,UUID application,int revision,UUID resource) {
        jdbc.update("INSERT INTO origination.audit_event(id,tenant,actor,action,application_id,revision,resource_id) VALUES(?,?,?,?,?,?,?)",UUID.randomUUID(),tenant,actor,action,application,revision,resource);
    }
}

