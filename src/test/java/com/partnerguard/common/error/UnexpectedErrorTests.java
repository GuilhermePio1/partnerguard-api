package com.partnerguard.common.error;

import com.partnerguard.support.ApiIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ERR-03 for exceptions thrown inside Spring MVC, which ApiExceptionHandler renders.
 * (Exceptions thrown before MVC, in filters, are covered by ErrorDispatchTests.)
 */
@Import(UnexpectedErrorTests.ExplodingController.class)
class UnexpectedErrorTests extends ApiIntegrationTest {

    @Test
    @DisplayName("ERR-03 an unexpected exception is a 500 without SQL, class names or stack traces")
    void unexpectedExceptionIsSanitized() {
        MvcTestResult result = mvc.get().uri("/api/test-errors/explode").exchange();

        assertProblem(result, 500, "internal-error");
        assertThat(result).bodyJson().extractingPath("$.detail").isEqualTo("An unexpected error occurred.");
        assertThat(body(result))
                .doesNotContain("SELECT")
                .doesNotContain("secret")
                .doesNotContain("IllegalStateException")
                .doesNotContain("at com.");
    }

    /** Nested in a test class, so component scanning skips it; only this test imports it. */
    @RestController
    static class ExplodingController {

        @GetMapping("/api/test-errors/explode")
        String explode() {
            throw new IllegalStateException("SELECT * FROM secret");
        }
    }
}
