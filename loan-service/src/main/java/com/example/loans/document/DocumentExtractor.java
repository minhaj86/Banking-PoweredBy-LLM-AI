package com.example.loans.document;

import com.example.loans.config.LoanProperties;
import com.example.loans.domain.entity.DocumentRecord;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.context.annotation.*;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
import javax.imageio.ImageIO;
import java.io.*;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.*;

@Component @RequiredArgsConstructor
public class DocumentExtractor {
    private final LoanProperties config;
    private final OcrAdapter ocr;
    private static final Pattern FACT=Pattern.compile("(?i)^\\s*(net_income|gross_income|debt_repayment|non_debt_expenses)\\s*[:=]\\s*([0-9]{1,12}(?:\\.[0-9]{1,2})?)\\s+(SEK|EUR|USD)\\s+(MONTHLY|ANNUAL|WEEKLY)\\s*$");
    public String detect(byte[] bytes) {
        if(bytes.length==0 || bytes.length>config.getMaxFileBytes()) throw new IllegalArgumentException("FILE_SIZE");
        if(bytes.length>=5 && new String(bytes,0,5,java.nio.charset.StandardCharsets.US_ASCII).equals("%PDF-")) return "application/pdf";
        boolean jpeg=bytes.length>=3 && (bytes[0]&255)==255 && (bytes[1]&255)==216 && (bytes[2]&255)==255;
        boolean png=bytes.length>=8 && Arrays.equals(Arrays.copyOf(bytes,8),new byte[]{(byte)137,80,78,71,13,10,26,10});
        if(jpeg||png) {
            try(var input=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                var readers=ImageIO.getImageReaders(input); if(!readers.hasNext()) throw new IllegalArgumentException("INVALID_IMAGE");
                var reader=readers.next();
                try { reader.setInput(input); if((long)reader.getWidth(0)*reader.getHeight(0)>40000000L) throw new IllegalArgumentException("IMAGE_PIXELS"); }
                finally { reader.dispose(); }
            } catch(IOException ex) { throw new IllegalArgumentException("INVALID_IMAGE"); }
            return jpeg?"image/jpeg":"image/png";
        }
        throw new IllegalArgumentException("UNSUPPORTED_MEDIA");
    }
    public FinancialEvidence extract(byte[] bytes,DocumentRecord doc) {
        var facts=new ArrayList<FinancialEvidence.Fact>(); var limitations=new ArrayList<String>();
        try {
            if(doc.getMediaType().equals("application/pdf")) {
                try(var pdf=Loader.loadPDF(bytes)) {
                    if(pdf.isEncrypted() || pdf.getNumberOfPages()<1 || pdf.getNumberOfPages()>config.getMaxPages()) throw new IllegalArgumentException("PDF_PAGE_LIMIT_OR_ENCRYPTION");
                    if(pdf.getDocumentCatalog().getAcroForm()!=null || pdf.getDocumentCatalog().getOpenAction()!=null) limitations.add("EMBEDDED_CONTENT_IGNORED");
                    for(int page=1;page<=pdf.getNumberOfPages();page++) {
                        var stripper=new PDFTextStripper(); stripper.setStartPage(page); stripper.setEndPage(page);
                        var writer=new LimitedWriter(20000); stripper.writeText(pdf,writer);
                        String text=writer.toString();
                        if(text.isBlank()) {
                            var result=ocr.extract(bytes,doc.getMediaType(),page);
                            if(!result.reliable()) { limitations.add("MANUAL_EXTRACTION_REQUIRED"); continue; }
                            text=result.text();
                        }
                        parse(text,doc.getId(),doc.getPeriodStart(),doc.getPeriodEnd(),page,facts);
                    }
                }
            } else {
                var result=ocr.extract(bytes,doc.getMediaType(),1);
                if(!result.reliable()) limitations.add("MANUAL_EXTRACTION_REQUIRED");
                else parse(result.text(),doc.getId(),doc.getPeriodStart(),doc.getPeriodEnd(),1,facts);
            }
            if(facts.isEmpty()) limitations.add("NO_SUPPORTED_FINANCIAL_FACTS");
            limitations.add("EXTRACTION_DOES_NOT_VERIFY_AUTHENTICITY");
            return new FinancialEvidence(List.copyOf(facts),List.copyOf(new LinkedHashSet<>(limitations)));
        } catch(IOException ex) { return new FinancialEvidence(List.of(),List.of("MALFORMED_OR_RESOURCE_LIMITED_DOCUMENT","MANUAL_EXTRACTION_REQUIRED")); }
    }
    private void parse(String text,UUID document,LocalDate start,LocalDate end,int page,List<FinancialEvidence.Fact> facts) {
        String[] lines=text.split("\\R");
        for(int line=0;line<lines.length;line++) {
            var match=FACT.matcher(lines[line]);
            if(match.matches()) facts.add(new FinancialEvidence.Fact(match.group(1).toLowerCase(Locale.ROOT),match.group(2),match.group(3).toUpperCase(Locale.ROOT),
                match.group(4).toUpperCase(Locale.ROOT),start,end,FinancialEvidence.Provenance.DOCUMENT_EXTRACTED,
                new FinancialEvidence.Source(document,page,line+1,match.group().strip())));
        }
        // Unrecognised prose, URLs, embedded instructions and identifiers never leave this parser.
    }
    private static class LimitedWriter extends StringWriter {
        private final int limit; LimitedWriter(int limit) { this.limit=limit; }
        @Override public void write(char[] chars,int off,int len) { if(getBuffer().length()+len>limit) throw new IllegalArgumentException("TEXT_LIMIT"); super.write(chars,off,len); }
        @Override public void write(String str,int off,int len) { if(getBuffer().length()+len>limit) throw new IllegalArgumentException("TEXT_LIMIT"); super.write(str,off,len); }
    }
    @Configuration static class Config {
        @Bean @org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean(OcrAdapter.class)
        OcrAdapter ocr() { return (bytes,media,page)->new OcrAdapter.Result(null,false); }
    }
}

