package com.example.loans.integration.ledger;

import com.example.loans.config.LoanProperties;
import com.example.loans.exception.LoanException;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;
import java.net.http.HttpClient;
import java.util.*;

@Component
public class HttpLedgerClient implements LedgerClient {
    private final LoanProperties config;
    private final RestClient http;
    public HttpLedgerClient(LoanProperties config) {
        this.config=config;
        var factory=new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(config.getProviderTimeout()).build());
        factory.setReadTimeout(config.getProviderTimeout());
        var builder=RestClient.builder().baseUrl(config.getLedger().getUrl()).requestFactory(factory);
        if(config.getLedger().getTokenUri()!=null&&!config.getLedger().getTokenUri().isBlank()) {
            var credentials=new MachineCredentials(config.getLedger());
            builder.requestInterceptor((request,body,execution)->{request.getHeaders().setBearerAuth(credentials.token());return execution.execute(request,body);});
        } else if(config.getLedger().getBearerToken()!=null&&!config.getLedger().getBearerToken().isBlank()) builder.defaultHeaders(h->h.setBearerAuth(config.getLedger().getBearerToken()));
        http=builder.build();
    }
    public Balance balance(UUID ledger,UUID account) {
        var result=http.get().uri("/api/v1/ledgers/{ledger}/accounts/{account}/balance",ledger,account).retrieve().body(Balance.class);
        if(result==null||!account.equals(result.accountId())||!"SEK".equals(result.currency()))throw LoanException.unavailable("LEDGER_BALANCE_UNAVAILABLE");return result;
    }
    public Account provision(UUID loanId) {
        UUID ledger=config.getLedger().getId();
        var ledgerData=http.get().uri("/api/v1/ledgers/{id}",ledger).retrieve().body(LedgerData.class);
        if(ledgerData==null || !ledger.equals(ledgerData.id()) || !"SEK".equals(ledgerData.currency())) throw LoanException.invalid("LEDGER_CURRENCY_MISMATCH","Configured lender ledger must use SEK");
        String reference="loan:"+loanId+":principal";
        AccountData account;
        try {
            account=http.get().uri(b->b.path("/api/v1/ledgers/{id}/accounts/by-external-reference").queryParam("externalReference",reference).build(ledger)).retrieve().body(AccountData.class);
        } catch(HttpClientErrorException.NotFound ex) {
            account=http.post().uri("/api/v1/ledgers/{id}/accounts/provision",ledger).header("Idempotency-Key","loan:"+loanId+":principal")
                .body(Map.of("externalReference",reference,"code","LN-"+loanId,"name","Loan principal receivable","type","ASSET","enforceNonNegativeBalance",true))
                .retrieve().body(AccountData.class);
        }
        if(account==null || !ledger.equals(account.ledgerId()) || !reference.equals(account.externalReference()) || !"ASSET".equals(account.type())
            || !"DEBIT".equals(account.normalBalanceSide()) || !"ACTIVE".equals(account.status()) || !account.enforceNonNegativeBalance()
            || !("LN-"+loanId).equals(account.code())) throw LoanException.invalid("LEDGER_ACCOUNT_MISMATCH","Remote account does not match the approved loan mapping");
        return new Account(account.id(),ledger,reference,account.type(),account.normalBalanceSide(),account.status(),"SEK");
    }
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown=true) private record LedgerData(UUID id,String currency) {}
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown=true) private record AccountData(UUID id,UUID ledgerId,String externalReference,String type,String normalBalanceSide,String status,boolean enforceNonNegativeBalance,String code) {}
}

