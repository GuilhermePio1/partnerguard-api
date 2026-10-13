package com.partnerguard.security;

import com.partnerguard.security.ContractOperations.Operation;
import com.partnerguard.support.ApiIntegrationTest;
import com.partnerguard.support.SchemaOwner;
import com.partnerguard.support.SecurityTestTokens;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.FieldSource;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;

import static com.partnerguard.support.Fixtures.NEVER_EXISTS;
import static com.partnerguard.support.Fixtures.PARTNER_A;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * specs/authorization.md rule 5 (ADR-0021): a PARTNER whose partner is SUSPENDED or doesn't
 * exist gets 403 everywhere. The status is changed as the schema owner, outside the test's
 * transaction, so it is restored after every test.
 */
class PartnerStatusTests extends ApiIntegrationTest {

    static final List<Operation> OPERATIONS = ContractOperations.ALL;

    @AfterEach
    void reactivatePartnerA() {
        SchemaOwner.setPartnerStatus(PARTNER_A, "ACTIVE");
    }

    @ParameterizedTest(name = "AUTHZ-20 {0} as a suspended partner → 403")
    @FieldSource("OPERATIONS")
    @DisplayName("AUTHZ-20 a suspended partner's user gets 403 on every operation")
    void suspendedPartnerIsForbiddenEverywhere(Operation operation) {
        SchemaOwner.setPartnerStatus(PARTNER_A, "SUSPENDED");

        assertProblem(operation.call(mvc, SecurityTestTokens.partnerA()), 403, "forbidden");
    }

    @Test
    @DisplayName("AUTHZ-21 a token issued while the partner was ACTIVE stops working once it is suspended")
    void suspensionAppliesToTokensAlreadyIssued() {
        RequestPostProcessor token = SecurityTestTokens.partnerA();
        assertThat(mvc.get().uri("/api/v1/claims").with(token)).hasStatusOk();

        SchemaOwner.setPartnerStatus(PARTNER_A, "SUSPENDED");

        assertProblem(mvc.get().uri("/api/v1/claims").with(token).exchange(), 403, "forbidden");
    }

    @Test
    @DisplayName("AUTHZ-22 a partner_id that names no partner gets 403, on lists and on /partners/me")
    void unknownPartnerIsForbidden() {
        RequestPostProcessor token = SecurityTestTokens.partner(NEVER_EXISTS);

        assertProblem(mvc.get().uri("/api/v1/claims").with(token).exchange(), 403, "forbidden");
        assertProblem(mvc.get().uri("/api/v1/partners/me").with(token).exchange(), 403, "forbidden");
    }
}
