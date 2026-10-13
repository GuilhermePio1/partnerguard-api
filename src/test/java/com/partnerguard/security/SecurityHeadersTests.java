package com.partnerguard.security;

import com.partnerguard.support.ApiIntegrationTest;
import com.partnerguard.support.SecurityTestTokens;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.util.Map;

import static com.partnerguard.support.Fixtures.NEVER_EXISTS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;

/** specs/hardening.md, the header table and rule 11. */
class SecurityHeadersTests extends ApiIntegrationTest {

    private static final String CSP = "default-src 'none'; frame-ancestors 'none'";

    /** Every response, /api/** or not. */
    private static final Map<String, String> COMMON_HEADERS = Map.of(
            "Content-Security-Policy", CSP,
            "X-Content-Type-Options", "nosniff",
            "X-Frame-Options", "DENY",
            "Referrer-Policy", "no-referrer");

    @Test
    @DisplayName("HRD-01 a 200 from /api/** has every header in the table, with Cache-Control: no-store")
    void apiSuccessHasSecurityHeaders() {
        assertApiHeaders(mvc.get().uri("/api/v1/claims").exchange(), 200);
    }

    @Test
    @DisplayName("HRD-01 /api/** errors from MVC (404, 400) carry the same headers")
    void apiMvcErrorsHaveSecurityHeaders() {
        assertApiHeaders(mvc.get().uri("/api/v1/claims/{id}", NEVER_EXISTS).exchange(), 404);
        assertApiHeaders(mvc.patch().uri("/api/v1/claims/{id}", NEVER_EXISTS)
                .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":-1}").exchange(), 400);
    }

    @Test
    @DisplayName("HRD-01 /api/** errors from Spring Security (401, 403) carry the same headers")
    void apiSecurityErrorsHaveSecurityHeaders() {
        assertApiHeaders(mvc.get().uri("/api/v1/claims").with(anonymous()).exchange(), 401);
        assertApiHeaders(mvc.get().uri("/api/v1/partners").with(SecurityTestTokens.partnerA()).exchange(), 403);
    }

    @Test
    @DisplayName("HRD-02 /actuator/health has the same headers except Cache-Control")
    void healthHasSecurityHeaders() {
        MvcTestResult result = mvc.get().uri("/actuator/health").with(anonymous()).exchange();

        assertThat(result).hasStatusOk();
        assertCommonHeaders(result.getResponse());
    }

    @Test
    @DisplayName("HRD-09 a CORS preflight from https://evil.example gets no Access-Control-Allow-Origin")
    void corsPreflightIsNotAllowed() {
        MvcTestResult result = mvc.options().uri("/api/v1/claims").with(anonymous())
                .header(HttpHeaders.ORIGIN, "https://evil.example")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization")
                .exchange();

        assertThat(result.getResponse().getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isNull();
        assertThat(result.getResponse().getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS)).isNull();
    }

    @Test
    @DisplayName("HRD-09 a simple cross-origin GET gets no Access-Control-Allow-Origin either")
    void corsSimpleRequestIsNotAllowed() {
        MvcTestResult result = mvc.get().uri("/api/v1/claims")
                .header(HttpHeaders.ORIGIN, "https://evil.example").exchange();

        assertThat(result.getResponse().getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isNull();
    }

    private static void assertApiHeaders(MvcTestResult result, int status) {
        assertThat(result).hasStatus(status);
        assertCommonHeaders(result.getResponse());
        assertThat(result.getResponse().getHeaders(HttpHeaders.CACHE_CONTROL)).containsExactly("no-store");
    }

    private static void assertCommonHeaders(MockHttpServletResponse response) {
        COMMON_HEADERS.forEach((name, value) ->
                assertThat(response.getHeaders(name)).as(name).containsExactly(value));
    }
}
