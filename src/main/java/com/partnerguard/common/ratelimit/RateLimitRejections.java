package com.partnerguard.common.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.partnerguard.common.error.ProblemResponseWriter;
import com.partnerguard.common.error.ProblemType;
import com.partnerguard.common.security.LogValues;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Answers a request that a bucket refused: {@code 429} Problem Details with
 * {@code Retry-After} (specs/rate-limiting.md rule 5), and an {@code event=rate_limited} log
 * line at most once per key per minute (rule 9), so a flood can't flood the log as well.
 */
@Component
public class RateLimitRejections {

    private static final Logger log = LoggerFactory.getLogger(RateLimitRejections.class);
    private static final Duration LOG_INTERVAL = Duration.ofMinutes(1);
    private static final int MAX_LOGGED_KEYS = 100_000;

    private final ProblemResponseWriter problems;
    /** Keys logged within the last minute. Bounded like the buckets themselves. */
    private final Cache<String, Boolean> recentlyLogged;

    public RateLimitRejections(ProblemResponseWriter problems, Clock clock) {
        this.problems = problems;
        this.recentlyLogged = Caffeine.newBuilder()
                .maximumSize(MAX_LOGGED_KEYS)
                .expireAfterWrite(LOG_INTERVAL)
                .ticker(() -> TimeUnit.MILLISECONDS.toNanos(clock.millis()))
                .build();
    }

    void reject(RateLimit limit, String key, RateLimitDecision decision,
                HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (recentlyLogged.asMap().putIfAbsent(limit.keyType() + ":" + key, Boolean.TRUE) == null) {
            log.warn("event=rate_limited limit={} key_type={} key={} method={} path={}",
                    limit.name().toLowerCase(Locale.ROOT), limit.keyType(), LogValues.loggable(key),
                    request.getMethod(), LogValues.loggable(request.getRequestURI()));
        }

        response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(decision.retryAfterSeconds()));
        problems.write(response, ProblemType.RATE_LIMIT_EXCEEDED, request.getRequestURI());
    }
}
