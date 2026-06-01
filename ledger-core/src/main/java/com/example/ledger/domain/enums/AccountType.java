package com.example.ledger.domain.enums;

import java.math.BigDecimal;

public enum AccountType {
    ASSET, LIABILITY, EQUITY, REVENUE, EXPENSE;

    public Side normalSide() {
        return this == ASSET || this == EXPENSE ? Side.DEBIT : Side.CREDIT;
    }

    public BigDecimal balance(BigDecimal debits, BigDecimal credits) {
        return normalSide() == Side.DEBIT ? debits.subtract(credits) : credits.subtract(debits);
    }
}
