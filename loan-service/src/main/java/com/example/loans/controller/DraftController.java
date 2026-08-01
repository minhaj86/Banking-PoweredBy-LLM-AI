package com.example.loans.controller;

import com.example.loans.dto.request.ApplicationInput;
import com.example.loans.dto.response.ApplicationView;
import com.example.loans.security.Actor;
import com.example.loans.service.ApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import java.util.UUID;

@RestController @RequestMapping("/api/v1/drafts") @RequiredArgsConstructor @PreAuthorize("hasRole('APPLICANT')")
public class DraftController {
    private final ApplicationService applications;
    @PostMapping public ApplicationView create(@RequestHeader("Idempotency-Key") String key,@RequestBody ApplicationInput input){return applications.createDraft(Actor.current(),key,input);}
    @PutMapping("/{id}") public ApplicationView save(@PathVariable UUID id,@RequestHeader("If-Match") long version,@RequestHeader("Idempotency-Key") String key,@RequestBody ApplicationInput input){return applications.saveDraft(Actor.current(),id,version,key,input);}
}
