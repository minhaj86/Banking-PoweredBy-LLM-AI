package com.example.ledger.domain.valueobject;

import com.example.ledger.exception.LedgerException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.regex.Pattern;

public record Money(BigDecimal value) {
    public static final BigDecimal MAX_AMOUNT = new BigDecimal("999999999999.99");
    public static final BigDecimal MAX_TRANSACTION_TOTAL = new BigDecimal("9999999999999.99");
    public static final BigDecimal ZERO = new BigDecimal("0.00");
    private static final Pattern DECIMAL = Pattern.compile("[0-9]+(?:\\.[0-9]+)?");

    public Money {
        if (value == null || value.signum() <= 0 || value.compareTo(MAX_AMOUNT) > 0) {
            throw invalid();
        }
        try {
            value = value.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            throw invalid();
        }
    }

    public static Money parse(String text) {
        if (text == null || text.length() > 64 || !DECIMAL.matcher(text).matches()) {
            throw invalid();
        }
        return new Money(new BigDecimal(text));
    }

    public static String format(BigDecimal value) {
        return value.setScale(2, RoundingMode.UNNECESSARY).toPlainString();
    }

    private static LedgerException invalid() {
        return LedgerException.invalid("INVALID_AMOUNT",
                "Amount must be a positive decimal string, at most 999999999999.99, with no rounding required to two decimals");
    }
}
