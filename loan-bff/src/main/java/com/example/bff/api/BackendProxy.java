package com.example.bff.api;

import com.example.bff.config.BffProperties;
import com.example.bff.security.TokenRelay;
import jakarta.servlet.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;

@Service
public class BackendProxy {
    private final BffProperties properties;private final RoutePolicy policy;private final TokenRelay relay;private final HttpClient http;
    private final java.util.concurrent.Semaphore uploads=new java.util.concurrent.Semaphore(2);
    public BackendProxy(BffProperties p,RoutePolicy policy,TokenRelay relay){
        this.properties=p;this.policy=policy;this.relay=relay;
        http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
    }
    public void forward(HttpServletRequest req,HttpServletResponse res,Authentication auth)throws IOException {
        var identity=relay.identity(req,res,auth);String path=req.getRequestURI().substring("/bff/api".length());
        policy.authorise(req.getMethod(),path,identity);var parameters=policy.query(req);
        if("staff".equals(parameters.get("view"))&&!identity.strong())RoutePolicy.deny("STEP_UP_REQUIRED");
        boolean multipart=Optional.ofNullable(req.getContentType()).orElse("").startsWith("multipart/form-data");
        long limit=multipart?11534336:65536;
        if(req.getContentLengthLong()>limit)throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,"REQUEST_TOO_LARGE");
        String query=req.getQueryString();
        var uri=URI.create(properties.getLoanUrl()+"/api/v1"+path+(query==null?"":"?"+query));
        var builder=HttpRequest.newBuilder(uri).timeout(properties.getDownstreamTimeout()).header("Authorization","Bearer "+identity.jwt().getTokenValue())
            .header("X-Correlation-ID",String.valueOf(req.getAttribute("correlationId")));
        for(String header:List.of("Content-Type","Idempotency-Key","If-Match","Accept")) {
            String value=req.getHeader(header);if(value!=null&&value.length()<300)builder.header(header,value);
        }
        HttpRequest.BodyPublisher body=Set.of("POST","PUT").contains(req.getMethod())?HttpRequest.BodyPublishers.ofInputStream(()->{
            try{return new LimitedInput(req.getInputStream(),limit);}catch(IOException e){throw new UncheckedIOException(e);}
        }):HttpRequest.BodyPublishers.noBody();
        boolean acquired=multipart&&uploads.tryAcquire();
        if(multipart&&!acquired)throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"UPLOAD_CAPACITY");
        try {
            var response=http.send(builder.method(req.getMethod(),body).build(),HttpResponse.BodyHandlers.ofInputStream());
            res.setStatus(response.statusCode());
            for(String header:List.of("Content-Type","Content-Disposition","X-Correlation-ID","Retry-After"))response.headers().firstValue(header).ifPresent(value->res.setHeader(header,value));
            res.setHeader("Cache-Control","no-store");res.setHeader("X-Content-Type-Options","nosniff");
            try(var input=response.body()){input.transferTo(res.getOutputStream());}
        }catch(InterruptedException ex){Thread.currentThread().interrupt();throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"DOWNSTREAM_INTERRUPTED");}
        catch(IOException ex){if(!res.isCommitted())throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"LOAN_SERVICE_UNAVAILABLE");}
        finally{if(acquired)uploads.release();}
    }
    public byte[] publicProduct() {
        try {var result=http.send(HttpRequest.newBuilder(URI.create(properties.getLoanUrl()+"/api/v1/products/current")).timeout(Duration.ofSeconds(5)).GET().build(),HttpResponse.BodyHandlers.ofByteArray());
            if(result.statusCode()!=200)throw new IOException();return result.body();}
        catch(Exception ex){throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"PRODUCT_UNAVAILABLE");}
    }
    private static class LimitedInput extends FilterInputStream {
        private long read;private final long limit;LimitedInput(InputStream in,long limit){super(in);this.limit=limit;}
        public int read()throws IOException{int value=super.read();if(value!=-1&&++read>limit)throw new IOException("Request bound");return value;}
        public int read(byte[] bytes,int off,int len)throws IOException{int n=super.read(bytes,off,len);if(n>0&&(read+=n)>limit)throw new IOException("Request bound");return n;}
    }
}

