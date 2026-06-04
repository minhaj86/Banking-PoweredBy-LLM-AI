package com.example.ledger.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Immutable;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "journal_transaction")
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JournalTransaction {
    @Id private UUID id;
    @Column(nullable = false) private UUID ledgerId;
    @Column(length = 200) private String externalReference;
    @Column(nullable = false, length = 500) private String description;
    @Column(nullable = false) private Instant recordedAt;
    private UUID reversalOfTransactionId;
    @Column(length = 500) private String reversalReason;
    @Column(nullable = false, length = 6) private String state;
    @OneToMany(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id", insertable = false, updatable = false)
    @OrderBy("lineNumber ASC")
    private List<JournalEntry> entries;
}
