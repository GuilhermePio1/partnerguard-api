package com.partnerguard.common.ratelimit;

import com.partnerguard.common.error.ProblemResponseWriter;
import com.partnerguard.support.MutableClock;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** specs/rate-limiting.md rule 6: the IP key is the socket address. */
class PreAuthRateLimitFilterTests {

    private static final int IP_CEILING = 2;

    private final MutableClock clock = new MutableClock();
    private final PreAuthRateLimitFilter filter = new PreAuthRateLimitFilter(
            new RateLimitBuckets(new RateLimitProperties(IP_CEILING, 60, 20, 60), clock),
            new RateLimitRejections(mock(ProblemResponseWriter.class), clock));

    @Test
    @DisplayName("RL-08 with no trusted proxy, X-Forwarded-For is ignored: the bucket key is the socket address")
    void forwardedForIsIgnored() throws ServletException, IOException {
        for (int i = 0; i < IP_CEILING; i++) {
            MockFilterChain chain = new MockFilterChain();
            filter.doFilter(request("1.2.3." + i), new MockHttpServletResponse(), chain);
            assertThat(chain.getRequest()).as("request %d passes", i + 1).isNotNull();
        }

        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request("9.9.9.9"), response, chain);

        // A fresh X-Forwarded-For value didn't buy a fresh bucket.
        assertThat(chain.getRequest()).isNull();
        assertThat(response.getHeader(HttpHeaders.RETRY_AFTER)).isNotNull();
    }

    private static MockHttpServletRequest request(String forwardedFor) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/claims");
        request.setRemoteAddr("192.0.2.10");
        request.addHeader("X-Forwarded-For", forwardedFor);
        return request;
    }
}
