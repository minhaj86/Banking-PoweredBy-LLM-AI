package com.example.bff.api;

import com.example.bff.config.BffProperties;
import com.example.bff.security.*;
import jakarta.servlet.http.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;
import java.util.*;

@RestController @RequiredArgsConstructor
public class BffController {
    private final TokenRelay relay;private final BackendProxy proxy;private final BffProperties properties;
    @GetMapping("/bff/csrf")public Map<String,String> csrf(CsrfToken token){return Map.of("token",token.getToken(),"headerName",token.getHeaderName());}
    @GetMapping("/bff/me")public Map<String,Object> me(HttpServletRequest req,HttpServletResponse res,Authentication auth){
        var who=relay.identity(req,res,auth);
        var capabilities=new LinkedHashSet<String>();
        if(who.roles().contains("APPLICANT"))capabilities.add("APPLY");
        if(who.staff()&&who.strong()) {
            if(who.roles().contains("LOAN_OFFICER"))capabilities.add("REVIEW");
            if(who.roles().contains("COMPLIANCE_REVIEWER"))capabilities.add("COMPLIANCE");
            if(who.roles().contains("OPERATIONS"))capabilities.add("OPERATE");
            capabilities.add("CONFIGURATION");
        }
        return Map.of("identityKey",who.key(),"tenant",who.tenant(),"displayName",who.name(),"emailVerified",who.emailVerified(),"roles",who.roles(),"capabilities",capabilities,
            "staffStepUpRequired",who.staff()&&!who.strong(),"accountUrl",properties.getIssuer()+"/account/","instance",properties.getInstanceId());
    }
    @GetMapping("/bff/login")public void login(HttpServletRequest req,HttpServletResponse res,@RequestParam(defaultValue="/app")String returnTo,@RequestParam(defaultValue="false")boolean staff,@RequestParam(defaultValue="false")boolean register)throws IOException {
        String safe=SafeReturnPath.validate(returnTo);req.getSession().setAttribute("BFF_RETURN_PATH",safe);req.getSession().setAttribute("BFF_STAFF_LOGIN",staff);
        req.getSession().setAttribute("BFF_REGISTER",register);
        res.sendRedirect("/oauth2/authorization/keycloak");
    }
    @GetMapping(value="/bff/product",produces=MediaType.APPLICATION_JSON_VALUE)public byte[] product(){return proxy.publicProduct();}
    @RequestMapping(value="/bff/api/**",method={RequestMethod.GET,RequestMethod.POST,RequestMethod.PUT})
    public void api(HttpServletRequest req,HttpServletResponse res,Authentication auth)throws IOException{proxy.forward(req,res,auth);}
}

