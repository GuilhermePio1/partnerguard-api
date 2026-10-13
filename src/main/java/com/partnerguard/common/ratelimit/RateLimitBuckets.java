package com.partnerguard.common.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.TimeMeter;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * The token buckets of every limit, in memory (specs/rate-limiting.md rules 3, 5 and 7,
 * ADR-0006). Buckets refill greedily over the minute and live in a bounded cache, so a flood
 * of distinct keys can't exhaust memory. Time comes from the application {@link Clock}, so
 * tests refill buckets by moving a fake clock instead of sleeping.
 */
@Component
public class RateLimitBuckets {

    private static final int MAX_KEYS = 100_000;
    private static final Duration IDLE_EXPIRY = Duration.ofMinutes(10);
    private static final Duration PERIOD = Duration.ofMinutes(1);

    private final RateLimitProperties properties;
    private final TimeMeter time;
    private final Cache<BucketKey, Bucket> buckets;

    public RateLimitBuckets(RateLimitProperties properties, Clock clock) {
        this.properties = properties;
        this.time = new ClockTimeMeter(clock);
        this.buckets = Caffeine.newBuilder()
                .maximumSize(MAX_KEYS)
                .expireAfterAccess(IDLE_EXPIRY)
                .ticker(() -> TimeUnit.MILLISECONDS.toNanos(clock.millis()))
                .build();
    }

    /** Takes one token if there is one. A refusal takes nothing (rule 4). */
    RateLimitDecision tryConsume(RateLimit limit, String key) {
        ConsumptionProbe probe = bucket(limit, key).tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            return new RateLimitDecision(true, 0);
        }
        return new RateLimitDecision(false, retryAfterSeconds(probe.getNanosToWaitForRefill()));
    }

    /** Gives back a token taken by a request that another bucket of the same layer then refused (rule 4). */
    void refund(RateLimit limit, String key) {
        bucket(limit, key).addTokens(1);
    }

    /** For RL-09: the number of buckets held, after pending evictions. */
    long cachedBuckets() {
        buckets.cleanUp();
        return buckets.estimatedSize();
    }

    private Bucket bucket(RateLimit limit, String key) {
        return buckets.get(new BucketKey(limit, key), bucketKey -> newBucket(perMinute(bucketKey.limit())));
    }

    private int perMinute(RateLimit limit) {
        return switch (limit) {
            case IP_CEILING -> properties.ipCeilingPerMinute();
            case USER -> properties.userPerMinute();
            case USER_WRITES -> properties.userWritesPerMinute();
            case ANONYMOUS -> properties.anonymousPerMinute();
        };
    }

    private Bucket newBucket(int tokensPerMinute) {
        return Bucket.builder()
                .addLimit(bandwidth -> bandwidth.capacity(tokensPerMinute).refillGreedy(tokensPerMinute, PERIOD))
                .withCustomTimePrecision(time)
                .build();
    }

    /** Whole seconds, rounded up, at least 1 (rule 5). */
    private static long retryAfterSeconds(long nanosToWait) {
        return Math.max(1, (nanosToWait + TimeUnit.SECONDS.toNanos(1) - 1) / TimeUnit.SECONDS.toNanos(1));
    }

    private record BucketKey(RateLimit limit, String key) {
    }

    private record ClockTimeMeter(Clock clock) implements TimeMeter {

        @Override
        public long currentTimeNanos() {
            return TimeUnit.MILLISECONDS.toNanos(clock.millis());
        }

        @Override
        public boolean isWallClockBased() {
            return true;
        }
    }
}
