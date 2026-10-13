package com.partnerguard.support;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * MockMvc tests that need rate limits small enough to reach: the rate-limit tests themselves,
 * and the tests that need a {@code 429} next to other responses (error catalog, security
 * events). Subclasses share one Spring context, so keep this class's configuration the only
 * configuration they declare.
 *
 * <ul>
 *   <li>Valid callers come from {@link SecurityTestTokens}. Every token sent in an
 *       {@code Authorization} header is rejected by the stub {@link JwtDecoder}, as a real
 *       decoder rejects a bad signature.</li>
 *   <li>Each test starts two minutes after the previous one, on a clock pinned for the whole
 *       context, so every bucket is full again and the once-a-minute log filter is clear. The
 *       counter is static here, not per subclass, so two classes never pin the same instant.</li>
 *   <li>Tests pick their own client IPs with {@link #from(String)}, so IP buckets don't leak
 *       between tests.</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestClockConfig.class)
@ExtendWith(OutputCaptureExtension.class)
@Transactional
@TestPropertySource(properties = {
        "partnerguard.rate-limit.ip-ceiling-per-minute=" + SmallRateLimitsIntegrationTest.IP_CEILING,
        "partnerguard.rate-limit.user-per-minute=" + SmallRateLimitsIntegrationTest.USER,
        "partnerguard.rate-limit.user-writes-per-minute=" + SmallRateLimitsIntegrationTest.USER_WRITES,
        "partnerguard.rate-limit.anonymous-per-minute=" + SmallRateLimitsIntegrationTest.ANONYMOUS
})
public abstract class SmallRateLimitsIntegrationTest extends PostgresIntegrationTest {

    protected static final int IP_CEILING = 10;
    protected static final int USER = 3;
    protected static final int USER_WRITES = 2;
    protected static final int ANONYMOUS = 3;

    /** Test n of the whole context runs on a clock pinned 2n minutes after START. */
    private static final Instant START = Instant.now();
    private static final AtomicInteger TESTS_RUN = new AtomicInteger();

    @Autowired
    protected MockMvcTester mvc;

    @Autowired
    protected MutableClock clock;

    @MockitoBean
    protected JwtDecoder jwtDecoder;

    @BeforeEach
    void freshBucketsAndRejectingDecoder() {
        clock.pin(START.plus(Duration.ofMinutes(2L * TESTS_RUN.incrementAndGet())));
        when(jwtDecoder.decode(anyString())).thenThrow(new BadJwtException("Signed JWT rejected: Invalid signature"));
    }

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    /** Sends the request from {@code ip}. */
    protected static RequestPostProcessor from(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }
}
