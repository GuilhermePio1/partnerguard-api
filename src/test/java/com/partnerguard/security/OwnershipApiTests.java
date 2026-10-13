package com.partnerguard.security;

import com.partnerguard.support.ApiIntegrationTest;
import com.partnerguard.support.SecurityTestTokens;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;

import static com.partnerguard.support.Fixtures.CLAIM_A1;
import static com.partnerguard.support.Fixtures.CLAIM_A2;
import static com.partnerguard.support.Fixtures.CLAIM_B1;
import static com.partnerguard.support.Fixtures.CONTRACT_A;
import static com.partnerguard.support.Fixtures.CONTRACT_B;
import static com.partnerguard.support.Fixtures.NEVER_EXISTS;
import static com.partnerguard.support.Fixtures.PARTNER_A;
import static com.partnerguard.support.Fixtures.PARTNER_B;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ownership over HTTP, with a real Partner A token (specs/authorization.md rules 6 and 7,
 * ADR-0004). The service-level proofs are in ClaimServiceTests and ContractServiceTests.
 */
@ExtendWith(OutputCaptureExtension.class)
class OwnershipApiTests extends ApiIntegrationTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test
    @DisplayName("AUTHZ-07 a partnerId query parameter never widens a partner's list")
    void partnerIdParameterIsNotApplied() {
        MvcTestResult result = mvc.get().uri("/api/v1/claims?partnerId={id}", PARTNER_B)
                .with(SecurityTestTokens.partnerA()).exchange();

        // Ignoring the parameter or rejecting it are both fine; applying it is not.
        if (result.getResponse().getStatus() == 400) {
            assertProblem(result, 400, "validation-error");
        } else {
            assertThat(result).hasStatusOk();
            assertThat(result).bodyJson().extractingPath("$.content[*].id").asArray()
                    .containsExactlyInAnyOrder(CLAIM_A1.toString(), CLAIM_A2.toString());
        }
    }

    @Test
    @DisplayName("AUTHZ-08 a partner reads its own contract")
    void partnerReadsOwnContract() {
        MvcTestResult result = asPartnerA("/api/v1/contracts/" + CONTRACT_A);

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.partnerId").isEqualTo(PARTNER_A.toString());
    }

    @Test
    @DisplayName("AUTHZ-09 / CTR-03 another partner's contract is a 404")
    void otherPartnersContractIsNotFound() {
        assertProblem(asPartnerA("/api/v1/contracts/" + CONTRACT_B), 404, "not-found");
    }

    @Test
    @DisplayName("AUTHZ-10 another partner's contract and a missing one get identical 404 bodies, except instance")
    void otherPartnersContractLooksMissing() {
        MvcTestResult otherPartners = asPartnerA("/api/v1/contracts/" + CONTRACT_B);
        MvcTestResult missing = asPartnerA("/api/v1/contracts/" + NEVER_EXISTS);

        assertProblem(otherPartners, 404, "not-found");
        assertProblem(missing, 404, "not-found");
        assertThat(withoutInstance(otherPartners)).isEqualTo(withoutInstance(missing));
    }

    @Test
    @DisplayName("CLM-03 another partner's claim and a missing one get identical 404 bodies, except instance")
    void otherPartnersClaimLooksMissing() {
        MvcTestResult otherPartners = asPartnerA("/api/v1/claims/" + CLAIM_B1);
        MvcTestResult missing = asPartnerA("/api/v1/claims/" + NEVER_EXISTS);

        assertProblem(otherPartners, 404, "not-found");
        assertThat(withoutInstance(otherPartners)).isEqualTo(withoutInstance(missing));
    }

    @Test
    @DisplayName("AUTHZ-11 / CLM-01 / CTR-02 / CTR-07 a partner's lists hold only its own items, and the totals count only those")
    void partnerListsHoldOnlyOwnItems() {
        for (String path : List.of("/api/v1/contracts", "/api/v1/claims")) {
            MvcTestResult result = asPartnerA(path);

            assertThat(result).hasStatusOk();
            assertThat(result).bodyJson().extractingPath("$.content[*].partnerId").asArray()
                    .isNotEmpty()
                    .containsOnly(PARTNER_A.toString());
        }
        assertThat(asPartnerA("/api/v1/contracts")).bodyJson().extractingPath("$.totalElements").isEqualTo(1);
        assertThat(asPartnerA("/api/v1/claims")).bodyJson().extractingPath("$.totalElements").isEqualTo(2);
    }

    @Test
    @DisplayName("AUTHZ-12 an analyst lists every partner's claims")
    void analystListsAllClaims() {
        MvcTestResult result = mvc.get().uri("/api/v1/claims").with(SecurityTestTokens.analyst()).exchange();

        assertThat(result).bodyJson().extractingPath("$.content[*].id").asArray()
                .containsExactlyInAnyOrder(CLAIM_A1.toString(), CLAIM_A2.toString(), CLAIM_B1.toString());
    }

    @Test
    @DisplayName("AUTHZ-17 an ownership 404 writes one ownership_denied log event")
    void ownershipDenialIsLogged(CapturedOutput output) {
        assertProblem(asPartnerA("/api/v1/claims/" + CLAIM_B1), 404, "not-found");

        List<String> events = output.getOut().lines().filter(line -> line.contains("event=ownership_denied")).toList();
        assertThat(events).hasSize(1);
        assertThat(events.getFirst()).contains(SecurityTestTokens.PARTNER_SUBJECT).contains(CLAIM_B1.toString());
    }

    private MvcTestResult asPartnerA(String path) {
        return mvc.get().uri(path).with(SecurityTestTokens.partnerA()).exchange();
    }

    private static JsonNode withoutInstance(MvcTestResult result) {
        ObjectNode body = (ObjectNode) JSON.readTree(body(result));
        body.remove("instance");
        return body;
    }
}
