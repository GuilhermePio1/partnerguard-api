package com.partnerguard.common.ratelimit;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** The limits from specs/rate-limiting.md rule 8, per minute. */
@ConfigurationProperties("partnerguard.rate-limit")
@Validated
public record RateLimitProperties(
        @Positive int ipCeilingPerMinute,
        @Positive int userPerMinute,
        @Positive int userWritesPerMinute,
        @Positive int anonymousPerMinute) {
}
