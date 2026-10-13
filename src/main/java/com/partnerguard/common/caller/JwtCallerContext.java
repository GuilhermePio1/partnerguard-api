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

    @Override
    public String subject() {
        wellConfiguredCaller();
        String subject = authenticated().getName();
        if (subject == null || subject.isBlank()) {
            throw new AccessDeniedException("Token without a subject");
        }
        return subject;
    }

    private static Caller wellConfiguredCaller() {
        Caller caller = Caller.from(authenticated());
        if (caller.isMisconfiguredPartner()) {
            throw new AccessDeniedException("Misconfigured partner account");
        }
        return caller;
    }

    private static Authentication authenticated() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new AccessDeniedException("Not authenticated");
        }
        return authentication;
    }
}
