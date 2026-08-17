package com.example.loans.controller;

import com.example.loans.config.LoanProperties;
import com.example.loans.security.Actor;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/api/v1/staff") @RequiredArgsConstructor
public class StaffController {
    private final JdbcTemplate jdbc;
    private final LoanProperties config;
    @GetMapping("/summary") public Map<String,Object> summary(){
        var actor=Actor.current();actor.require("LOAN_OFFICER","COMPLIANCE_REVIEWER","OPERATIONS","PLATFORM_ADMIN");
        Map<String,Object> out=new LinkedHashMap<>();
        if(actor.reviewer())out.put("applications",jdbc.queryForList("SELECT state,count(*) AS count FROM origination.application WHERE tenant=? AND purged_at IS NULL GROUP BY state ORDER BY state",actor.tenant()));
        if(actor.has("OPERATIONS"))out.put("provisioning",jdbc.queryForList("SELECT provisioning_status AS state,count(*) AS count FROM origination.loan WHERE tenant=? GROUP BY provisioning_status",actor.tenant()));
        return out;
    }
    @GetMapping("/configuration") public Map<String,Object> configuration(){
        Actor.current().require("LOAN_OFFICER","COMPLIANCE_REVIEWER","OPERATIONS","PLATFORM_ADMIN");
        var p=config.getProduct();Map<String,Object> product=new LinkedHashMap<>();
        product.put("id",p.getId());product.put("version",p.getVersion());product.put("policyVersion",p.getPolicyVersion());product.put("eligible",p.isEligible());
        product.put("minAmount",p.getMinAmount().toPlainString());product.put("maxAmount",p.getMaxAmount().toPlainString());product.put("minMonths",p.getMinMonths());product.put("maxMonths",p.getMaxMonths());
        product.put("nominalAnnualRate",p.getNominalAnnualRate().toPlainString());product.put("stressAnnualRate",p.getStressAnnualRate().toPlainString());product.put("minimumResidual",p.getMinimumResidual().toPlainString());product.put("maximumDsti",p.getMaximumDsti().toPlainString());
        product.put("evidenceFreshnessDays",p.getEvidenceFreshnessDays());product.put("assessmentValidityDays",p.getAssessmentValidityDays());product.put("requiredCategories",p.getRequiredCategories());
        return Map.of("product",product,"editingSupported",false,"syntheticVerification",config.isSyntheticVerification(),"aiProvider",config.getAi().getProvider());
    }
}
