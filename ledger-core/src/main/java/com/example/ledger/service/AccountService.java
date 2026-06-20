package com.example.ledger.service;

import com.example.ledger.dto.request.CreateAccountRequest;
import com.example.ledger.dto.request.ProvisionAccountRequest;
import com.example.ledger.dto.response.AccountResponse;
import java.util.UUID;

public interface AccountService {
    AccountResponse create(UUID ledgerId, CreateAccountRequest request);
    AccountResponse provision(UUID ledgerId, String key, ProvisionAccountRequest request);
    AccountResponse byExternalReference(UUID ledgerId, String externalReference);
    AccountResponse get(UUID ledgerId, UUID accountId);
    AccountResponse close(UUID ledgerId, UUID accountId);
}
