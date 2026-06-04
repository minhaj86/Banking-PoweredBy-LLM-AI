package com.example.ledger.domain.entity;

import com.example.ledger.domain.enums.Side;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Immutable;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "journal_entry")
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JournalEntry {
    @Id private UUID id;
    @Column(nullable = false) private UUID ledgerId;
    @Column(name = "transaction_id", nullable = false) private UUID transactionId;
    @Column(nullable = false) private UUID accountId;
    @Column(nullable = false) private int lineNumber;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 6) private Side side;
    @Column(nullable = false, columnDefinition = "numeric") private BigDecimal amount;
    @Column(length = 500) private String description;
}
