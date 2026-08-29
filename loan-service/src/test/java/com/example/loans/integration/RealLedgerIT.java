package com.example.loans.integration;

import com.example.loans.*;
import com.example.loans.document.MalwareScanner;
import com.example.loans.integration.ledger.LedgerClient;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.*;
import org.springframework.web.client.*;
import org.testcontainers.containers.*;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.*;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RealLedgerIT extends LoanPostgresTest {
    static final Network NETWORK=Network.newNetwork();
    static final PostgreSQLContainer<?> LEDGER_DB=new PostgreSQLContainer<>("postgres:17.10-alpine")
        .withDatabaseName("ledger").withUsername("bootstrap").withPassword("test-bootstrap-only")
        .withInitScript("init-ledger-test.sql").withNetwork(NETWORK).withNetworkAliases("ledger-db");
    static final GenericContainer<?> LEDGER=new GenericContainer<>(DockerImageName.parse(
        "eclipse-temurin:21-jre-jammy@sha256:9603446cf51296842eab342514857cf7ab55a8891cd564e6c68ec237fae65a16"))
        .withNetwork(NETWORK).withCopyFileToContainer(MountableFile.forHostPath(Path.of(System.getProperty("basedir"),"..","ledger-core","target","ledger-core-0.1.0-SNAPSHOT.jar").normalize()),"/app/ledger.jar")
        .withEnv("DB_URL","jdbc:postgresql://ledger-db:5432/ledger").withEnv("DB_USER","ledger_app").withEnv("DB_PASSWORD","test-app-only")
        .withEnv("MIGRATION_DB_USER","ledger_migrator").withEnv("MIGRATION_DB_PASSWORD","test-migration-only")
        .withCommand("java","-jar","/app/ledger.jar").withExposedPorts(8080)
        .waitingFor(Wait.forHttp("/actuator/health").forStatusCode(200).withStartupTimeout(Duration.ofMinutes(2)));
    static String url;
    static UUID ledgerId;
    @DynamicPropertySource static void ledger(DynamicPropertyRegistry r) {
        LEDGER_DB.start(); LEDGER.start();
        url="http://"+LEDGER.getHost()+":"+LEDGER.getMappedPort(8080);
        var result=RestClient.create(url).post().uri("/api/v1/ledgers").body(Map.of("code","L-"+UUID.randomUUID(),"name","Synthetic real HTTP integration","currency","SEK")).retrieve().body(Map.class);
        ledgerId=UUID.fromString((String)result.get("id"));
        r.add("loan.ledger.url",()->url); r.add("loan.ledger.id",()->ledgerId.toString());
    }
    @MockitoBean MalwareScanner scanner;
    @MockitoSpyBean LedgerClient client;
    @BeforeEach void setup() {
        config.getLedger().setId(ledgerId);
        jdbc.update("UPDATE origination.durable_job SET state='DONE',lease_until=NULL WHERE state IN ('READY','RUNNING')");
        when(scanner.scan(any())).thenReturn(MalwareScanner.Result.CLEAN);
    }
    @Test void realLedgerRecoversLostResponseAndOutageWithOneAssetAccountAndNoPostings() {
        var app=ready(); UUID loan=approve(app);
        AtomicBoolean lose=new AtomicBoolean(true);
        doAnswer(call->{
            var account=call.callRealMethod();
            if(lose.getAndSet(false)) throw new ResourceAccessException("Synthetic lost HTTP response AFTER real remote commit");
            return account;
        }).when(client).provision(loan);
        worker.runOne("PROVISION"); assertThat(mappingRepository.findById(loan)).isEmpty();
        jdbc.update("UPDATE origination.durable_job SET available_at=now() WHERE target_id=?",loan);
        worker.runOne("PROVISION");
        var mapping=loans.mapping(TestFixtures.APPLICANT,loan);
        var http=RestClient.create(url);
        var account=http.get().uri("/api/v1/ledgers/{l}/accounts/{a}",ledgerId,mapping.accountId()).retrieve().body(Map.class);
        assertThat(account).containsEntry("type","ASSET").containsEntry("normalBalanceSide","DEBIT").containsEntry("externalReference","loan:"+loan+":principal");
        var balance=http.get().uri("/api/v1/ledgers/{l}/accounts/{a}/balance",ledgerId,mapping.accountId()).retrieve().body(Map.class);
        assertThat(balance).containsEntry("balance","0.00");
        var trial=http.get().uri("/api/v1/ledgers/{l}/trial-balance",ledgerId).retrieve().body(Map.class);
        assertThat((List<?>)trial.get("accounts")).hasSize(1); assertThat(trial).containsEntry("totalDebits","0.00").containsEntry("totalCredits","0.00");
        var entries=http.get().uri("/api/v1/ledgers/{l}/accounts/{a}/entries",ledgerId,mapping.accountId()).retrieve().body(Map.class);
        assertThat((List<?>)entries.get("entries")).isEmpty();
        var second=ready(); UUID secondLoan=approve(second);
        LEDGER.getDockerClient().pauseContainerCmd(LEDGER.getContainerId()).exec();
        try {
            worker.runOne("PROVISION");
            assertThat(loanRepository.findById(secondLoan)).isPresent();
            assertThat(mappingRepository.findById(secondLoan)).isEmpty();
        } finally { LEDGER.getDockerClient().unpauseContainerCmd(LEDGER.getContainerId()).exec(); }
        jdbc.update("UPDATE origination.durable_job SET available_at=now() WHERE target_id=?",secondLoan);
        worker.runOne("PROVISION");
        assertThat(mappingRepository.findById(secondLoan)).isPresent();
        assertThat(loans.get(TestFixtures.APPLICANT,secondLoan).lifecycle()).isEqualTo("AWAITING_AGREEMENT");
        var finalTrial=http.get().uri("/api/v1/ledgers/{l}/trial-balance",ledgerId).retrieve().body(Map.class);
        assertThat((List<?>)finalTrial.get("accounts")).hasSize(2); assertThat(finalTrial).containsEntry("totalDebits","0.00");
        assertThat(loans.retry(TestFixtures.OPERATIONS,loan,key(),new com.example.loans.dto.request.ReviewRequest("DEMO","Replay completed provision")).id()).isEqualTo(loan);
        assertThat(loans.mapping(TestFixtures.APPLICANT,loan).accountId()).isEqualTo(mapping.accountId());
    }
}

