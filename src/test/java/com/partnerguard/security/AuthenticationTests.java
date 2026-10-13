package com.partnerguard.security;

import com.partnerguard.support.PostgresIntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static com.partnerguard.support.Fixtures.PARTNER_A;
import static com.partnerguard.support.ProblemAssertions.assertUnauthorized;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * specs/authentication.md rules 3 to 6, through the full filter chain with a stub
 * {@link JwtDecoder}: which tokens are accepted is the decoder's job and is covered end to
 * end by {@link KeycloakAuthenticationTests}; here we test what the API does around it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class AuthenticationTests extends PostgresIntegrationTest {

    private static final String CLAIMS = "/api/v1/claims";
    private static final String VALID_TOKEN = "valid-token";

    /** Tokens the stub decoder rejects, with the reasons a real decoder gives. */
    private static final Map<String, RuntimeException> REJECTED = Map.of(
            "expired-token", invalid("Jwt expired at 2026-10-13T15:00:00Z"),
            "bad-signature-token", new BadJwtException("Signed JWT rejected: Invalid signature"),
            "wrong-issuer-token", invalid("The iss claim is not valid"),
            "wrong-audience-token", invalid("The aud claim is not valid"));

    @MockitoBean
    JwtDecoder jwtDecoder;

    @Autowired
    MockMvcTester mvc;

    @BeforeEach
    void stubDecoder() {
        when(jwtDecoder.decode(VALID_TOKEN)).thenReturn(validJwt());
        REJECTED.forEach((token, failure) -> when(jwtDecoder.decode(token)).thenThrow(failure));
    }

    @Test
    @DisplayName("AUTHN-01 no Authorization header is a 401 Problem Details with WWW-Authenticate: Bearer")
    void missingTokenIsUnauthorized() {
        assertUnauthorized(mvc.get().uri(CLAIMS).exchange());
    }

    @Test
    @DisplayName("AUTHN-08 a valid token in ?access_token= is ignored (401), while the same token in the header works")
    void tokenInQueryParameterIsIgnored() {
        assertThat(withBearer(VALID_TOKEN)).hasStatusOk();

        assertUnauthorized(mvc.get().uri(CLAIMS + "?access_token=" + VALID_TOKEN).exchange());
    }

    @Test
    @DisplayName("AUTHN-08 a valid token in a cookie is ignored (401)")
    void tokenInCookieIsIgnored() {
        assertUnauthorized(mvc.get().uri(CLAIMS).cookie(new Cookie("access_token", VALID_TOKEN)).exchange());
    }

    @Test
    @DisplayName("AUTHN-09 an authenticated call creates no session and sets no cookie")
    void authenticatedCallIsStateless() {
        MvcTestResult result = withBearer(VALID_TOKEN);

        assertThat(result).hasStatusOk();
        assertStateless(result);
    }

    @Test
    @DisplayName("AUTHN-09 a rejected call creates no session and sets no cookie either")
    void rejectedCallIsStateless() {
        MvcTestResult result = mvc.get().uri(CLAIMS).exchange();

        assertUnauthorized(result);
        assertStateless(result);
    }

    @ParameterizedTest(name = "AUTHN-10 the 401 for {0} doesn't say which check failed")
    @ValueSource(strings = {"no-token", "expired-token", "bad-signature-token", "wrong-issuer-token",
            "wrong-audience-token"})
    @DisplayName("AUTHN-10 a 401 doesn't say which check failed")
    void unauthorizedDoesNotRevealReason(String token) {
        MvcTestResult result = token.equals("no-token") ? mvc.get().uri(CLAIMS).exchange() : withBearer(token);

        assertUnauthorized(result);
        // Rule 5 names the body; the WWW-Authenticate header is held to the same standard
        // because Spring's default challenge copies the failure message into error_description.
        for (String revealing : List.of("expired", "signature", "issuer", "audience")) {
            assertThat(body(result)).doesNotContainIgnoringCase(revealing);
            assertThat(result.getResponse().getHeader(HttpHeaders.WWW_AUTHENTICATE))
                    .doesNotContainIgnoringCase(revealing);
        }
    }

    @ParameterizedTest(name = "AUTHN-11 one authentication_failed event for {0}, without the token")
    @ValueSource(strings = {"no-token", "bad-signature-token"})
    @DisplayName("AUTHN-11 a 401 writes one authentication_failed log event")
    void unauthorizedIsLogged(String token, CapturedOutput output) {
        MvcTestResult result = token.equals("no-token") ? mvc.get().uri(CLAIMS).exchange() : withBearer(token);

        assertUnauthorized(result);
        List<String> events = output.getOut().lines()
                .filter(line -> line.contains("event=authentication_failed"))
                .toList();
        assertThat(events).hasSize(1);
        assertThat(events.getFirst())
                .contains("127.0.0.1")
                .contains("GET")
                .contains(CLAIMS)
                .contains("reason=")
                .doesNotContain(token);
    }

    private MvcTestResult withBearer(String token) {
        return mvc.get().uri(CLAIMS).header(HttpHeaders.AUTHORIZATION, "Bearer " + token).exchange();
    }

    private static void assertStateless(MvcTestResult result) {
        assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
        assertThat(result.getRequest().getSession(false)).isNull();
    }

    private static String body(MvcTestResult result) {
        try {
            return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static JwtValidationException invalid(String description) {
        return new JwtValidationException(description,
                List.of(new OAuth2Error("invalid_token", description, "https://tools.ietf.org/html/rfc6750#section-3.1")));
    }

    /** What the decoder returns for shop-a's token (specs/fixtures.md). */
    private static Jwt validJwt() {
        Instant now = Instant.now();
        return Jwt.withTokenValue(VALID_TOKEN)
                .header("alg", "RS256")
                .subject("shop-a-subject")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .audience(List.of("partnerguard-api"))
                .claim("partner_id", PARTNER_A.toString())
                .claim("realm_access", Map.of("roles", List.of("PARTNER")))
                .build();
    }
}
