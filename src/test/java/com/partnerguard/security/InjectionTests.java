package com.partnerguard.security;

import com.partnerguard.support.ApiIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.util.List;

import static com.partnerguard.support.Fixtures.CLAIM_A1;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * specs/hardening.md rules 2 to 4: SQL payloads are either refused by format checks (400) or
 * stored verbatim as data, and the schema is the same afterwards.
 */
class InjectionTests extends ApiIntegrationTest {

    private static final List<String> TABLES = List.of("audit_log", "claim", "contract", "partner", "policy");

    @Autowired
    JdbcTemplate jdbc;

    private long claimsBefore;

    @BeforeEach
    void countClaims() {
        claimsBefore = claimCount();
    }

    @AfterEach
    void schemaIsUnchanged() {
        assertThat(jdbc.queryForList("select table_name from information_schema.tables "
                + "where table_schema = 'public' and table_name <> 'flyway_schema_history' order by table_name",
                String.class)).containsExactlyElementsOf(TABLES);
        assertThat(claimCount()).isEqualTo(claimsBefore);
    }

    @ParameterizedTest(name = "HRD-07 the description {0} is stored verbatim")
    @ValueSource(strings = {"' OR '1'='1", "1; DROP TABLE claim", "'; DROP TABLE claim; --",
            "x' UNION SELECT tax_id FROM partner --"})
    @DisplayName("HRD-07 SQL in free text is stored verbatim, with no side effect")
    void sqlInFreeTextIsData(String payload) {
        MvcTestResult patched = mvc.patch().uri("/api/v1/claims/{id}", CLAIM_A1)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"description\":\"" + payload + "\"}").exchange();

        assertThat(patched).hasStatusOk();
        assertThat(mvc.get().uri("/api/v1/claims/{id}", CLAIM_A1).exchange())
                .bodyJson().extractingPath("$.description").isEqualTo(payload);
    }

    @ParameterizedTest(name = "HRD-07 the query {0} is a 400")
    @ValueSource(strings = {"status=' OR '1'='1", "status=1; DROP TABLE claim", "sort=1; DROP TABLE claim",
            "sort=description' OR '1'='1,asc", "page=1; DROP TABLE claim", "size=' OR '1'='1"})
    @DisplayName("HRD-07 SQL in query parameters is refused by the format checks")
    void sqlInQueryParametersIsRefused(String query) {
        MvcTestResult result = mvc.get().uri("/api/v1/claims?" + query).exchange();

        assertProblem(result, 400, "validation-error");
    }

    @Test
    @DisplayName("HRD-07 SQL in a path parameter is refused by the format check")
    void sqlInPathIsRefused() {
        MvcTestResult result = mvc.get().uri("/api/v1/claims/{id}", "1' OR '1'='1").exchange();

        assertProblem(result, 400, "validation-error");
    }

    private long claimCount() {
        Long count = jdbc.queryForObject("select count(*) from claim", Long.class);
        return count == null ? 0 : count;
    }
}
