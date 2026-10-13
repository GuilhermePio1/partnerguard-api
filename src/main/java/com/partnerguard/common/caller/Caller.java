package com.partnerguard.common.caller;

import org.jspecify.annotations.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * The authenticated caller as the validated token describes it: subject, the API roles it
 * holds and its partner id (ADR-0003). Nothing here comes from the request itself.
 */
public record Caller(String subject, Set<Role> roles, Optional<UUID> partnerId) {

    /** Canonical form only: {@link UUID#fromString} also accepts shapes like {@code 1-1-1-1-1}. */
    private static final Pattern UUID_FORMAT =
            Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    public Caller {
        roles = Set.copyOf(roles);
    }

    public static Caller from(Authentication authentication) {
        Set<Role> roles = EnumSet.noneOf(Role.class);
        for (GrantedAuthority granted : authentication.getAuthorities()) {
            Arrays.stream(Role.values())
                    .filter(role -> role.authority().equals(granted.getAuthority()))
                    .forEach(roles::add);
        }
        Optional<UUID> partnerId = authentication instanceof JwtAuthenticationToken jwt
                ? parsePartnerId(jwt.getToken().getClaimAsString("partner_id"))
                : Optional.empty();
        return new Caller(authentication.getName(), roles, partnerId);
    }

    public boolean isPartner() {
        return roles.contains(Role.PARTNER);
    }

    public boolean isInternal() {
        return roles.contains(Role.ADMIN) || roles.contains(Role.ANALYST);
    }

    /**
     * specs/authorization.md rule 4: a PARTNER without a valid partner_id, or one that also
     * holds an internal role, is a misconfigured account and is refused everywhere.
     */
    public boolean isMisconfiguredPartner() {
        return isPartner() && (isInternal() || partnerId.isEmpty());
    }

    /** The strongest role held (ADMIN's permissions include ANALYST's), if any. */
    public Optional<Role> strongestRole() {
        return Arrays.stream(Role.values()).filter(roles::contains).findFirst();
    }

    private static Optional<UUID> parsePartnerId(@Nullable String claim) {
        return claim != null && UUID_FORMAT.matcher(claim).matches()
                ? Optional.of(UUID.fromString(claim))
                : Optional.empty();
    }
}
