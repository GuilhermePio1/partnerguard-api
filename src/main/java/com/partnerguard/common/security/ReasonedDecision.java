package com.partnerguard.common.security;

import org.springframework.security.authorization.AuthorizationDecision;

/** A decision that carries why it was taken, for the access_denied log (never for the client). */
final class ReasonedDecision extends AuthorizationDecision {

    private final String reason;

    ReasonedDecision(boolean granted, String reason) {
        super(granted);
        this.reason = reason;
    }

    String reason() {
        return reason;
    }
}
