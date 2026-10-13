package com.partnerguard.claim;

import com.partnerguard.support.ApiIntegrationTest;
import com.partnerguard.support.SecurityTestTokens;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;

import static com.partnerguard.support.Fixtures.CLAIM_A1;
import static com.partnerguard.support.Fixtures.CLAIM_A2;
import static com.partnerguard.support.Fixtures.CLAIM_B1;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Field-level permissions on PATCH /claims/{id} and their evaluation order
 * (specs/claims.md rules 3 and 4, ADR-0009): 400 → 403 → 404 → 409.
 */
class ClaimPermissionApiTests extends ApiIntegrationTest {

    @ParameterizedTest(name = "CLM-18 {0} changing {1} → {3}")
    @CsvSource(delimiter = '|', value = {
            "ADMIN   | status      | {\"status\":\"UNDER_REVIEW\"}   | 200",
            "ADMIN   | description | {\"description\":\"Revised\"}  | 200",
            "ADMIN   | amount      | {\"amount\":1300.00}           | 200",
            "ANALYST | status      | {\"status\":\"UNDER_REVIEW\"}   | 200",
            "ANALYST | description | {\"description\":\"Revised\"}  | 403",
            "ANALYST | amount      | {\"amount\":1300.00}           | 403",
            "PARTNER | status      | {\"status\":\"UNDER_REVIEW\"}   | 403",
            "PARTNER | description | {\"description\":\"Revised\"}  | 200",
            "PARTNER | amount      | {\"amount\":1300.00}           | 200"})
    @DisplayName("CLM-18 every role × field cell of the permission table")
    void permissionTable(String role, String field, String json, int expected) {
        MvcTestResult result = patch(caller(role), CLAIM_A1, json);

        if (expected == 403) {
            assertProblem(result, 403, "forbidden");
        } else {
            assertThat(result).hasStatus(expected);
        }
    }

    @Test
    @DisplayName("CLM-09 a partner edits the description of its own SUBMITTED claim")
    void partnerEditsOwnSubmittedClaim() {
        MvcTestResult result = patch(SecurityTestTokens.partnerA(), CLAIM_A1,
                "{\"description\":\"Front bumper and grille\"}");

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.description").isEqualTo("Front bumper and grille");
    }

    @Test
    @DisplayName("CLM-10 an analyst can't change the amount")
    void analystCannotChangeAmount() {
        assertProblem(patch(SecurityTestTokens.analyst(), CLAIM_A1, "{\"amount\":10}"), 403, "forbidden");
    }

    @Test
    @DisplayName("CLM-11 an analyst can't change the description")
    void analystCannotChangeDescription() {
        assertProblem(patch(SecurityTestTokens.analyst(), CLAIM_A1, "{\"description\":\"x\"}"), 403, "forbidden");
    }

    @Test
    @DisplayName("CLM-12 a partner can't change the status of its own claim")
    void partnerCannotChangeStatus() {
        assertProblem(patch(SecurityTestTokens.partnerA(), CLAIM_A1, "{\"status\":\"APPROVED\"}"), 403, "forbidden");
    }

    @Test
    @DisplayName("CLM-13 a forbidden field is a 403 even on another partner's claim: the field check runs before the lookup")
    void fieldCheckRunsBeforeOwnership() {
        assertProblem(patch(SecurityTestTokens.partnerA(), CLAIM_B1, "{\"status\":\"APPROVED\"}"), 403, "forbidden");
    }

    @Test
    @DisplayName("CLM-14 a partner editing another partner's claim gets a 404")
    void partnerCannotEditOtherPartnersClaim() {
        assertProblem(patch(SecurityTestTokens.partnerA(), CLAIM_B1, "{\"description\":\"x\"}"), 404, "not-found");
    }

    @Test
    @DisplayName("CLM-15 a partner can't edit its own claim once it is UNDER_REVIEW")
    void partnerCannotEditClaimUnderReview() {
        assertProblem(patch(SecurityTestTokens.partnerA(), CLAIM_A2, "{\"description\":\"x\"}"),
                409, "claim-not-editable");
    }

    @Test
    @DisplayName("ADR-0009 an invalid body is a 400 before the field check, even for a forbidden field")
    void validationRunsBeforeFieldCheck() {
        assertInvalidField(patch(SecurityTestTokens.analyst(), CLAIM_A1, "{\"amount\":0}"), "amount");
    }

    private MvcTestResult patch(RequestPostProcessor caller, UUID id, String json) {
        return mvc.patch().uri("/api/v1/claims/{id}", id).with(caller)
                .contentType(MediaType.APPLICATION_JSON).content(json).exchange();
    }

    private static RequestPostProcessor caller(String role) {
        return switch (role) {
            case "ADMIN" -> SecurityTestTokens.admin();
            case "ANALYST" -> SecurityTestTokens.analyst();
            case "PARTNER" -> SecurityTestTokens.partnerA();
            default -> throw new IllegalArgumentException(role);
        };
    }
}
