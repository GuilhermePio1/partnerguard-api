package com.partnerguard.support;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * A clock that follows the system clock until a test pins it. Lets tests move time past a
 * token's expiry (or a rate-limit refill) without sleeping.
 */
public final class MutableClock extends Clock {

    private volatile Instant pinned;

    /** Freezes the clock at {@code instant}. */
    public void pin(Instant instant) {
        this.pinned = instant;
    }

    /** Back to the system clock. */
    public void reset() {
        this.pinned = null;
    }

    @Override
    public Instant instant() {
        Instant instant = pinned;
        return instant != null ? instant : Instant.now();
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        throw new UnsupportedOperationException("MutableClock is always UTC");
    }
}
