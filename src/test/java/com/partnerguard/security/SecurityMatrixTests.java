package com.partnerguard.security;

import com.partnerguard.security.ContractOperations.Operation;
import com.partnerguard.support.PostgresIntegrationTest;
import com.partnerguard.support.SecurityTestTokens;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static com.partnerguard.support.ProblemAssertions.assertProblem;
import static com.partnerguard.support.ProblemAssertions.assertUnauthorized;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The endpoint × caller matrix (ADR-0016, specs/authorization.md): every operation in the
 * contract, called by every kind of caller. The expected status comes from the contract's
 * {@code x-required-roles}, not from this class, so the contract and the code can't drift.
 * Each test rolls back, so writes by allowed callers don't leak.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SecurityMatrixTests extends PostgresIntegrationTest {

    private static final Map<String, List<String>> REQUIRED_ROLES = ContractOperations.requiredRolesFromContract();

    /** Who calls, the criterion that covers that kind of caller, and the role it acts as (if any). */
    enum Caller {
        ANONYMOUS("AUTHZ-03", null, () -> null),
        ADMIN("AUTHZ-03", "ADMIN", SecurityTestTokens::admin),
        ANALYST("AUTHZ-03", "ANALYST", SecurityTestTokens::analyst),
        PARTNER_A("AUTHZ-03", "PARTNER", SecurityTestTokens::partnerA),
        PARTNER_WITHOUT_PARTNER_ID("AUTHZ-05", null, SecurityTestTokens::partnerWithoutPartnerId),
        PARTNER_WITH_MALFORMED_PARTNER_ID("AUTHZ-06", null, SecurityTestTokens::partnerWithMalformedPartnerId),
        UNMAPPED_ROLE_ONLY("AUTHZ-04", null, SecurityTestTokens::unmappedRole),
        PARTNER_AND_ADMIN("AUTHZ-19", null, () -> SecurityTestTokens.partnerAWithInternalRole("ADMIN")),
        PARTNER_AND_ANALYST("AUTHZ-19", null, () -> SecurityTestTokens.partnerAWithInternalRole("ANALYST"));

        final String criterion;
        /** The contract role this caller acts as; null for callers that must be refused everywhere. */
        final String role;
        final Supplier<RequestPostProcessor> token;

        Caller(String criterion, String role, Supplier<RequestPostProcessor> token) {
            this.criterion = criterion;
            this.role = role;
            this.token = token;
        }
    }

    @Autowired
    MockMvcTester mvc;

    static Stream<Arguments> matrix() {
        return ContractOperations.ALL.stream().flatMap(operation -> Arrays.stream(Caller.values())
                .map(caller -> Arguments.of(
                        caller.criterion + " " + operation + " as " + caller + " → " + expectedStatus(operation, caller),
                        operation, caller)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("matrix")
    @DisplayName("AUTHZ-03 every operation × caller gets the status the contract implies")
    void matrixCell(String name, Operation operation, Caller caller) {
        Operation request = operation.operationId().equals("updateClaim") && caller == Caller.ANALYST
                ? ContractOperations.UPDATE_CLAIM_STATUS
                : operation;

        MvcTestResult result = request.call(mvc, caller.token.get());

        int expected = expectedStatus(operation, caller);
        switch (expected) {
            case 401 -> assertUnauthorized(result);
            case 403 -> assertProblem(result, 403, "forbidden");
            default -> assertThat(result).hasStatus(expected);
        }
    }

    @Test
    @DisplayName("AUTHZ-02 every operation in the contract is in the matrix, and nothing else is")
    void matrixCoversTheContract() {
        assertThat(ContractOperations.ALL).extracting(Operation::operationId)
                .containsExactlyInAnyOrderElementsOf(REQUIRED_ROLES.keySet());
        assertThat(REQUIRED_ROLES).allSatisfy((operationId, roles) ->
                assertThat(roles).as("x-required-roles of %s", operationId).isNotEmpty());
    }

    private static int expectedStatus(Operation operation, Caller caller) {
        if (caller == Caller.ANONYMOUS) {
            return 401;
        }
        List<String> allowed = REQUIRED_ROLES.get(operation.operationId());
        return caller.role != null && allowed.contains(caller.role) ? operation.successStatus() : 403;
    }
}
