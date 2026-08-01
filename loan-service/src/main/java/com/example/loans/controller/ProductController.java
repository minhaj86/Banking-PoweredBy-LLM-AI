package com.example.loans.controller;

import com.example.loans.config.LoanProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequiredArgsConstructor
public class ProductController {
    private final LoanProperties config;
    @GetMapping("/api/v1/products/current") public Map<String,Object> product(){
        var p=config.getProduct();Map<String,Object> out=new LinkedHashMap<>();
        out.put("id",p.getId());out.put("version",p.getVersion());out.put("name","Personal loan · SEK");out.put("currency","SEK");out.put("eligible",p.isEligible());out.put("policyVersion",p.getPolicyVersion());
        out.put("minAmount",p.getMinAmount().toPlainString());out.put("maxAmount",p.getMaxAmount().toPlainString());out.put("minMonths",p.getMinMonths());out.put("maxMonths",p.getMaxMonths());out.put("nominalAnnualRate",p.getNominalAnnualRate().toPlainString());
        out.put("questionnaireVersion","demo-1");out.put("privacyNoticeVersion","demo-1");out.put("requiredCategories",p.getRequiredCategories());out.put("requiredPayslips",p.getRequiredPayslips());out.put("evidenceFreshnessDays",p.getEvidenceFreshnessDays());out.put("maxFileBytes",config.getMaxFileBytes());out.put("mediaTypes",List.of("application/pdf","image/jpeg","image/png"));out.put("maxPages",config.getMaxPages());return out;
    }
}
