package com.partnerguard.common.caller;

/**
 * The API's roles: the Keycloak realm roles with these exact names (ADR-0003). Any other
 * realm role is ignored. Declared from strongest to weakest.
 */
public enum Role {

    ADMIN,
    ANALYST,
    PARTNER;

    /** The Spring Security authority for this role. */
    public String authority() {
        return "ROLE_" + name();
    }
}
