package com.partnerguard.support;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HTTP-level tests through MockMvc, against the seeded Testcontainers database. Each test
 * runs in a transaction that is rolled back, so changes to the fixtures never leak.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public abstract class ApiIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    protected MockMvcTester mvc;

    protected static String body(MvcTestResult result) {
        try {
            return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Asserts a catalog Problem Details response (specs/errors.md). */
    protected static void assertProblem(MvcTestResult result, int status, String slug) {
        assertThat(result).hasStatus(status);
        assertThat(result).hasContentTypeCompatibleWith("application/problem+json");
        assertThat(result).bodyJson().extractingPath("$.type")
                .isEqualTo("https://problems.partnerguard.example/" + slug);
    }

    /** Asserts a 400 validation problem whose first error names {@code field}. */
    protected static void assertInvalidField(MvcTestResult result, String field) {
        assertProblem(result, 400, "validation-error");
        assertThat(result).bodyJson().extractingPath("$.errors[*].field").asArray().contains(field);
    }
}
