package com.example.loans.integration.storage;

import com.example.loans.config.LoanProperties;
import com.example.loans.security.SensitiveTextConverter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.*;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.*;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.*;
import software.amazon.awssdk.services.s3.model.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Component @RequiredArgsConstructor
public class S3DocumentStorage implements DocumentStorage {
    private final S3Client client;
    private final LoanProperties properties;
    private final SensitiveTextConverter encryption;
    public void put(String key,byte[] bytes,Metadata metadata) {
        // Client-side authenticated encryption also protects local MinIO volumes.
        byte[] encrypted=encryption.convertToDatabaseColumn(Base64.getEncoder().encodeToString(bytes)).getBytes(StandardCharsets.UTF_8);
        client.putObject(PutObjectRequest.builder().bucket(properties.getStorage().getBucket()).key(key).contentType("application/octet-stream")
                .metadata(Map.of("checksum",metadata.checksum(),"plaintext-size",Long.toString(metadata.size()),"actual-media",metadata.mediaType())).build(),RequestBody.fromBytes(encrypted));
    }
    public Metadata head(String key) {
        var h=client.headObject(HeadObjectRequest.builder().bucket(properties.getStorage().getBucket()).key(key).build());
        return new Metadata(h.metadata().get("checksum"),Long.parseLong(h.metadata().get("plaintext-size")),h.metadata().get("actual-media"));
    }
    public byte[] get(String key) {
        try(var stream=client.getObject(GetObjectRequest.builder().bucket(properties.getStorage().getBucket()).key(key).build())) {
            int bound=Math.toIntExact(properties.getMaxFileBytes()*2);
            byte[] bytes=stream.readNBytes(bound+1);
            if(bytes.length>bound) throw new IllegalStateException("Object exceeds encrypted file bound");
            return Base64.getDecoder().decode(encryption.convertToEntityAttribute(new String(bytes,StandardCharsets.UTF_8)));
        } catch(java.io.IOException ex) { throw new IllegalStateException("Object could not be read"); }
    }
    public void delete(String key) { client.deleteObject(DeleteObjectRequest.builder().bucket(properties.getStorage().getBucket()).key(key).build()); }
    @Configuration static class Config {
        @Bean S3Client s3(LoanProperties p) {
            var s=p.getStorage();
            return S3Client.builder().endpointOverride(URI.create(s.getEndpoint())).region(Region.of(s.getRegion())).forcePathStyle(true)
                .serviceConfiguration(S3Configuration.builder().chunkedEncodingEnabled(false).build())
                .requestChecksumCalculation(software.amazon.awssdk.core.checksums.RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(software.amazon.awssdk.core.checksums.ResponseChecksumValidation.WHEN_REQUIRED)
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(s.getAccessKey(),s.getSecretKey())))
                .overrideConfiguration(c -> c.apiCallTimeout(p.getProviderTimeout()).apiCallAttemptTimeout(p.getProviderTimeout().dividedBy(2))).build();
        }
    }
}

