package com.example.loans.integration.storage;

import com.example.loans.TestFixtures;
import com.example.loans.config.LoanProperties;
import com.example.loans.security.SensitiveTextConverter;
import com.example.loans.service.IdempotencyService;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.images.builder.Transferable;
import software.amazon.awssdk.services.s3.model.*;
import java.time.Duration;
import java.util.Base64;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
class S3StorageIT {
    @Container static final GenericContainer<?> S3=new GenericContainer<>("chrislusf/seaweedfs:3.85")
        .withCopyToContainer(Transferable.of("{\"identities\":[{\"name\":\"test\",\"credentials\":[{\"accessKey\":\"test-only\",\"secretKey\":\"test-secret-only\"}],\"actions\":[\"Admin\",\"Read\",\"Write\"]}]}"),"/etc/test-s3.json")
        .withCommand("server","-s3","-s3.port=8333","-s3.config=/etc/test-s3.json","-dir=/data","-ip=localhost","-ip.bind=0.0.0.0","-master.volumeSizeLimitMB=100","-volume.max=3")
        .withExposedPorts(8333).waitingFor(Wait.forHttp("/").forStatusCode(403).withStartupTimeout(Duration.ofSeconds(90)));
    @Test void encryptedDocumentsRoundTripAgainstRealS3CompatibleStorageWithoutChunkFrames() {
        var p=new LoanProperties(); p.setEncryptionKey(Base64.getEncoder().encodeToString(new byte[32]));
        p.getStorage().setEndpoint("http://"+S3.getHost()+":"+S3.getMappedPort(8333)); p.getStorage().setAccessKey("test-only");p.getStorage().setSecretKey("test-secret-only");
        try(var client=new S3DocumentStorage.Config().s3(p)) {
            client.createBucket(CreateBucketRequest.builder().bucket(p.getStorage().getBucket()).build());
            var storage=new S3DocumentStorage(client,p,new SensitiveTextConverter(p));byte[] pdf=TestFixtures.pdf("net_income=30000.00 SEK MONTHLY");
            var metadata=new DocumentStorage.Metadata(IdempotencyService.hash(pdf),pdf.length,"application/pdf");
            storage.put("quarantine/random/test",pdf,metadata); assertThat(storage.head("quarantine/random/test")).isEqualTo(metadata);
            assertThat(storage.get("quarantine/random/test")).isEqualTo(pdf);
            String raw=client.getObjectAsBytes(GetObjectRequest.builder().bucket(p.getStorage().getBucket()).key("quarantine/random/test").build()).asUtf8String();
            assertThat(raw).startsWith("v1:").doesNotContain("net_income");
            storage.delete("quarantine/random/test");
            assertThatThrownBy(()->storage.head("quarantine/random/test")).isInstanceOf(software.amazon.awssdk.services.s3.model.S3Exception.class);
        }
    }
}
