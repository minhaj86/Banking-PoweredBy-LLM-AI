package com.example.ledger.domain.entity;

import com.example.ledger.domain.enums.AccountStatus;
import com.example.ledger.domain.enums.AccountType;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Entity
@Table(name = "account")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Account {
    @Id private UUID id;
    @Column(nullable = false, updatable = false) private UUID ledgerId;
    @Column(nullable = false, updatable = false, length = 50) private String code;
    @Column(nullable = false, updatable = false, length = 200) private String name;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 10) private AccountType type;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 6) private AccountStatus status;
    @Column(nullable = false, updatable = false) private boolean enforceNonNegativeBalance;
    @Column(nullable = false, updatable = false) private Instant createdAt;
    @Column(updatable = false, length = 200) private String externalReference;

    public static Account create(UUID ledgerId, String code, String name, AccountType type, boolean nonNegative) {
        return new Account(UUID.randomUUID(), ledgerId, code, name, type, AccountStatus.ACTIVE,
                nonNegative, Instant.now().truncatedTo(ChronoUnit.MICROS), null);
    }
}
