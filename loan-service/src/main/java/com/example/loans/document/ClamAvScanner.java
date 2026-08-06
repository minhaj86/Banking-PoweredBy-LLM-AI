package com.example.loans.document;

import com.example.loans.config.LoanProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

@Component @RequiredArgsConstructor
public class ClamAvScanner implements MalwareScanner {
    private final LoanProperties config;
    public Result scan(byte[] bytes) {
        if(config.getScannerHost()==null || config.getScannerHost().isBlank()) return Result.UNAVAILABLE;
        try(var socket=new Socket()) {
            socket.connect(new InetSocketAddress(config.getScannerHost(),config.getScannerPort()),(int)config.getProviderTimeout().toMillis());
            socket.setSoTimeout((int)config.getProviderTimeout().toMillis());
            var out=new DataOutputStream(socket.getOutputStream()); out.write("zINSTREAM\0".getBytes(StandardCharsets.US_ASCII));
            for(int n=0;n<bytes.length;n+=8192) { int size=Math.min(8192,bytes.length-n); out.writeInt(size); out.write(bytes,n,size); }
            out.writeInt(0); out.flush();
            byte[] response=socket.getInputStream().readNBytes(512);
            String value=new String(response,StandardCharsets.US_ASCII);
            return value.contains("FOUND")?Result.INFECTED:value.contains("stream: OK")?Result.CLEAN:Result.UNAVAILABLE;
        } catch(IOException ex) { return Result.UNAVAILABLE; }
    }
}

