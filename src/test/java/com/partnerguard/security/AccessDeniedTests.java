package com.partnerguard.security;

import com.partnerguard.support.ApiIntegrationTest;
import com.partnerguard.support.SecurityTestTokens;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Deny by default and the 403 log (specs/authorization.md rules 8 and 10, ADR-0005). */
@ExtendWith(OutputCaptureExtension.class)
class AccessDeniedTests extends ApiIntegrationTest {

    @Test
    @DisplayName("AUTHZ-13 an authenticated caller gets 403 outside /api/** and 404 for an unknown path inside it")
    void denyAllOutsideApi() {
        assertProblem(mvc.get().uri("/internal/anything").exchange(), 403, "forbidden");
        assertProblem(mvc.get().uri("/api/v1/unknown").exchange(), 404, "not-found");
    }

    @Test
    @DisplayName("AUTHZ-16 a 403 writes one access_denied log event with sub, method and path")
    void accessDenialIsLogged(CapturedOutput output) {
        assertProblem(mvc.post().uri("/api/v1/partners").with(SecurityTestTokens.analyst())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"legalName\":\"Oficina Gama Ltda\",\"taxId\":\"33.444.555/0001-81\"}")
                .exchange(), 403, "forbidden");

        List<String> events = output.getOut().lines().filter(line -> line.contains("event=access_denied")).toList();
        assertThat(events).hasSize(1);
        assertThat(events.getFirst())
                .contains(SecurityTestTokens.ANALYST_SUBJECT)
                .contains("POST")
                .contains("/api/v1/partners");
    }
}
