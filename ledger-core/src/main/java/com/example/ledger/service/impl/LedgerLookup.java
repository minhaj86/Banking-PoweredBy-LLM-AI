package com.example.ledger.service.impl;

import com.example.ledger.domain.entity.*;
import com.example.ledger.domain.repository.*;
import com.example.ledger.exception.LedgerException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class LedgerLookup {
    private final LedgerRepository ledgers;
    private final AccountRepository accounts;
    private final JournalRepository journals;

    public Ledger ledger(UUID id) { return ledgers.findById(id).orElseThrow(() -> LedgerException.missing("Ledger")); }
    public Account account(UUID ledgerId, UUID id) {
        return accounts.findByIdAndLedgerId(id, ledgerId).orElseThrow(() -> LedgerException.missing("Account"));
    }
    public JournalTransaction transaction(UUID ledgerId, UUID id) {
        return journals.findPosted(ledgerId, id).orElseThrow(() -> LedgerException.missing("Transaction"));
    }
}
