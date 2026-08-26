package com.example.loans;

import com.example.loans.dto.request.ApplicationInput;
import com.example.loans.security.Actor;
import com.example.loans.dto.request.DecisionRequest;
import com.example.loans.dto.response.AssessmentView;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.font.*;
import java.io.*;
import java.time.*;
import java.util.*;

public final class TestFixtures {
    private TestFixtures() {}
    public static final Actor APPLICANT=new Actor("demo","synthetic-applicant",Set.of("APPLICANT"));
    public static final Actor OFFICER=new Actor("demo","synthetic-officer",Set.of("LOAN_OFFICER"));
    public static final Actor OPERATIONS=new Actor("demo","synthetic-operations",Set.of("OPERATIONS"));
    public static ApplicationInput input() { return input("30000.00","12000.00"); }
    public static ApplicationInput input(String net,String expenses) {
        LocalDate today=LocalDate.now(ZoneOffset.UTC);
        return new ApplicationInput("personal-sek",1,"100000.00","SEK",60,"Synthetic home improvement",
            new ApplicationInput.Identity("Synthetic Applicant",today.minusYears(35),"SYNTHETIC-ID","synthetic@example.invalid","+460000000","Synthetic Street 1","SE"),
            new ApplicationInput.Employment("EMPLOYED",today.minusYears(2),"Synthetic Employer"),
            new ApplicationInput.Income("40000.00",net,"MONTHLY","SEK",today.minusMonths(1).withDayOfMonth(1),today.minusMonths(1).withDayOfMonth(today.minusMonths(1).lengthOfMonth()),false,List.of()),
            new ApplicationInput.Expenses("8000.00",expenses,1,"SEK",true),List.of(),
            new ApplicationInput.Kyc("demo-1",Instant.now().minusSeconds(1),"Personal loan","Employment",List.of("SE"),false),
            new ApplicationInput.Privacy("demo-1",Instant.now().minusSeconds(1),true));
    }
    public static byte[] pdf(String... lines) {
        try(var doc=new PDDocument(); var out=new ByteArrayOutputStream()) {
            var page=new PDPage(); doc.addPage(page);
            try(var content=new PDPageContentStream(doc,page)) {
                content.beginText(); content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA),12); content.newLineAtOffset(40,740);
                for(String line:lines) { content.showText(line); content.newLineAtOffset(0,-20); } content.endText();
            }
            doc.save(out); return out.toByteArray();
        } catch(IOException ex) { throw new IllegalStateException(ex); }
    }
    public static DecisionRequest approval(AssessmentView a) {
        var findings=new HashSet<>(a.evidenceLimitations()); findings.addAll(a.affordability().findings()); findings.addAll(a.affordability().blockers());
        a.ai().advisory().findings().forEach(f->findings.add(f.code().name())); a.ai().advisory().inconsistencies().forEach(f->findings.add(f.code().name()));
        a.ai().advisory().limitations().forEach(l->findings.add(l.name()));
        return new DecisionRequest(DecisionRequest.Outcome.APPROVE,List.of("EVIDENCE_REVIEWED"),"Reviewed synthetic evidence, confirmed financial facts and affordability.",
            "Your synthetic application is approved subject to agreement and later funding.",Set.copyOf(a.documentIds()),findings,true,true,true);
    }
}

