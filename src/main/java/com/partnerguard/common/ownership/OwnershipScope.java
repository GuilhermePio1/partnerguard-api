package com.partnerguard.common.ownership;

import java.util.Objects;
import java.util.UUID;

/**
 * Which rows a caller may see (ADR-0004). Services pick a partner-scoped query for
 * {@link PartnerOnly} and an unscoped one for {@link Unrestricted}; a resource outside
 * the scope is reported exactly like a missing one.
 */
public sealed interface OwnershipScope {

    static OwnershipScope unrestricted() {
        return Unrestricted.INSTANCE;
    }

    static OwnershipScope partner(UUID partnerId) {
        return new PartnerOnly(partnerId);
    }

    /** Internal staff: every partner's data. */
    enum Unrestricted implements OwnershipScope {
        INSTANCE
    }

    /** A partner user: only rows whose partner_id is this partner. */
    record PartnerOnly(UUID partnerId) implements OwnershipScope {
        public PartnerOnly {
            Objects.requireNonNull(partnerId, "partnerId");
        }
    }
}
