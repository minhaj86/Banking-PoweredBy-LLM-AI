package com.example.ledger.domain.valueobject;

import com.example.ledger.domain.enums.Side;
import java.util.UUID;

public record PostingLine(UUID accountId, Side side, Money amount, String description) {
    public PostingLine reversed() {
        return new PostingLine(accountId, side.opposite(), amount, description);
    }
}
