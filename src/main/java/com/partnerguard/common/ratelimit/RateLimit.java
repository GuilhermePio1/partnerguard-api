package com.partnerguard.common.ratelimit;

/** The four buckets of specs/rate-limiting.md rules 1 and 2, and what each is keyed on. */
enum RateLimit {

    IP_CEILING("ip"),
    USER("sub"),
    USER_WRITES("sub"),
    ANONYMOUS("ip");

    private final String keyType;

    RateLimit(String keyType) {
        this.keyType = keyType;
    }

    String keyType() {
        return keyType;
    }
}
