package com.example.ledger.domain.enums;

public enum Side {
    DEBIT, CREDIT;

    public Side opposite() {
        return this == DEBIT ? CREDIT : DEBIT;
    }
}
