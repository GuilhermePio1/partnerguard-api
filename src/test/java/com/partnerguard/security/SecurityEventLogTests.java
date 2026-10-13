package com.partnerguard.security;

import com.partnerguard.support.SecurityTestTokens;
import com.partnerguard.support.SmallRateLimitsIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;

import static com.partnerguard.support.Fixtures.CLAIM_B1;
import static com.partnerguard.support.Fixtures.PARTNER_A;
import static org.assertj.core.api.Assertions.assertThat;

/** specs/audit.md rule 5: one WARN line per security event, with the listed fields. */
class SecurityEventLogTests extends SmallRateLimitsIntegrationTest {

    private static final String TOKEN = "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJ4In0.c2lnbmF0dXJlLXZhbHVl";

    @Test
    @DisplayName("AUD-06 a 401 writes one authentication_failed line with ip, method, path and reason, and no token")
    void authenticationFailed(CapturedOutput output) {
        mvc.get().uri("/api/v1/claims").with(from("10.7.1.1"))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN).exchange();

        String line = onlyEvent(output, "authentication_failed");
        assertThat(line).contains("ip=10.7.1.1", "method=GET", "path=/api/v1/claims", "reason=");
        assertNoCredentials(line);
    }

    @Test
    @DisplayName("AUD-06 a 403 writes one access_denied line with sub, roles, method, path and reason")
    void accessDenied(CapturedOutput output) {
        mvc.post().uri("/api/v1/partners").with(from("10.7.2.1")).with(SecurityTestTokens.analyst())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"legalName\":\"Oficina Gama Ltda\",\"taxId\":\"33444555000181\"}").exchange();

        String line = onlyEvent(output, "access_denied");
        assertThat(line).contains("sub=" + SecurityTestTokens.ANALYST_SUBJECT, "roles=ANALYST", "method=POST",
                "path=/api/v1/partners", "reason=");
        assertNoCredentials(line);
        assertThat(line).doesNotContain("33444555000181");
    }

    @Test
    @DisplayName("AUD-06 an ownership 404 writes one ownership_denied line with sub, partnerId, resourceType and resourceId")
    void ownershipDenied(CapturedOutput output) {
        mvc.get().uri("/api/v1/claims/{id}", CLAIM_B1).with(from("10.7.3.1")).with(SecurityTestTokens.partnerA())
                .exchange();

        String line = onlyEvent(output, "ownership_denied");
        assertThat(line).contains("sub=" + SecurityTestTokens.PARTNER_SUBJECT, "partnerId=" + PARTNER_A,
                "resourceType=claim", "resourceId=" + CLAIM_B1);
    }

    @Test
    @DisplayName("AUD-06 a 429 writes one rate_limited line with limit, keyType, key, method and path")
    void rateLimited(CapturedOutput output) {
        RequestPostProcessor ip = from("10.7.4.1");
        for (int i = 0; i <= ANONYMOUS + 1; i++) {
            mvc.get().uri("/actuator/health").with(ip).exchange();
        }

        String line = onlyEvent(output, "rate_limited");
        assertThat(line).contains("limit=anonymous", "keyType=ip", "key=10.7.4.1", "method=GET",
                "path=/actuator/health");
    }

    private static String onlyEvent(CapturedOutput output, String event) {
        List<String> lines = output.getOut().lines().filter(line -> line.contains("event=" + event)).toList();
        assertThat(lines).as("%s lines", event).hasSize(1);
        assertThat(lines.getFirst()).contains("WARN");
        return lines.getFirst();
    }

    private static void assertNoCredentials(String line) {
        assertThat(line).doesNotContain(TOKEN).doesNotContain("Bearer").doesNotContainIgnoringCase("authorization");
    }
}
