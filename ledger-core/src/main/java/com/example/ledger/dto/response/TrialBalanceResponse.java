package com.example.ledger.dto.response;

import com.example.ledger.domain.enums.*;
import java.util.List;
import java.util.UUID;

public record TrialBalanceResponse(UUID ledgerId, LedgerCurrency currency, List<Row> accounts,
        String totalDebits, String totalCredits, String closingDebits, String closingCredits) {
    public TrialBalanceResponse { accounts = List.copyOf(accounts); }

    public record Row(UUID accountId, String code, String name, AccountType type, Side normalBalanceSide,
            String totalDebits, String totalCredits, String balance, String closingDebit, String closingCredit) {}
}
