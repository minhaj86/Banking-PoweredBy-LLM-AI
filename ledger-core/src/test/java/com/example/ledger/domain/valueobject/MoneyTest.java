package com.example.ledger.domain.valueobject;

import com.example.ledger.exception.LedgerException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

class MoneyTest {
    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "0", "-1", "1.001", "NaN", "Infinity", "1e2", " 1", "+1", ".50",
            "1000000000000.00", "999999999999.999", "1,00"})
    void rejectsInvalidAmounts(String text) {
        assertThatThrownBy(() -> Money.parse(text)).isInstanceOf(LedgerException.class)
                .hasMessageContaining("positive decimal string");
    }

    @Test
    void canonicalizesWithoutRounding() {
        assertThat(Money.parse("0001.0000")).isEqualTo(Money.parse("1"));
        assertThat(Money.parse("0.01").value()).isEqualByComparingTo("0.01");
        assertThat(Money.parse("999999999999.99").value()).isEqualByComparingTo(Money.MAX_AMOUNT);
        assertThat(Money.format(Money.parse("1.2").value())).isEqualTo("1.20");
    }
}
