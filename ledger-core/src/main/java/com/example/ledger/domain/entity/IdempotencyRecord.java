package com.example.ledger.domain.entity;

import java.time.Instant;
import java.util.UUID;

/** JDBC-managed reservation; its deferred transaction FK prevents an unfinished claim from committing. */
public record IdempotencyRecord(String fingerprint, UUID transactionId, int responseStatus, Instant createdAt) {}
