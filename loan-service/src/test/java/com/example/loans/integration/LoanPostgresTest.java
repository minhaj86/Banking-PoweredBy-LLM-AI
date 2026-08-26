package com.example.loans.integration;

import com.example.loans.*;
import com.example.loans.config.LoanProperties;
import com.example.loans.domain.repository.*;
import com.example.loans.dto.response.*;
import com.example.loans.integration.storage.DocumentStorage;
import com.example.loans.mapper.Json;
import com.example.loans.service.*;
import com.example.loans.workflow.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc @ActiveProfiles("local") @Import(LoanPostgresTest.StorageConfig.class)
abstract class LoanPostgresTest {
    static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:17.10-alpine")
        .withDatabaseName("loans").withUsername("bootstrap").withPassword("test-bootstrap-only").withInitScript("init-loan-test.sql");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        POSTGRES.start();
        r.add("spring.datasource.url",POSTGRES::getJdbcUrl); r.add("spring.datasource.username",()->"loan_app"); r.add("spring.datasource.password",()->"test-app-only");
        r.add("spring.flyway.url",POSTGRES::getJdbcUrl); r.add("spring.flyway.user",()->"loan_migrator"); r.add("spring.flyway.password",()->"test-migration-only");
        r.add("loan.encryption-key",()->Base64.getEncoder().encodeToString(new byte[32]));
        r.add("loan.local-jwt-secret",()->Base64.getEncoder().encodeToString(new byte[32]));
        r.add("loan.ledger.id",()->"00000000-0000-0000-0000-000000000001");
        r.add("loan.storage.access-key",()->"test-only"); r.add("loan.storage.secret-key",()->"test-only");
        r.add("loan.workers-enabled",()->false); r.add("loan.provider-timeout",()->"2s");
    }
    @Autowired ApplicationService applications;
    @Autowired DocumentService documents;
    @Autowired DecisionService decisions;
    @Autowired LoanService loans;
    @Autowired OperationsService operations;
    @Autowired OriginationWorker worker;
    @Autowired JobQueue jobs;
    @Autowired JdbcTemplate jdbc;
    @Autowired Json json;
    @Autowired MockMvc mvc;
    @Autowired LoanProperties config;
    @Autowired ApplicationRepository applicationRepository;
    @Autowired AssessmentRepository assessmentRepository;
    @Autowired MappingRepository mappingRepository;
    @Autowired LoanRepository loanRepository;
    @Autowired DocumentRepository documentRepository;
    static String key() { return UUID.randomUUID().toString(); }
    ApplicationView create() { return applications.create(TestFixtures.APPLICANT,key(),TestFixtures.input("30000.00","4000.00")); }
    ApplicationView ready() { return ready("30000.00","4000.00"); }
    ApplicationView ready(String net,String expenses) {
        var app=applications.create(TestFixtures.APPLICANT,key(),TestFixtures.input(net,expenses));
        for(String category:List.of("PAYSLIP","TAX_ASSESSMENT")) {
            app=applications.get(TestFixtures.APPLICANT,app.id());
            documents.upload(TestFixtures.APPLICANT,app.id(),app.version(),key(),
                new DocumentService.UploadMetadata(category,LocalDate.now().minusMonths(1).withDayOfMonth(1),LocalDate.now().minusDays(1),null),
                TestFixtures.pdf("net_income="+net+" SEK MONTHLY"));
        }
        while(worker.runOne("DOCUMENT")) {}
        app=applications.get(TestFixtures.APPLICANT,app.id());
        applications.submit(TestFixtures.APPLICANT,app.id(),app.version(),key());
        worker.runOne("ASSESS");
        return applications.get(TestFixtures.APPLICANT,app.id());
    }
    UUID approve(ApplicationView app) { return decisions.decide(TestFixtures.OFFICER,app.id(),app.version(),key(),TestFixtures.approval(applications.assessment(TestFixtures.OFFICER,app.id()))).loanId(); }
    record Outcome(Object value,Throwable error) {}
    List<Outcome> race(java.util.function.Supplier<?> left,java.util.function.Supplier<?> right) throws Exception {
        var start=new CyclicBarrier(2);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var futures=new ArrayList<Future<Outcome>>();
            for(var action:List.of(left,right)) futures.add(pool.submit(()->{
                start.await(10,TimeUnit.SECONDS);
                try { return new Outcome(action.get(),null); } catch(Throwable ex) { return new Outcome(null,ex); }
            }));
            return List.of(futures.getFirst().get(30,TimeUnit.SECONDS),futures.getLast().get(30,TimeUnit.SECONDS));
        }
    }
    @TestConfiguration static class StorageConfig {
        @Bean @Primary DocumentStorage memoryStorage() {
            return new DocumentStorage() {
                private final Map<String,byte[]> bytes=new ConcurrentHashMap<>();
                private final Map<String,Metadata> meta=new ConcurrentHashMap<>();
                public void put(String key,byte[] data,Metadata metadata) { bytes.put(key,data.clone()); meta.put(key,metadata); }
                public Metadata head(String key) { return meta.get(key); }
                public byte[] get(String key) { return bytes.get(key).clone(); }
                public void delete(String key) { bytes.remove(key); meta.remove(key); }
            };
        }
    }
}

