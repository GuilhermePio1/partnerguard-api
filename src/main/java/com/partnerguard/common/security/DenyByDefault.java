package com.partnerguard.common.security;

import org.jspecify.annotations.Nullable;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

import java.util.function.Supplier;

/**
 * {@code denyAll()} with a reason for the access_denied log: anything outside {@code /api/**}
 * and the public paths is refused (specs/authorization.md rule 8, ADR-0005).
 */
public final class DenyByDefault implements AuthorizationManager<RequestAuthorizationContext> {

    @Override
    public AuthorizationResult authorize(Supplier<? extends @Nullable Authentication> authentication,
                                         RequestAuthorizationContext context) {
        return new ReasonedDecision(false, "outside /api/** and the public paths");
    }
}
