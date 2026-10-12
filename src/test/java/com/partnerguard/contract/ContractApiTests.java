package com.partnerguard.contract;

import com.partnerguard.support.ApiIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static com.partnerguard.support.Fixtures.CONTRACT_A;
import static com.partnerguard.support.Fixtures.CONTRACT_B;
import static com.partnerguard.support.Fixtures.NEVER_EXISTS;
import static com.partnerguard.support.Fixtures.PARTNER_A;
import static org.assertj.core.api.Assertions.assertThat;

class ContractApiTests extends ApiIntegrationTest {

    @Test
    @DisplayName("CTR-01 internal callers list every contract")
    void listsAllContracts() {
        MvcTestResult result = mvc.get().uri("/api/v1/contracts").exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.content[*].id").asArray()
                .containsExactlyInAnyOrder(CONTRACT_A.toString(), CONTRACT_B.toString());
    }

    @Test
    @DisplayName("CTR-04 returns a contract's details")
    void returnsContract() {
        MvcTestResult result = mvc.get().uri("/api/v1/contracts/{id}", CONTRACT_A).exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.partnerId").isEqualTo(PARTNER_A.toString());
        assertThat(result).bodyJson().extractingPath("$.startDate").isEqualTo("2026-01-01");
        assertThat(result).bodyJson().extractingPath("$.terms").isEqualTo("Standard repair agreement, Partner A");
    }

    @Test
    @DisplayName("CTR-05 a contract that doesn't exist is a 404")
    void missingContractIsNotFound() {
        assertProblem(mvc.get().uri("/api/v1/contracts/{id}", NEVER_EXISTS).exchange(), 404, "not-found");
    }

    @Test
    @DisplayName("CTR-06 contracts can't be created through the API")
    void contractsAreReadOnly() {
        MvcTestResult result = mvc.post().uri("/api/v1/contracts").exchange();

        assertThat(result).hasStatus(405);
        assertThat(result).hasContentTypeCompatibleWith("application/problem+json");
    }

    @Test
    @DisplayName("ERR-07 an unknown path under /api/v1 is a 404 not-found Problem Details")
    void unknownPathIsNotFound() {
        assertProblem(mvc.get().uri("/api/v1/unknown").exchange(), 404, "not-found");
    }
}
