package com.partnerguard.support;

import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static org.assertj.core.api.Assertions.assertThat;

/** Assertions on Problem Details responses (specs/errors.md). */
public final class ProblemAssertions {

    private ProblemAssertions() {
    }

    /** Asserts a catalog Problem Details response. */
    public static void assertProblem(MvcTestResult result, int status, String slug) {
        assertThat(result).hasStatus(status);
        assertThat(result).hasContentTypeCompatibleWith("application/problem+json");
        assertThat(result).bodyJson().extractingPath("$.type")
                .isEqualTo("https://problems.partnerguard.example/" + slug);
    }

    /**
     * Asserts the 401 contract (specs/authentication.md rule 5): Problem Details of type
     * {@code unauthorized} plus a {@code WWW-Authenticate: Bearer} challenge. Checking more than
     * the status matters: Spring Security's defaults also answer 401, but with an empty body.
     */
    public static void assertUnauthorized(MvcTestResult result) {
        assertProblem(result, 401, "unauthorized");
        assertThat(result.getResponse().getHeader(HttpHeaders.WWW_AUTHENTICATE)).startsWith("Bearer");
    }
}
