package com.partnerguard.common.error;

import com.partnerguard.support.PostgresIntegrationTest;
import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ERR-08: the servlet /error dispatch only ever produces Problem Details.
 * Needs a real server: MockMvc never goes through the container's error dispatch.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ErrorDispatchTests extends PostgresIntegrationTest {

    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @LocalServerPort
    int port;

    @ParameterizedTest(name = "Accept: {0}")
    @ValueSource(strings = {"text/html", "application/json", "*/*"})
    @DisplayName("ERR-08 a filter calling sendError(403) gets Problem Details")
    void sendErrorFromFilter(String accept) throws Exception {
        HttpResponse<String> response = get("/test-errors/status/403", accept);

        assertProblem(response, 403, "forbidden", "/test-errors/status/403");
    }

    @ParameterizedTest(name = "Accept: {0}")
    @ValueSource(strings = {"text/html", "application/json"})
    @DisplayName("ERR-08 an exception thrown in a filter gets a 500 without internal details")
    void exceptionFromFilter(String accept) throws Exception {
        HttpResponse<String> response = get("/test-errors/throw", accept);

        assertProblem(response, 500, "internal-error", "/test-errors/throw");
        assertThat(response.body())
                .doesNotContain("SELECT")
                .doesNotContain("IllegalStateException")
                .doesNotContain("at com.");
        assertThat(JSON.readTree(response.body()).get("detail").asString())
                .isEqualTo("An unexpected error occurred.");
    }

    @ParameterizedTest(name = "Accept: {0}")
    @ValueSource(strings = {"text/html", "application/json"})
    @DisplayName("ERR-08 GET /error directly is a 404 Problem Details, not Boot's status 999")
    void directErrorRequest(String accept) throws Exception {
        HttpResponse<String> response = get("/error", accept);

        assertProblem(response, 404, "not-found", "/error");
    }

    @ParameterizedTest(name = "Accept: {0}")
    @ValueSource(strings = {"text/html", "application/json"})
    @DisplayName("ERR-08 statuses outside the catalog still get Problem Details")
    void statusOutsideCatalog(String accept) throws Exception {
        HttpResponse<String> response = get("/test-errors/status/503", accept);

        assertThat(response.statusCode()).isEqualTo(503);
        assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(
                contentType -> assertThat(contentType).startsWith("application/problem+json"));
        assertThat(JSON.readTree(response.body()).get("status").asInt()).isEqualTo(503);
    }

    private HttpResponse<String> get(String path, String accept) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Accept", accept)
                .GET()
                .build();
        return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static void assertProblem(HttpResponse<String> response, int status, String slug, String instance) {
        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(
                contentType -> assertThat(contentType).startsWith("application/problem+json"));
        assertThat(response.body())
                .doesNotContainIgnoringCase("<html")
                .doesNotContain("Whitelabel")
                .doesNotContain("timestamp")
                .doesNotContain("999");

        JsonNode body = JSON.readTree(response.body());
        assertThat(body.get("type").asString()).isEqualTo(ProblemType.BASE_URI + slug);
        assertThat(body.get("status").asInt()).isEqualTo(status);
        assertThat(body.get("instance").asString()).isEqualTo(instance);
    }

    @TestConfiguration
    static class FailingFilters {

        /** Simulates what Spring Security and other filters do: fail before Spring MVC. */
        @Bean
        FilterRegistrationBean<Filter> failingFilter() {
            Filter filter = (request, response, chain) -> {
                String uri = ((jakarta.servlet.http.HttpServletRequest) request).getRequestURI();
                if (uri.equals("/test-errors/throw")) {
                    throw new IllegalStateException("SELECT * FROM secret");
                }
                int status = Integer.parseInt(uri.substring(uri.lastIndexOf('/') + 1));
                ((HttpServletResponse) response).sendError(status);
            };
            FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>(filter);
            registration.addUrlPatterns("/test-errors/*");
            return registration;
        }
    }
}
