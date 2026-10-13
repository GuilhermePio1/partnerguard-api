package com.partnerguard.security;

import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static com.partnerguard.support.Fixtures.CLAIM_A1;
import static com.partnerguard.support.Fixtures.CONTRACT_A;
import static com.partnerguard.support.Fixtures.POLICY_1;

/**
 * One valid request per operation in contracts/openapi.yaml, aimed at Partner A's resources
 * (specs/fixtures.md), so an allowed caller gets the success status and nothing else can
 * explain a 403. Shared by the security matrix and the suspended-partner tests.
 */
final class ContractOperations {

    /** A request that succeeds for any caller allowed to make it. */
    record Operation(String operationId, HttpMethod method, String path, String body, int successStatus) {

        MvcTestResult call(MockMvcTester mvc, RequestPostProcessor caller) {
            MockMvcTester.MockMvcRequestBuilder request = mvc.method(method).uri(path);
            if (body != null) {
                request.contentType(MediaType.APPLICATION_JSON).content(body);
            }
            if (caller != null) {
                request.with(caller);
            }
            return request.exchange();
        }

        @Override
        public String toString() {
            return operationId;
        }
    }

    static final List<Operation> ALL = List.of(
            new Operation("listPartners", HttpMethod.GET, "/api/v1/partners", null, 200),
            new Operation("createPartner", HttpMethod.POST, "/api/v1/partners",
                    "{\"legalName\":\"Oficina Gama Ltda\",\"taxId\":\"33.444.555/0001-81\"}", 201),
            new Operation("getCurrentPartner", HttpMethod.GET, "/api/v1/partners/me", null, 200),
            new Operation("listContracts", HttpMethod.GET, "/api/v1/contracts", null, 200),
            new Operation("getContract", HttpMethod.GET, "/api/v1/contracts/" + CONTRACT_A, null, 200),
            new Operation("listClaims", HttpMethod.GET, "/api/v1/claims", null, 200),
            new Operation("getClaim", HttpMethod.GET, "/api/v1/claims/" + CLAIM_A1, null, 200),
            // PARTNER and ADMIN may change the description; ANALYST uses UPDATE_CLAIM_STATUS.
            new Operation("updateClaim", HttpMethod.PATCH, "/api/v1/claims/" + CLAIM_A1,
                    "{\"description\":\"Front bumper and grille\"}", 200),
            new Operation("getPolicyByNumber", HttpMethod.GET, "/api/v1/policies/" + POLICY_1, null, 200));

    /** ANALYST may only change the status (ADR-0009): SUBMITTED → UNDER_REVIEW is a valid move. */
    static final Operation UPDATE_CLAIM_STATUS = new Operation("updateClaim", HttpMethod.PATCH,
            "/api/v1/claims/" + CLAIM_A1, "{\"status\":\"UNDER_REVIEW\"}", 200);

    private ContractOperations() {
    }

    /** {@code operationId → x-required-roles}, read from the contract itself. */
    @SuppressWarnings("unchecked")
    static Map<String, List<String>> requiredRolesFromContract() {
        Map<String, Object> contract;
        try (Reader reader = Files.newBufferedReader(Path.of("contracts/openapi.yaml"))) {
            contract = new Yaml().load(reader);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        Map<String, List<String>> roles = new TreeMap<>();
        Map<String, Map<String, Object>> paths = (Map<String, Map<String, Object>>) contract.get("paths");
        paths.values().forEach(pathItem -> pathItem.forEach((method, value) -> {
            if (value instanceof Map<?, ?> operation && operation.containsKey("operationId")) {
                roles.put((String) operation.get("operationId"), (List<String>) operation.get("x-required-roles"));
            }
        }));
        return roles;
    }
}
