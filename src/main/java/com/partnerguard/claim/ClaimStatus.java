package com.partnerguard.claim;

import java.util.Set;

/** SUBMITTED → UNDER_REVIEW → APPROVED | REJECTED. APPROVED and REJECTED are final (ADR-0009). */
enum ClaimStatus {
    SUBMITTED,
    UNDER_REVIEW,
    APPROVED,
    REJECTED;

    boolean canTransitionTo(ClaimStatus target) {
        return switch (this) {
            case SUBMITTED -> target == UNDER_REVIEW;
            case UNDER_REVIEW -> Set.of(APPROVED, REJECTED).contains(target);
            case APPROVED, REJECTED -> false;
        };
    }
}
