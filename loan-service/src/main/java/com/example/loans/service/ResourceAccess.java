package com.example.loans.service;

import com.example.loans.domain.entity.*;
import com.example.loans.domain.repository.*;
import com.example.loans.exception.LoanException;
import com.example.loans.security.Actor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component @RequiredArgsConstructor
public class ResourceAccess {
    private final ApplicationRepository applications;
    private final LoanRepository loans;
    public ApplicationRecord application(Actor actor,java.util.UUID id,boolean lock) {
        var a=(lock?applications.lock(id):applications.findById(id)).orElseThrow(LoanException::missing);
        if(!a.getTenant().equals(actor.tenant()) || (!actor.reviewer() && !a.getApplicant().equals(actor.subject()))) throw LoanException.missing();
        if(!a.getApplicant().equals(actor.subject())) Actor.requireStaffAssurance();
        if(a.getPurgedAt()!=null) throw new LoanException("RESOURCE_ERASED",org.springframework.http.HttpStatus.GONE,"Sensitive application content was erased under retention policy");
        return a;
    }
    public LoanRecord loan(Actor actor,java.util.UUID id,boolean lock) {
        var l=(lock?loans.lock(id):loans.findById(id)).orElseThrow(LoanException::missing);
        if(!l.getTenant().equals(actor.tenant()) || (!actor.reviewer()&&!l.getApplicant().equals(actor.subject()))) throw LoanException.missing();
        if(!l.getApplicant().equals(actor.subject())) Actor.requireStaffAssurance();
        return l;
    }
    public void expected(long current,long expected) { if(current!=expected) throw LoanException.conflict("VERSION_CONFLICT","Resource changed; reload and submit the current version"); }
    public void independentReviewer(Actor actor,ApplicationRecord app) {
        Actor.requireStaffAssurance();
        if(app.getApplicant().equals(actor.subject())) throw new org.springframework.security.access.AccessDeniedException("Staff cannot review their own application");
    }
}

