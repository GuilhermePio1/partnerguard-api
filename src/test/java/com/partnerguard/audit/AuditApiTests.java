package com.partnerguard.audit;

import com.partnerguard.support.ApiIntegrationTest;
import com.partnerguard.support.SecurityTestTokens;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static com.partnerguard.support.Fixtures.CLAIM_A1;
import static com.partnerguard.support.Fixtures.CLAIM_A2;
import static com.partnerguard.support.Fixtures.CLAIM_B1;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * specs/audit.md rules 1 to 3 (ADR-0012, ADR-0023) through the API. Each test rolls back, so
 * rows are read through the JPA repository, whose queries flush the pending insert first.
 */
class AuditApiTests extends ApiIntegrationTest {

    private static final String PARTNERS = "/api/v1/partners";
    private static final String NEW_PARTNER = """
            {"legalName":"Oficina Gama Ltda","taxId":"12.ABC.345/01DE-35"}""";

    @Autowired
    AuditLogRepository auditLog;

    @Test
    @DisplayName("AUD-01 a successful POST /partners writes one PARTNER_CREATED row with the admin's sub")
    void partnerCreatedIsAudited() {
        MvcTestResult result = createPartner();

        assertThat(result).hasStatus(201);
        AuditEntry entry = onlyEntryFor("partner:" + createdId(result));
        assertThat(entry.getAction()).isEqualTo(AuditAction.PARTNER_CREATED);
        assertThat(entry.getUserId()).isEqualTo(SecurityTestTokens.ADMIN_SUBJECT);
    }

    @Test
    @DisplayName("PRT-12 a successful create writes PARTNER_CREATED with resource partner:<id>, userId the admin's sub and the fields it set")
    void partnerCreatedEntryNamesTheResource() {
        MvcTestResult result = createPartner();

        AuditEntry entry = onlyEntryFor("partner:" + createdId(result));
        assertThat(entry.getResource()).isEqualTo("partner:" + createdId(result));
        assertThat(entry.getUserId()).isEqualTo(SecurityTestTokens.ADMIN_SUBJECT);
        assertThat(entry.getDetails()).isEqualTo(Map.of("fields", List.of("legalName", "taxId")));
        assertThat(entry.getTimestamp()).isNotNull();
    }

    @Test
    @DisplayName("AUD-02 a successful PATCH writes one CLAIM_UPDATED row whose details.fields lists the fields it carried")
    void claimUpdatedIsAudited() {
        MvcTestResult result = patchClaim(CLAIM_A2, SecurityTestTokens.admin(),
                "{\"amount\":1234.56,\"status\":\"APPROVED\"}");

        assertThat(result).hasStatusOk();
        AuditEntry entry = onlyEntryFor("claim:" + CLAIM_A2);
        assertThat(entry.getAction()).isEqualTo(AuditAction.CLAIM_UPDATED);
        assertThat(entry.getUserId()).isEqualTo(SecurityTestTokens.ADMIN_SUBJECT);
        assertThat(entry.getDetails()).isEqualTo(Map.of("fields", List.of("status", "amount")));
    }

    @Test
    @DisplayName("CLM-22 a successful PATCH writes exactly one CLAIM_UPDATED entry listing the fields and no values")
    void claimUpdateEntryHasNamesOnly() {
        MvcTestResult result = patchClaim(CLAIM_A1, SecurityTestTokens.partnerA(),
                "{\"description\":\"Rear bumper replaced, invoice 4471\"}");

        assertThat(result).hasStatusOk();
        AuditEntry entry = onlyEntryFor("claim:" + CLAIM_A1);
        assertThat(entry.getUserId()).isEqualTo(SecurityTestTokens.PARTNER_SUBJECT);
        assertThat(entry.getDetails()).isEqualTo(Map.of("fields", List.of("description")));
    }

    @Test
    @DisplayName("CLM-22 a PATCH that repeats the current value is still one entry naming the field")
    void unchangedValueIsStillAudited() {
        String current = body(mvc.get().uri("/api/v1/claims/{id}", CLAIM_A1).exchange());
        String description = current.replaceAll(".*\"description\":\"([^\"]*)\".*", "$1");

        assertThat(patchClaim(CLAIM_A1, SecurityTestTokens.admin(),
                "{\"description\":\"" + description + "\"}")).hasStatusOk();

        assertThat(onlyEntryFor("claim:" + CLAIM_A1).getDetails())
                .isEqualTo(Map.of("fields", List.of("description")));
    }

    @ParameterizedTest(name = "AUD-03 a rejected write ({0}) writes no audit row")
    @MethodSource("rejectedWrites")
    @DisplayName("AUD-03 a rejected write writes no audit row")
    void rejectedWriteIsNotAudited(String why, int expectedStatus, String method, String uri,
                                   RequestPostProcessor caller, String json) {
        long before = auditLog.count();

        MvcTestResult result = "POST".equals(method)
                ? mvc.post().uri(uri).with(caller).contentType(MediaType.APPLICATION_JSON).content(json).exchange()
                : mvc.patch().uri(uri).with(caller).contentType(MediaType.APPLICATION_JSON).content(json).exchange();

        assertThat(result).hasStatus(expectedStatus);
        assertThat(auditLog.count()).isEqualTo(before);
    }

    static Stream<Arguments> rejectedWrites() {
        String claimA1 = "/api/v1/claims/" + CLAIM_A1;
        return Stream.of(
                Arguments.of("400 invalid body", 400, "PATCH", claimA1, SecurityTestTokens.admin(),
                        "{\"amount\":-1}"),
                Arguments.of("400 invalid partner", 400, "POST", PARTNERS, SecurityTestTokens.admin(),
                        "{\"legalName\":\"Oficina Gama Ltda\",\"taxId\":\"00000000000000\"}"),
                Arguments.of("403 field not allowed", 403, "PATCH", claimA1, SecurityTestTokens.analyst(),
                        "{\"description\":\"x\"}"),
                Arguments.of("403 role not allowed", 403, "POST", PARTNERS, SecurityTestTokens.analyst(),
                        NEW_PARTNER),
                Arguments.of("404 another partner's claim", 404, "PATCH", "/api/v1/claims/" + CLAIM_B1,
                        SecurityTestTokens.partnerA(), "{\"description\":\"x\"}"),
                Arguments.of("409 invalid transition", 409, "PATCH", "/api/v1/claims/" + CLAIM_A2,
                        SecurityTestTokens.analyst(), "{\"status\":\"UNDER_REVIEW\"}"),
                Arguments.of("409 duplicate tax id", 409, "POST", PARTNERS, SecurityTestTokens.admin(),
                        "{\"legalName\":\"Another Alfa\",\"taxId\":\"11.222.333/0001-81\"}"));
    }

    @Test
    @DisplayName("AUD-05 audit rows hold no value from the request body")
    void entriesHoldNoRequestValues() {
        MvcTestResult created = createPartner();
        patchClaim(CLAIM_A2, SecurityTestTokens.admin(), "{\"amount\":4321.09,\"description\":\"Secret note 7781\"}");

        List<AuditEntry> entries = List.of(
                onlyEntryFor("partner:" + createdId(created)), onlyEntryFor("claim:" + CLAIM_A2));
        for (AuditEntry entry : entries) {
            assertThat(entry.getDetails().toString())
                    .doesNotContain("Oficina Gama")
                    .doesNotContainIgnoringCase("12ABC34501DE35")
                    .doesNotContain("12.ABC")
                    .doesNotContain("4321")
                    .doesNotContain("Secret note");
            assertThat(entry.getResource()).doesNotContain("Oficina").doesNotContain("Secret");
        }
    }

    private MvcTestResult createPartner() {
        return mvc.post().uri(PARTNERS).contentType(MediaType.APPLICATION_JSON).content(NEW_PARTNER).exchange();
    }

    private MvcTestResult patchClaim(UUID id, RequestPostProcessor caller, String json) {
        return mvc.patch().uri("/api/v1/claims/{id}", id).with(caller)
                .contentType(MediaType.APPLICATION_JSON).content(json).exchange();
    }

    private static UUID createdId(MvcTestResult result) {
        return UUID.fromString(body(result).replaceAll(".*\"id\":\"([^\"]+)\".*", "$1"));
    }

    private AuditEntry onlyEntryFor(String resource) {
        List<AuditEntry> entries = auditLog.findAll().stream()
                .filter(entry -> entry.getResource().equals(resource))
                .toList();
        assertThat(entries).as("audit entries for %s", resource).hasSize(1);
        return entries.getFirst();
    }
}
