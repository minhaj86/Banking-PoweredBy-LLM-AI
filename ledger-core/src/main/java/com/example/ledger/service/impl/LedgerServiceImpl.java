package com.example.ledger.service.impl;

import com.example.ledger.domain.entity.Ledger;
import com.example.ledger.domain.repository.LedgerRepository;
import com.example.ledger.dto.request.CreateLedgerRequest;
import com.example.ledger.dto.response.LedgerResponse;
import com.example.ledger.mapper.LedgerMapper;
import com.example.ledger.service.LedgerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LedgerServiceImpl implements LedgerService {
    private final LedgerRepository repository;
    private final LedgerLookup lookup;
    private final LedgerMapper mapper;
    private final RequestValidation validation;

    @Override
    @Transactional
    public LedgerResponse create(CreateLedgerRequest request) {
        validation.validate(request);
        return mapper.ledger(repository.saveAndFlush(Ledger.create(request.code(), request.name(), request.currency())));
    }

    @Override
    public LedgerResponse get(UUID ledgerId) { return mapper.ledger(lookup.ledger(ledgerId)); }
}
