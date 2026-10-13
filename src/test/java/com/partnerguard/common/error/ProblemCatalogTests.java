package com.partnerguard.common.error;

import com.partnerguard.support.SecurityTestTokens;
import com.partnerguard.support.SmallRateLimitsIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static com.partnerguard.support.Fixtures.CLAIM_A1;
import static com.partnerguard.support.Fixtures.CLAIM_A2;
import static com.partnerguard.support.Fixtures.NEVER_EXISTS;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * specs/errors.md catalog, one real request per entry. Titles are copied from the spec on
 * purpose, not read from {@link ProblemType}, so a change to either side shows up here.
 * The 500 entry is in {@code UnexpectedErrorTests}, which has a controller that throws.
 */
class ProblemCatalogTests extends SmallRateLimitsIntegrationTest {

    @ParameterizedTest(name = "ERR-01 {0} is {1} \"{2}\" Problem Details")
    @CsvSource(delimiter = '|', textBlock = """
            validation-error          | 400 | Invalid request
            unauthorized              | 401 | Authentication required
            forbidden                 | 403 | Access denied
            not-found                 | 404 | Resource not found
            duplicate-tax-id          | 409 | Partner already exists
            invalid-status-transition | 409 | Invalid status transition
            claim-not-editable        | 409 | Claim can no longer be edited
            unsupported-media-type    | 415 | Unsupported media type
            rate-limit-exceeded       | 429 | Too many requests
            """)
    @DisplayName("ERR-01 each catalog entry matches the catalog's type, status and title")
    void catalogEntry(String slug, int status, String title) throws Exception {
        MvcTestResult result = trigger(slug, from("10.6.1." + status % 256));

        assertCatalogProblem(result, slug, status, title);
    }

    @Test
    @DisplayName("ERR-05 a 401 from the entry point, a 403 from the access denied handler and a 429 from the filter are all Problem Details")
    void filterErrorsAreProblemDetails() throws Exception {
        RequestPostProcessor ip = from("10.6.5.1");
        MvcTestResult unauthorized = mvc.get().uri("/api/v1/claims").with(ip)
                .header(HttpHeaders.AUTHORIZATION, "Bearer rejected-token").exchange();
        // Refused by the caller gate in the filter chain, before any controller.
        MvcTestResult forbidden = mvc.get().uri("/api/v1/claims").with(ip)
                .with(SecurityTestTokens.partnerWithoutPartnerId()).exchange();
        MvcTestResult tooMany = exhaustAnonymousBucket(from("10.6.5.2"));

        assertCatalogProblem(unauthorized, "unauthorized", 401, "Authentication required");
        assertCatalogProblem(forbidden, "forbidden", 403, "Access denied");
        assertCatalogProblem(tooMany, "rate-limit-exceeded", 429, "Too many requests");
    }

    private MvcTestResult trigger(String slug, RequestPostProcessor ip) {
        return switch (slug) {
            case "validation-error" -> mvc.get().uri("/api/v1/claims?sort=password,asc").with(ip)
                    .with(SecurityTestTokens.admin()).exchange();
            case "unauthorized" -> mvc.get().uri("/api/v1/claims").with(ip).exchange();
            case "forbidden" -> mvc.post().uri("/api/v1/partners").with(ip).with(SecurityTestTokens.analyst())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"legalName\":\"Oficina Gama Ltda\",\"taxId\":\"33444555000181\"}").exchange();
            case "not-found" -> mvc.get().uri("/api/v1/claims/{id}", NEVER_EXISTS).with(ip)
                    .with(SecurityTestTokens.admin()).exchange();
            case "duplicate-tax-id" -> mvc.post().uri("/api/v1/partners").with(ip).with(SecurityTestTokens.admin())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"legalName\":\"Another Alfa\",\"taxId\":\"11.222.333/0001-81\"}").exchange();
            case "invalid-status-transition" -> patch(CLAIM_A2, ip, SecurityTestTokens.analyst(),
                    MediaType.APPLICATION_JSON, "{\"status\":\"UNDER_REVIEW\"}");
            case "claim-not-editable" -> patch(CLAIM_A2, ip, SecurityTestTokens.partnerA(),
                    MediaType.APPLICATION_JSON, "{\"description\":\"x\"}");
            case "unsupported-media-type" -> patch(CLAIM_A1, ip, SecurityTestTokens.admin(),
                    MediaType.TEXT_PLAIN, "description=x");
            case "rate-limit-exceeded" -> exhaustAnonymousBucket(ip);
            default -> throw new IllegalArgumentException(slug);
        };
    }

    private MvcTestResult patch(UUID id, RequestPostProcessor ip, RequestPostProcessor caller,
                                MediaType contentType, String body) {
        return mvc.patch().uri("/api/v1/claims/{id}", id).with(ip).with(caller)
                .contentType(contentType).content(body).exchange();
    }

    /** anonymous-per-minute calls to /actuator/health, then the one that is refused. */
    private MvcTestResult exhaustAnonymousBucket(RequestPostProcessor ip) {
        for (int i = 0; i < ANONYMOUS; i++) {
            assertThat(mvc.get().uri("/actuator/health").with(ip).exchange()).hasStatusOk();
        }
        return mvc.get().uri("/actuator/health").with(ip).exchange();
    }

    private static void assertCatalogProblem(MvcTestResult result, String slug, int status, String title)
            throws Exception {
        assertThat(result).hasStatus(status);
        assertThat(result.getResponse().getContentType()).startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        assertThat(result).bodyJson().extractingPath("$.type")
                .isEqualTo("https://problems.partnerguard.example/" + slug);
        assertThat(result).bodyJson().extractingPath("$.title").isEqualTo(title);
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo(status);
        assertThat(result).bodyJson().extractingPath("$.instance").asString().startsWith("/");
        for (String internal : List.of("Exception", "at com.", "SQL", "springframework")) {
            assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).doesNotContain(internal);
        }
    }
}
