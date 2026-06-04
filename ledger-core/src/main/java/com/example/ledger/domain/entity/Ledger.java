package com.example.ledger.domain.entity;

import com.example.ledger.domain.enums.LedgerCurrency;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Entity
@Table(name = "ledger")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Ledger {
    @Id private UUID id;
    @Column(nullable = false, updatable = false, length = 50) private String code;
    @Column(nullable = false, updatable = false, length = 200) private String name;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 3) private LedgerCurrency currency;
    @Column(nullable = false, updatable = false) private Instant createdAt;

    public static Ledger create(String code, String name, LedgerCurrency currency) {
        return new Ledger(UUID.randomUUID(), code, name, currency, Instant.now().truncatedTo(ChronoUnit.MICROS));
    }
}
