package com.partnerguard.support;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * HTTP-level tests through MockMvc, against the seeded Testcontainers database. Each test
 * runs in a transaction that is rolled back, so changes to the fixtures never leak.
 *
 * <p>Every request is authenticated as ADMIN unless a test overrides it, because these tests
 * are about behavior, not access. Authentication is covered by the security tests.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(ApiIntegrationTest.AuthenticatedAsAdmin.class)
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
        ProblemAssertions.assertProblem(result, status, slug);
    }

    /** Asserts a 400 validation problem whose first error names {@code field}. */
    protected static void assertInvalidField(MvcTestResult result, String field) {
        assertProblem(result, 400, "validation-error");
        assertThat(result).bodyJson().extractingPath("$.errors[*].field").asArray().contains(field);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class AuthenticatedAsAdmin {

        /** A default request whose post-processor every request in the class inherits. */
        @Bean
        MockMvcBuilderCustomizer adminByDefault() {
            return builder -> builder.defaultRequest(get("/").with(SecurityTestTokens.admin()));
        }
    }
}
