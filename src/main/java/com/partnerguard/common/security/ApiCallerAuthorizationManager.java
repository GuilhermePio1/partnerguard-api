package com.partnerguard.common.security;

import com.partnerguard.common.caller.ActivePartners;
import com.partnerguard.common.caller.Caller;
import org.jspecify.annotations.Nullable;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * The gate in front of every {@code /api/**} request (specs/authorization.md rules 4 and 5,
 * ADR-0021). It refuses anonymous callers, misconfigured partner accounts (no valid
 * partner_id, or an internal role as well) and partners that are suspended or don't exist.
 * Which roles may call which operation is decided afterwards, per method, by @PreAuthorize.
 */
@Component
public class ApiCallerAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {

    private final ActivePartners activePartners;

    ApiCallerAuthorizationManager(ActivePartners activePartners) {
        this.activePartners = activePartners;
    }

    @Override
    public AuthorizationResult authorize(Supplier<? extends @Nullable Authentication> authentication,
                                         RequestAuthorizationContext context) {
        Authentication current = authentication.get();
        if (current == null || !current.isAuthenticated() || current instanceof AnonymousAuthenticationToken) {
            return new ReasonedDecision(false, "not authenticated");
        }
        Caller caller = Caller.from(current);
        if (!caller.isPartner()) {
            return new ReasonedDecision(true, "not a partner; roles are checked per operation");
        }
        if (caller.isMisconfiguredPartner()) {
            return new ReasonedDecision(false, caller.isInternal()
                    ? "PARTNER token also holds an internal role"
                    : "PARTNER token without a valid partner_id");
        }
        // Read on every request, never cached, so a suspension also stops tokens already issued.
        boolean active = activePartners.isActive(caller.partnerId().orElseThrow());
        return new ReasonedDecision(active, active ? "active partner" : "partner is suspended or doesn't exist");
    }
}
