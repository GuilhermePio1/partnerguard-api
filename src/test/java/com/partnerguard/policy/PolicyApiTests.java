package com.partnerguard.policy;

import com.partnerguard.support.ApiIntegrationTest;
import com.partnerguard.support.SecurityTestTokens;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static com.partnerguard.support.Fixtures.POLICY_1;
import static com.partnerguard.support.Fixtures.UNKNOWN_POLICY;
import static org.assertj.core.api.Assertions.assertThat;

class PolicyApiTests extends ApiIntegrationTest {

    @Test
    @DisplayName("POL-01 finds a policy by its number")
    void findsPolicy() {
        MvcTestResult result = mvc.get().uri("/api/v1/policies/{number}", POLICY_1).exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.holderName").isEqualTo("Maria Silva");
        assertThat(result).bodyJson().extractingPath("$.vehiclePlate").isEqualTo("ABC1D23");
    }

    @Test
    @DisplayName("POL-02 a partner can't look up policies")
    void partnerCannotLookUpPolicies() {
        assertProblem(mvc.get().uri("/api/v1/policies/{number}", POLICY_1).with(SecurityTestTokens.partnerA())
                .exchange(), 403, "forbidden");
    }

    @Test
    @DisplayName("POL-03 an unknown policy number is a 404")
    void unknownPolicyIsNotFound() {
        assertProblem(mvc.get().uri("/api/v1/policies/{number}", UNKNOWN_POLICY).exchange(), 404, "not-found");
    }

    @Test
    @DisplayName("POL-04 an injection attempt in the number is a 400, before any query")
    void rejectsInjectionAttempt() {
        assertInvalidField(mvc.get().uri("/api/v1/policies/{number}", "POL' OR '1'='1").exchange(), "number");
    }

    @Test
    @DisplayName("POL-05 a lowercase number is a 400")
    void rejectsLowercaseNumber() {
        assertInvalidField(mvc.get().uri("/api/v1/policies/{number}", "pol-2026-000001").exchange(), "number");
    }
}
