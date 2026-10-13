package com.partnerguard.security;

import com.partnerguard.support.MutableClock;
import com.partnerguard.support.PostgresIntegrationTest;
import com.partnerguard.support.SecurityTestTokens;
import com.partnerguard.support.TestClockConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static com.partnerguard.support.Fixtures.CLAIM_A1;
import static com.partnerguard.support.ProblemAssertions.assertProblem;
import static com.partnerguard.support.ProblemAssertions.assertUnauthorized;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * specs/rate-limiting.md through the full filter chain, with small limits (ADR-0006, ADR-0022).
 *
 * <p>Valid callers come from {@link SecurityTestTokens} (already-validated JWTs). Forged
 * tokens go through a stub {@link JwtDecoder} that rejects every token, as a real decoder
 * rejects a bad signature, so they never become an authentication.
 *
 * <p>Each test starts two minutes after the previous one on a pinned clock, so every bucket
 * is full again and the once-a-minute log filter is clear. Each test also uses its own client
 * IP, so the IP ceiling of one test never leaks into another.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestClockConfig.class)
@ExtendWith(OutputCaptureExtension.class)
@Transactional
@TestPropertySource(properties = {
        "partnerguard.rate-limit.ip-ceiling-per-minute=" + RateLimitTests.IP_CEILING,
        "partnerguard.rate-limit.user-per-minute=" + RateLimitTests.USER,
        "partnerguard.rate-limit.user-writes-per-minute=" + RateLimitTests.USER_WRITES,
        "partnerguard.rate-limit.anonymous-per-minute=" + RateLimitTests.ANONYMOUS
})
class RateLimitTests extends PostgresIntegrationTest {

    static final int IP_CEILING = 10;
    static final int USER = 3;
    static final int USER_WRITES = 2;
    static final int ANONYMOUS = 3;

    private static final String CLAIMS = "/api/v1/claims";
    private static final String HEALTH = "/actuator/health";

    /** Test n runs on a clock pinned 2n minutes after START. */
    private static final Instant START = Instant.now();
    private static final AtomicInteger TESTS_RUN = new AtomicInteger();

    @Autowired
    MockMvcTester mvc;

    @Autowired
    MutableClock clock;

    @MockitoBean
    JwtDecoder jwtDecoder;

    @BeforeEach
    void freshBuckets() {
        clock.pin(START.plus(Duration.ofMinutes(2L * TESTS_RUN.incrementAndGet())));
        when(jwtDecoder.decode(anyString())).thenThrow(new BadJwtException("Signed JWT rejected: Invalid signature"));
    }

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    @Test
    @DisplayName("RL-01 user-per-minute + 1 GETs by one user: the last one is 429 with Retry-After and Problem Details")
    void userLimit() {
        RequestPostProcessor ip = from("10.0.1.1");
        for (int i = 0; i < USER; i++) {
            assertThat(mvc.get().uri(CLAIMS).with(SecurityTestTokens.partnerA()).with(ip).exchange()).hasStatusOk();
        }

        MvcTestResult limited = mvc.get().uri(CLAIMS).with(SecurityTestTokens.partnerA()).with(ip).exchange();

        assertRateLimited(limited);
    }

    @Test
    @DisplayName("RL-02 when shop-a is limited, analyst calling from the same IP still gets 200")
    void bucketsArePerSubject() {
        RequestPostProcessor ip = from("10.0.2.1");
        for (int i = 0; i < USER; i++) {
            mvc.get().uri(CLAIMS).with(SecurityTestTokens.partnerA()).with(ip).exchange();
        }
        assertRateLimited(mvc.get().uri(CLAIMS).with(SecurityTestTokens.partnerA()).with(ip).exchange());

        assertThat(mvc.get().uri(CLAIMS).with(SecurityTestTokens.analyst()).with(ip).exchange()).hasStatusOk();
    }

    @Test
    @DisplayName("RL-03 user-writes-per-minute + 1 PATCHes: the last one is 429 while the same user's GETs still succeed")
    void writeLimit() {
        RequestPostProcessor ip = from("10.0.3.1");
        for (int i = 0; i < USER_WRITES; i++) {
            assertThat(patchClaim(ip)).hasStatusOk();
        }

        assertRateLimited(patchClaim(ip));
        assertThat(mvc.get().uri(CLAIMS).with(SecurityTestTokens.admin()).with(ip).exchange()).hasStatusOk();
    }

    @Test
    @DisplayName("RL-04 forged tokens with a random sub from one IP hit the IP ceiling (a forged sub creates no bucket)")
    void forgedSubjectsHitTheIpCeiling() {
        RequestPostProcessor ip = from("10.0.4.1");
        for (int i = 0; i < IP_CEILING; i++) {
            assertUnauthorized(withBearer(forgedToken(UUID.randomUUID().toString()), ip));
        }

        assertRateLimited(withBearer(forgedToken(UUID.randomUUID().toString()), ip));
    }

    @Test
    @DisplayName("RL-05 forged tokens carrying analyst's sub leave analyst's own bucket untouched")
    void forgedVictimSubjectDoesNotDrainTheVictim() {
        RequestPostProcessor attacker = from("10.0.5.1");
        for (int i = 0; i <= USER; i++) {
            assertUnauthorized(withBearer(forgedToken(SecurityTestTokens.ANALYST_SUBJECT), attacker));
        }

        RequestPostProcessor victim = from("10.0.5.2");
        for (int i = 0; i < USER; i++) {
            assertThat(mvc.get().uri(CLAIMS).with(SecurityTestTokens.analyst()).with(victim).exchange()).hasStatusOk();
        }
    }

    @Test
    @DisplayName("RL-06 anonymous calls to /actuator/health over the anonymous limit get 429")
    void anonymousLimitOnHealth() {
        RequestPostProcessor ip = from("10.0.6.1");
        for (int i = 0; i < ANONYMOUS; i++) {
            assertThat(mvc.get().uri(HEALTH).with(ip).exchange()).hasStatusOk();
        }

        assertRateLimited(mvc.get().uri(HEALTH).with(ip).exchange());
    }

    @Test
    @DisplayName("RL-11 several 429s for one key within a minute write one rate_limited event; a minute later, one more")
    void rateLimitedIsLoggedOncePerKeyPerMinute(CapturedOutput output) {
        RequestPostProcessor ip = from("10.0.11.1");
        exhaustAndHitTwice(ip);
        assertThat(rateLimitedEvents(output)).hasSize(1);
        assertThat(rateLimitedEvents(output).getFirst())
                .contains("key_type=sub")
                .contains("key=" + SecurityTestTokens.PARTNER_SUBJECT)
                .contains("path=" + CLAIMS);

        clock.pin(clock.instant().plus(Duration.ofMinutes(1)));
        exhaustAndHitTwice(ip);

        assertThat(rateLimitedEvents(output)).hasSize(2);
    }

    @Test
    @DisplayName("RL-12 anonymous-per-minute + 1 calls without a token to GET /api/v1/claims: the last one is 429, not 401")
    void anonymousLimitOnProtectedPath() {
        RequestPostProcessor ip = from("10.0.12.1");
        for (int i = 0; i < ANONYMOUS; i++) {
            assertUnauthorized(mvc.get().uri(CLAIMS).with(ip).exchange());
        }

        assertRateLimited(mvc.get().uri(CLAIMS).with(ip).exchange());
    }

    @Test
    @DisplayName("RL-13 once the IP ceiling is exhausted, a request with a token gets 429 and the token is never decoded")
    void ipCeilingRejectsBeforeJwtValidation() {
        RequestPostProcessor ip = from("10.0.13.1");
        for (int i = 0; i < IP_CEILING; i++) {
            assertUnauthorized(withBearer(forgedToken(UUID.randomUUID().toString()), ip));
        }

        assertRateLimited(withBearer(forgedToken(SecurityTestTokens.ANALYST_SUBJECT), ip));
        verify(jwtDecoder, times(IP_CEILING)).decode(anyString());
    }

    /** user-per-minute 200s, then two 429s, all as shop-a. */
    private void exhaustAndHitTwice(RequestPostProcessor ip) {
        for (int i = 0; i < USER; i++) {
            assertThat(mvc.get().uri(CLAIMS).with(SecurityTestTokens.partnerA()).with(ip).exchange()).hasStatusOk();
        }
        assertRateLimited(mvc.get().uri(CLAIMS).with(SecurityTestTokens.partnerA()).with(ip).exchange());
        assertRateLimited(mvc.get().uri(CLAIMS).with(SecurityTestTokens.partnerA()).with(ip).exchange());
    }

    private MvcTestResult patchClaim(RequestPostProcessor ip) {
        return mvc.patch().uri(CLAIMS + "/{id}", CLAIM_A1).with(SecurityTestTokens.admin()).with(ip)
                .contentType(MediaType.APPLICATION_JSON).content("{\"description\":\"Rate limit check\"}").exchange();
    }

    private MvcTestResult withBearer(String token, RequestPostProcessor ip) {
        return mvc.get().uri(CLAIMS).header(HttpHeaders.AUTHORIZATION, "Bearer " + token).with(ip).exchange();
    }

    private static void assertRateLimited(MvcTestResult result) {
        assertProblem(result, 429, "rate-limit-exceeded");
        assertThat(result.getResponse().getHeader(HttpHeaders.RETRY_AFTER))
                .isNotNull()
                .satisfies(retryAfter -> assertThat(Long.parseLong(retryAfter)).isGreaterThanOrEqualTo(1));
    }

    private static List<String> rateLimitedEvents(CapturedOutput output) {
        return output.getOut().lines().filter(line -> line.contains("event=rate_limited")).toList();
    }

    /** A well-formed JWT whose signature nobody issued. */
    private static String forgedToken(String subject) {
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        String header = encoder.encodeToString("{\"alg\":\"RS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String payload = encoder.encodeToString(("{\"sub\":\"" + subject + "\",\"aud\":\"partnerguard-api\"}")
                .getBytes(StandardCharsets.UTF_8));
        return header + "." + payload + "." + encoder.encodeToString("forged".getBytes(StandardCharsets.UTF_8));
    }

    private static RequestPostProcessor from(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }
}
