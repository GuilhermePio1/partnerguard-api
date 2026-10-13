package com.partnerguard.common.caller;

import com.partnerguard.common.ownership.OwnershipScope;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/** The caller as the validated JWT of the current request describes it (ADR-0003). */
@Component
class JwtCallerContext implements CallerContext {

    @Override
    public OwnershipScope ownershipScope() {
        Caller caller = wellConfiguredCaller();
        if (caller.isPartner()) {
            return OwnershipScope.partner(caller.partnerId().orElseThrow());
        }
        if (caller.isInternal()) {
            return OwnershipScope.unrestricted();
        }
        throw new AccessDeniedException("No role grants a data scope");
    }

    @Override
    public Role role() {
        return wellConfiguredCaller().strongestRole()
                .orElseThrow(() -> new AccessDeniedException("No mapped role"));
    }

    @Override
    public Optional<UUID> partnerId() {
        Caller caller = wellConfiguredCaller();
        return caller.isPartner() ? caller.partnerId() : Optional.empty();
    }

    private static Caller wellConfiguredCaller() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new AccessDeniedException("Not authenticated");
        }
        Caller caller = Caller.from(authentication);
        if (caller.isMisconfiguredPartner()) {
            throw new AccessDeniedException("Misconfigured partner account");
        }
        return caller;
    }
}
