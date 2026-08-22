package com.example.loans.service;

import com.example.loans.security.Actor;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service @RequiredArgsConstructor
public class OperationalMetrics {
    private final JdbcTemplate jdbc;
    @Transactional(readOnly=true) public Map<String,Object> snapshot(Actor actor) {
        actor.require("OPERATIONS");
        var applications=jdbc.queryForList("SELECT state,count(*) AS count FROM origination.application WHERE tenant=? GROUP BY state",actor.tenant());
        var queue=jdbc.queryForList("""
            SELECT j.kind,j.state,count(*) AS count FROM origination.durable_job j WHERE
            j.target_id IN (SELECT id FROM origination.application WHERE tenant=?)
            OR j.target_id IN (SELECT d.id FROM origination.document d JOIN origination.application a ON a.id=d.application_id WHERE a.tenant=?)
            OR j.target_id IN (SELECT id FROM origination.loan WHERE tenant=?)
            GROUP BY j.kind,j.state
            """,actor.tenant(),actor.tenant(),actor.tenant());
        long backlog=queue.stream().filter(r->Set.of("READY","RUNNING").contains(r.get("state"))).mapToLong(r->((Number)r.get("count")).longValue()).sum();
        var runtime=Runtime.getRuntime();
        return Map.of("applications",applications,"jobs",queue,"backlog",backlog,"heapUsedBytes",runtime.totalMemory()-runtime.freeMemory(),
            "heapCommittedBytes",runtime.totalMemory(),"availableProcessors",runtime.availableProcessors(),
            "measurementScope","This instance plus tenant database backlog. AI throughput requires separate provider measurements.");
    }
}

