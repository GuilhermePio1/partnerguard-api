package com.partnerguard.claim;

import com.partnerguard.support.ApiIntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.util.UUID;

import static com.partnerguard.support.Fixtures.CLAIM_A1;
import static com.partnerguard.support.Fixtures.CLAIM_A2;
import static com.partnerguard.support.Fixtures.CLAIM_B1;
import static com.partnerguard.support.Fixtures.NEVER_EXISTS;
import static org.assertj.core.api.Assertions.assertThat;

class ClaimApiTests extends ApiIntegrationTest {

    @Autowired
    EntityManager entityManager;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("CLM-02 filters claims by status")
    void filtersByStatus() {
        MvcTestResult result = mvc.get().uri("/api/v1/claims?status=SUBMITTED").exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.content[*].id").asArray()
                .containsExactlyInAnyOrder(CLAIM_A1.toString(), CLAIM_B1.toString());
    }

    @Test
    @DisplayName("API-02 an unknown status filter value is a 400 naming status")
    void rejectsUnknownStatusFilter() {
        assertInvalidField(mvc.get().uri("/api/v1/claims?status=PAID").exchange(), "status");
    }

    @Test
    @DisplayName("CLM-04 returns a claim's details")
    void returnsClaim() {
        MvcTestResult result = mvc.get().uri("/api/v1/claims/{id}", CLAIM_A1).exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("SUBMITTED");
        assertThat(result).bodyJson().extractingPath("$.amount").isEqualTo(1250.5);
    }

    @Test
    @DisplayName("API-06 a malformed id is a 400 naming id")
    void rejectsMalformedId() {
        assertInvalidField(mvc.get().uri("/api/v1/claims/not-a-uuid").exchange(), "id");
    }

    @Test
    @DisplayName("CLM-05 UNDER_REVIEW can move to APPROVED")
    void approvesClaimUnderReview() {
        MvcTestResult result = patch(CLAIM_A2, "{\"status\":\"APPROVED\"}");

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("APPROVED");
    }

    @Test
    @DisplayName("CLM-06 SUBMITTED can't skip UNDER_REVIEW")
    void cannotSkipReview() {
        assertProblem(patch(CLAIM_A1, "{\"status\":\"APPROVED\"}"), 409, "invalid-status-transition");
    }

    @Test
    @DisplayName("CLM-07 APPROVED is final")
    void approvedIsFinal() {
        assertThat(patch(CLAIM_A2, "{\"status\":\"APPROVED\"}")).hasStatusOk();

        assertProblem(patch(CLAIM_A2, "{\"status\":\"REJECTED\"}"), 409, "invalid-status-transition");
    }

    @Test
    @DisplayName("CLM-08 a transition to the same status is rejected")
    void sameStatusIsRejected() {
        assertProblem(patch(CLAIM_A2, "{\"status\":\"UNDER_REVIEW\"}"), 409, "invalid-status-transition");
    }

    @Test
    @DisplayName("CLM-16 updates description and amount")
    void updatesDescriptionAndAmount() {
        MvcTestResult result = patch(CLAIM_A1, "{\"amount\":1300.00,\"description\":\"Revised\"}");

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.description").isEqualTo("Revised");
        assertThat(result).bodyJson().extractingPath("$.amount").isEqualTo(1300.0);
    }

    @Test
    @DisplayName("CLM-17 a partnerId in the body is a 400, never applied")
    void rejectsPartnerIdInBody() {
        assertInvalidField(patch(CLAIM_A1, "{\"partnerId\":\"10000000-0000-4000-8000-00000000000b\"}"), "partnerId");
    }

    @Test
    @DisplayName("CLM-19 an empty body is a 400")
    void rejectsEmptyBody() {
        assertInvalidField(patch(CLAIM_A1, "{}"), "body");
    }

    @Test
    @DisplayName("CLM-20 an explicit null is a 400, not 'leave unchanged'")
    void rejectsExplicitNull() {
        MvcTestResult result = patch(CLAIM_A1, "{\"description\":null}");

        assertInvalidField(result, "description");
        assertThat(result).bodyJson().extractingPath("$.errors[0].message").isEqualTo("must not be null");
    }

    @Test
    @DisplayName("CLM-21 amounts must be positive with at most two decimals")
    void rejectsInvalidAmounts() {
        assertInvalidField(patch(CLAIM_A1, "{\"amount\":0}"), "amount");
        assertInvalidField(patch(CLAIM_A1, "{\"amount\":10.001}"), "amount");
        assertInvalidField(patch(CLAIM_A1, "{\"amount\":1000000.01}"), "amount");
    }

    @Test
    @DisplayName("HRD-04 markup in the description is rejected")
    void rejectsMarkupInDescription() {
        assertInvalidField(patch(CLAIM_A1, "{\"description\":\"<img src=x onerror=alert(1)>\"}"), "description");
    }

    @Test
    @DisplayName("HRD-05 tabs and newlines in the description are accepted")
    void acceptsTabsAndNewlines() {
        assertThat(patch(CLAIM_A1, "{\"description\":\"Line 1\\n\\tLine 2\"}")).hasStatusOk();
    }

    @Test
    @DisplayName("HRD-06 control characters in the description are rejected")
    void rejectsControlCharacters() {
        assertInvalidField(patch(CLAIM_A1, "{\"description\":\"bad\\u0000value\"}"), "description");
        assertInvalidField(patch(CLAIM_A1, "{\"description\":\"bad\\u001bvalue\"}"), "description");
    }

    @Test
    @DisplayName("CLM-24 / HRD-07 an SQL payload is stored verbatim and has no effect")
    void storesSqlPayloadVerbatim() {
        String payload = "'; DROP TABLE claim; --";

        assertThat(patch(CLAIM_A1, "{\"description\":\"" + payload + "\"}")).hasStatusOk();
        entityManager.flush();

        assertThat(jdbcTemplate.queryForObject("select description from claim where id = ?", String.class, CLAIM_A1))
                .isEqualTo(payload);
        assertThat(jdbcTemplate.queryForObject("select count(*) from claim", Integer.class)).isEqualTo(3);
    }

    @Test
    @DisplayName("CLM-14 updating a claim that doesn't exist is a 404")
    void updatingMissingClaimIsNotFound() {
        assertProblem(patch(NEVER_EXISTS, "{\"description\":\"x\"}"), 404, "not-found");
    }

    private MvcTestResult patch(UUID id, String json) {
        return mvc.patch().uri("/api/v1/claims/{id}", id)
                .contentType(MediaType.APPLICATION_JSON).content(json).exchange();
    }
}
