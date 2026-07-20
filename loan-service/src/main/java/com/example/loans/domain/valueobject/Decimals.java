package com.example.loans.domain.valueobject;

import com.example.loans.exception.LoanException;
import java.math.*;

public final class Decimals {
    private Decimals() {}
    public static BigDecimal money(String value) {
        if (value == null || !value.matches("[0-9]{1,12}(\\.[0-9]{1,2})?")) throw LoanException.invalid("INVALID_MONEY", "Money requires an unsigned decimal string with at most two fractional digits");
        return new BigDecimal(value).setScale(2, RoundingMode.UNNECESSARY);
    }
    public static BigDecimal optional(String value) { return value == null ? null : money(value); }
    public static String format(BigDecimal value) { return value == null ? null : value.toPlainString(); }
}
