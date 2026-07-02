package com.example.ledger.integration;

import com.example.ledger.domain.enums.AccountType;
import com.example.ledger.dto.request.ProvisionAccountRequest;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

class AccountProvisioningIT extends PostgresIntegrationTest {
    @Test void concurrentProvisioningAndLostResponseRecoverOneZeroBalanceAccount() throws Exception {
        var ledger=ledger(); String reference="loan:"+UUID.randomUUID()+":principal";
        var request=new ProvisionAccountRequest(reference,"LOAN-1","Principal",AccountType.ASSET,true);
        var barrier=new CyclicBarrier(2);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var a=pool.submit(()->{barrier.await(10,TimeUnit.SECONDS);return accounts.provision(ledger.id(),"provision-a",request);});
            var b=pool.submit(()->{barrier.await(10,TimeUnit.SECONDS);return accounts.provision(ledger.id(),"provision-b",request);});
            var first=a.get(30,TimeUnit.SECONDS); assertThat(b.get(30,TimeUnit.SECONDS)).isEqualTo(first);
            assertThat(accounts.provision(ledger.id(),"provision-a",request)).isEqualTo(first);
            assertThat(accounts.byExternalReference(ledger.id(),reference)).isEqualTo(first);
            balance(ledger.id(),first.id(),"0.00"); assertThat(transactions(ledger.id())).isZero();
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ledger.account WHERE ledger_id=? AND external_reference=?",Integer.class,ledger.id(),reference)).isOne();
        assertThatThrownBy(()->accounts.provision(ledger.id(),"provision-a",new ProvisionAccountRequest(reference,"OTHER","Other",AccountType.LIABILITY,false)))
            .hasMessageContaining("different");
        assertThatThrownBy(()->jdbc.update("UPDATE ledger.account SET external_reference=? WHERE ledger_id=?","changed",ledger.id())).isInstanceOf(org.springframework.dao.DataAccessException.class);
    }
}

