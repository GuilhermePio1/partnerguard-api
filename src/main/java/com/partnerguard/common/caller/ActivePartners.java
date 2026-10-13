package com.partnerguard.common.caller;

import java.util.UUID;

/**
 * Whether a partner may use the API (specs/authorization.md rule 5, ADR-0021). Implemented by
 * the partner feature, so shared code doesn't depend on it (ADR-0011).
 */
public interface ActivePartners {

    /** True only if the partner exists and is ACTIVE. Read from the database on every call. */
    boolean isActive(UUID partnerId);
}
