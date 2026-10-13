package com.partnerguard.common.ratelimit;

import com.partnerguard.support.MutableClock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** specs/rate-limiting.md rules 3, 5 and 7, on the bucket store alone. */
class RateLimitBucketsTests {

    private static final Instant START = Instant.parse("2026-10-13T12:00:00Z");

    private final MutableClock clock = new MutableClock();
    private final RateLimitBuckets buckets = new RateLimitBuckets(new RateLimitProperties(120, 3, 2, 60), clock);

    @Test
    @DisplayName("RL-07 after a 429, waiting Retry-After seconds (fake clock) lets the next call through, and not a second earlier")
    void retryAfterIsEnough() {
        clock.pin(START);
        for (int i = 0; i < 3; i++) {
            assertThat(buckets.tryConsume(RateLimit.USER, "shop-a").allowed()).isTrue();
        }

        RateLimitDecision rejected = buckets.tryConsume(RateLimit.USER, "shop-a");
        assertThat(rejected.allowed()).isFalse();
        // Greedy refill of 3 per minute: one token every 20 seconds.
        assertThat(rejected.retryAfterSeconds()).isEqualTo(20);

        clock.pin(START.plusSeconds(rejected.retryAfterSeconds() - 1));
        assertThat(buckets.tryConsume(RateLimit.USER, "shop-a").allowed()).isFalse();

        clock.pin(START.plusSeconds(rejected.retryAfterSeconds()));
        assertThat(buckets.tryConsume(RateLimit.USER, "shop-a").allowed()).isTrue();
    }

    @Test
    @DisplayName("RL-07 Retry-After is rounded up to whole seconds and is at least 1")
    void retryAfterIsWholeSecondsAndAtLeastOne() {
        clock.pin(START);
        for (int i = 0; i < 3; i++) {
            buckets.tryConsume(RateLimit.USER, "shop-a");
        }

        clock.pin(START.plus(Duration.ofMillis(10_500)));
        assertThat(buckets.tryConsume(RateLimit.USER, "shop-a").retryAfterSeconds()).isEqualTo(10);

        // One millisecond left still means "retry after 1 second", never 0.
        clock.pin(START.plus(Duration.ofMillis(19_999)));
        assertThat(buckets.tryConsume(RateLimit.USER, "shop-a").retryAfterSeconds()).isEqualTo(1);
    }

    @Test
    @DisplayName("RL-07 a refused request takes no token: the refusals while waiting don't push the wait further")
    void refusalsTakeNoToken() {
        clock.pin(START);
        for (int i = 0; i < 3; i++) {
            buckets.tryConsume(RateLimit.USER, "shop-a");
        }
        for (int i = 0; i < 10; i++) {
            assertThat(buckets.tryConsume(RateLimit.USER, "shop-a").allowed()).isFalse();
        }

        clock.pin(START.plusSeconds(20));
        assertThat(buckets.tryConsume(RateLimit.USER, "shop-a").allowed()).isTrue();
    }

    @Test
    @DisplayName("RL-09 200,000 distinct keys leave at most 100,000 buckets in the cache")
    void cacheIsBounded() {
        for (int i = 0; i < 200_000; i++) {
            buckets.tryConsume(RateLimit.IP_CEILING, "key-" + i);
        }

        assertThat(buckets.cachedBuckets()).isLessThanOrEqualTo(100_000);
    }
}
