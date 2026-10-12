package com.partnerguard.claim;

import java.math.BigDecimal;

/** The fields a PATCH asks to change; {@code null} means "leave unchanged". */
record ClaimChanges(ClaimStatus status, String description, BigDecimal amount) {
}
