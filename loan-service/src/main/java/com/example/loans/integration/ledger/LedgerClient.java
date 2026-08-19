package com.example.loans.integration.ledger;
import java.util.UUID;
public interface LedgerClient {
    record Account(UUID id,UUID ledgerId,String externalReference,String type,String normalBalanceSide,String status,String currency) {}
    Account provision(UUID loanId);
    record Balance(UUID accountId,String currency,String normalBalanceSide,String totalDebits,String totalCredits,String balance) {}
    Balance balance(UUID ledger,UUID account);
}

