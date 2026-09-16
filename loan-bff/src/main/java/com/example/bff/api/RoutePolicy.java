package com.example.bff.api;

import com.example.bff.security.TokenRelay.Identity;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import java.util.regex.Pattern;

@Component
public class RoutePolicy {
    private static final String ID="[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";
    public void authorise(String method,String path,Identity who) {
        boolean read="GET".equals(method);String role=null;boolean allowed=false;
        if(read && path.equals("/products/current"))allowed=true;
        else if((path.equals("/drafts")&&method.equals("POST"))||(path.matches("/drafts/"+ID)&&method.equals("PUT"))) {allowed=true;role="APPLICANT";}
        else if(read && (path.equals("/loan-applications")||path.matches("/loan-applications/"+ID+"(?:/(?:documents(?:/"+ID+"(?:/content)?)?|processing-status|timeline|information-requests|review-requests))?"))){allowed=true;role="READER";}
        else if(read && path.matches("/loans/"+ID+"(?:/(?:terms|repayment-schedule|ledger-account|balance))?")){allowed=true;role="READER";}
        else if(path.matches("/loan-applications/"+ID+"/(?:submit|withdraw|documents|review-requests)") && method.equals("POST")){allowed=true;role="APPLICANT";if(path.endsWith("/submit")&&!who.emailVerified())deny("EMAIL_VERIFICATION_REQUIRED");}
        else if(path.matches("/loan-applications/"+ID+"/(?:decisions|information-requests)")&&method.equals("POST")){allowed=true;role="LOAN_OFFICER";}
        else if(path.matches("/loan-applications/"+ID+"/assessment")&&read){allowed=true;role="LOAN_OFFICER";}
        else if(path.matches("/loan-applications/"+ID+"/documents/"+ID+"/manual-extraction")&&method.equals("POST")){allowed=true;role="LOAN_OFFICER";}
        else if(path.matches("/loan-applications/"+ID+"/verifications")&&read){allowed=true;role="COMPLIANCE_OR_OFFICER";}
        else if(path.matches("/loan-applications/"+ID+"/verification-reviews")&&method.equals("POST")){allowed=true;role="COMPLIANCE_REVIEWER";}
        else if(path.matches("/loans/"+ID+"/provisioning/retry")&&method.equals("POST")){allowed=true;role="OPERATIONS";}
        else if(path.equals("/staff/summary")&&read){allowed=true;role="STAFF";}
        else if(path.equals("/staff/configuration")&&read){allowed=true;role="STAFF";}
        else if(path.equals("/operations/metrics")&&read){allowed=true;role="OPERATIONS";}
        else if(path.equals("/operations/jobs")&&read){allowed=true;role="OPERATIONS";}
        else if(path.equals("/operations/provisioning/reconcile")&&method.equals("POST")){allowed=true;role="OPERATIONS";}
        else if(path.matches("/operations/applications/"+ID+"/jobs/"+ID+"/retry")&&method.equals("POST")){allowed=true;role="OPERATIONS";}
        else if(path.matches("/operations/applications/"+ID+"/legal-hold")&&method.equals("POST")){allowed=true;role="COMPLIANCE_REVIEWER";}
        else if(path.matches("/operations/applications/"+ID+"/audit")&&read){allowed=true;role="REVIEWER";}
        if(!allowed)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"ROUTE_NOT_AVAILABLE");
        if(who.roles().stream().noneMatch(Set.of("APPLICANT","LOAN_OFFICER","COMPLIANCE_REVIEWER","OPERATIONS","PLATFORM_ADMIN")::contains))deny("ROLE_REQUIRED");
        if(role!=null) {
            boolean permitted=switch(role) {
                case "STAFF" -> who.staff();
                case "COMPLIANCE_OR_OFFICER" -> who.roles().contains("LOAN_OFFICER")||who.roles().contains("COMPLIANCE_REVIEWER");
                case "READER" -> who.roles().stream().anyMatch(Set.of("APPLICANT","LOAN_OFFICER","COMPLIANCE_REVIEWER","OPERATIONS")::contains);
                case "REVIEWER" -> who.roles().stream().anyMatch(Set.of("LOAN_OFFICER","COMPLIANCE_REVIEWER","OPERATIONS")::contains);
                default -> who.roles().contains(role);
            };
            if(!permitted)deny("ROLE_REQUIRED");
            if(!role.equals("APPLICANT")&&!role.equals("READER")&&!who.strong())deny("STEP_UP_REQUIRED");
        }
    }
    public static void deny(String code){throw new ResponseStatusException(HttpStatus.FORBIDDEN,code);}
    public Map<String,String> query(jakarta.servlet.http.HttpServletRequest req) {
        var allowed=Set.of("page","size","status","productId","from","to","sort","q","view","enabled");
        Map<String,String> values=new HashMap<>();String query=req.getQueryString();if(query==null)return values;
        if(query.length()>2000)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"QUERY_NOT_ALLOWED");
        for(String pair:query.split("&")){
            String[] parts=pair.split("=",2);String key=java.net.URLDecoder.decode(parts[0],java.nio.charset.StandardCharsets.UTF_8);
            if(!allowed.contains(key)||values.containsKey(key))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"QUERY_NOT_ALLOWED");
            values.put(key,parts.length==2?java.net.URLDecoder.decode(parts[1],java.nio.charset.StandardCharsets.UTF_8):"");
        }return values;
    }
}

