package com.partnerguard.common.error;

import com.partnerguard.support.ApiIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

import static com.partnerguard.support.Fixtures.NEVER_EXISTS;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * ERR-03 and ERR-06 for exceptions thrown inside Spring MVC, which ApiExceptionHandler renders.
 * (Exceptions thrown before MVC, in filters, are covered by ErrorDispatchTests.)
 */
@ExtendWith(OutputCaptureExtension.class)
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

    @Test
    @DisplayName("ERR-01 internal-error is 500 \"Internal error\" Problem Details")
    void internalErrorMatchesTheCatalog() {
        MvcTestResult result = mvc.get().uri("/api/test-errors/explode").exchange();

        assertThat(result).hasStatus(500);
        assertThat(result.getResponse().getContentType()).startsWith("application/problem+json");
        assertThat(result).bodyJson().extractingPath("$.type")
                .isEqualTo("https://problems.partnerguard.example/internal-error");
        assertThat(result).bodyJson().extractingPath("$.title").isEqualTo("Internal error");
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo(500);
    }

    @Test
    @DisplayName("ERR-06 a 500 carries X-Correlation-Id, and the same id is in the server log with the exception")
    void correlationIdOfA500IsLogged(CapturedOutput output) {
        MvcTestResult result = mvc.get().uri("/api/test-errors/explode").exchange();

        String correlationId = correlationId(result);
        assertThat(output.getOut().lines()
                .filter(line -> line.contains("event=unhandled_exception"))
                .filter(line -> line.contains("correlationId=" + correlationId)))
                .hasSize(1);
        assertThat(output.getOut()).contains("SELECT * FROM secret");
    }

    @Test
    @DisplayName("ERR-06 every response carries its own X-Correlation-Id (200, 404)")
    void everyResponseHasACorrelationId() {
        String ok = correlationId(mvc.get().uri("/api/v1/claims").exchange());
        String notFound = correlationId(mvc.get().uri("/api/v1/claims/{id}", NEVER_EXISTS).exchange());

        assertThat(ok).isNotEqualTo(notFound);
    }

    @Test
    @DisplayName("ERR-06 a client-sent X-Correlation-Id is ignored; the server generates its own")
    void clientCorrelationIdIsIgnored() {
        String planted = "11111111-1111-4111-8111-111111111111";

        MvcTestResult result = mvc.get().uri("/api/v1/claims").header("X-Correlation-Id", planted).exchange();

        assertThat(correlationId(result)).isNotEqualTo(planted);
    }

    /** The response's X-Correlation-Id, asserted to be a single UUID. */
    private static String correlationId(MvcTestResult result) {
        assertThat(result.getResponse().getHeaders("X-Correlation-Id")).hasSize(1);
        String correlationId = result.getResponse().getHeader("X-Correlation-Id");
        assertThat(UUID.fromString(correlationId).toString()).isEqualTo(correlationId);
        return correlationId;
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
