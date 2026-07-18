package com.example.loans.security;
import com.example.loans.config.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class ProductionGuardTest {
    @Test void productionRejectsSyntheticVerificationAndMockAi() {
        var p=new LoanProperties(); p.setSyntheticVerification(true);
        assertThatThrownBy(()->ProductionGuard.validate(p,false)).hasMessageContaining("Synthetic");
        p.setSyntheticVerification(false);
        assertThatThrownBy(()->ProductionGuard.validate(p,false)).hasMessageContaining("Mock AI");
    }
    @Test void externalAiRequiresExplicitDeploymentApprovalAndModel() {
        var p=new LoanProperties(); p.getAi().setProvider("openai");
        assertThatThrownBy(()->ProductionGuard.validate(p,true)).hasMessageContaining("data-handling");
    }
    @Test void sensitiveDataEncryptionIsRandomisedAuthenticatedAndRoundTrips() {
        var p=new LoanProperties(); p.setEncryptionKey(java.util.Base64.getEncoder().encodeToString(new byte[32]));
        var c=new SensitiveTextConverter(p); String a=c.convertToDatabaseColumn("SYNTHETIC-ID"),b=c.convertToDatabaseColumn("SYNTHETIC-ID");
        assertThat(a).isNotEqualTo(b).doesNotContain("SYNTHETIC"); assertThat(c.convertToEntityAttribute(a)).isEqualTo("SYNTHETIC-ID");
        assertThatThrownBy(()->c.convertToEntityAttribute(a.substring(0,a.length()-4)+"AAAA")).isInstanceOf(IllegalStateException.class);
    }
}

