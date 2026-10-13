package com.partnerguard.common.ownership;

import com.partnerguard.common.error.ResourceNotFoundException;
import com.partnerguard.common.security.LogValues;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

/**
 * The 404 for a lookup that found nothing (ADR-0004). Under a partner scope it is logged as
 * {@code event=ownership_denied} (specs/authorization.md rule 10). A scoped query can't tell
 * "another partner's" from "missing" without a second query, which would defeat its purpose,
 * so every partner-scoped miss is logged.
 */
public final class OwnershipDenials {

    private static final Logger log = LoggerFactory.getLogger(OwnershipDenials.class);

    private OwnershipDenials() {
    }

    public static ResourceNotFoundException notFound(OwnershipScope scope, String resourceType, UUID resourceId) {
        if (scope instanceof OwnershipScope.PartnerOnly(var partnerId)) {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            log.warn("event=ownership_denied sub={} partnerId={} resourceType={} resourceId={}",
                    LogValues.loggable(authentication == null ? null : authentication.getName()), partnerId,
                    resourceType, resourceId);
        }
        return new ResourceNotFoundException();
    }
}
