package com.example.ledger.domain.valueobject;

import com.example.ledger.domain.enums.AccountType;
import com.example.ledger.domain.enums.Side;
import com.example.ledger.exception.LedgerException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class PostingTest {
    private final UUID a = UUID.randomUUID();
    private final UUID b = UUID.randomUUID();

    private PostingLine line(UUID account, Side side, String amount) {
        return new PostingLine(account, side, Money.parse(amount), null);
    }

    @Test
    void balancesCompoundPostingsAndReversesAllLines() {
        var posting = new Posting("ref", "compound", List.of(
                line(a, Side.DEBIT, "10"), line(b, Side.CREDIT, "4"),
                line(b, Side.CREDIT, "6")));
        var reversal = posting.reversed("Correction");
        assertThat(reversal.description()).isEqualTo("Correction");
        assertThat(reversal.externalReference()).isNull();
        for (int i = 0; i < posting.entries().size(); i++) {
            assertThat(reversal.entries().get(i).accountId()).isEqualTo(posting.entries().get(i).accountId());
            assertThat(reversal.entries().get(i).amount()).isEqualTo(posting.entries().get(i).amount());
            assertThat(reversal.entries().get(i).side()).isEqualTo(posting.entries().get(i).side().opposite());
        }
    }

    @Test
    void rejectsUnbalancedAndSingleAccountPostings() {
        assertThatThrownBy(() -> new Posting(null, "bad", List.of(
                line(a, Side.DEBIT, "10"), line(b, Side.CREDIT, "9"))))
                .isInstanceOf(LedgerException.class).hasMessageContaining("Total debits");
        assertThatThrownBy(() -> new Posting(null, "bad", List.of(
                line(a, Side.DEBIT, "10"), line(a, Side.CREDIT, "10"))))
                .isInstanceOf(LedgerException.class).hasMessageContaining("distinct");
        assertThatThrownBy(() -> new Posting(null, "bad", List.of(
                line(a, Side.DEBIT, "10"), line(b, Side.DEBIT, "10"))))
                .isInstanceOf(LedgerException.class).hasMessageContaining("Total debits");
    }

    @Test
    void capsTransactionTotal() {
        var lines = new java.util.ArrayList<PostingLine>();
        for (int i = 0; i < 11; i++) {
            lines.add(line(a, Side.DEBIT, Money.MAX_AMOUNT.toPlainString()));
            lines.add(line(b, Side.CREDIT, Money.MAX_AMOUNT.toPlainString()));
        }
        assertThatThrownBy(() -> new Posting(null, "too large", lines))
                .isInstanceOf(LedgerException.class).hasMessageContaining("Each side");
    }

    @ParameterizedTest
    @EnumSource(AccountType.class)
    void derivesNormalDirection(AccountType type) {
        var expected = type == AccountType.ASSET || type == AccountType.EXPENSE ? "7" : "-7";
        assertThat(type.balance(new BigDecimal("10"), new BigDecimal("3"))).isEqualByComparingTo(expected);
        assertThat(type.normalSide()).isEqualTo(expected.equals("7") ? Side.DEBIT : Side.CREDIT);
    }
}
