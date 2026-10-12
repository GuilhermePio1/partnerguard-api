package com.partnerguard.common.caller;

import com.partnerguard.common.ownership.OwnershipScope;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * TEMPORARY, milestone 2 only: there is no authentication yet, so every caller gets the
 * internal (unrestricted) view and has no partner identity. Endpoints that need a partner
 * identity, such as GET /partners/me, therefore fail closed with 403.
 *
 * <p>Milestones 3 and 4 replace this bean with one built from the validated JWT
 * (roles and the partner_id claim). Delete this class then.
 */
@Component
class OpenAccessCallerContext implements CallerContext {

    @Override
    public OwnershipScope ownershipScope() {
        return OwnershipScope.unrestricted();
    }

    @Override
    public Optional<UUID> partnerId() {
        return Optional.empty();
    }
}
