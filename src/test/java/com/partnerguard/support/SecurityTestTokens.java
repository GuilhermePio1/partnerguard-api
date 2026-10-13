package com.partnerguard.support;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

/**
 * Callers for MockMvc tests, as already-validated JWTs (the decoder is bypassed). Each one
 * carries the same roles both as {@code realm_access.roles} and as authorities, so tests hold
 * whichever the code reads. How Keycloak claims become authorities is tested on its own in
 * {@code RoleMappingTests}; token validation in {@code KeycloakAuthenticationTests}.
 */
public final class SecurityTestTokens {

    public static final String ADMIN_SUBJECT = "admin-subject";
    public static final String ANALYST_SUBJECT = "analyst-subject";
    public static final String PARTNER_SUBJECT = "partner-subject";

    private SecurityTestTokens() {
    }

    public static JwtRequestPostProcessor admin() {
        return caller(ADMIN_SUBJECT, token -> { }, "ADMIN");
    }

    public static JwtRequestPostProcessor analyst() {
        return caller(ANALYST_SUBJECT, token -> { }, "ANALYST");
    }

    /** shop-a (specs/fixtures.md). */
    public static JwtRequestPostProcessor partnerA() {
        return partner(Fixtures.PARTNER_A);
    }

    public static JwtRequestPostProcessor partner(UUID partnerId) {
        return caller(PARTNER_SUBJECT, token -> token.claim("partner_id", partnerId.toString()), "PARTNER");
    }

    /** AUTHZ-05: a PARTNER whose token has no partner_id claim. */
    public static JwtRequestPostProcessor partnerWithoutPartnerId() {
        return caller(PARTNER_SUBJECT, token -> { }, "PARTNER");
    }

    /** AUTHZ-06: a PARTNER whose partner_id isn't a UUID. */
    public static JwtRequestPostProcessor partnerWithMalformedPartnerId() {
        return caller(PARTNER_SUBJECT, token -> token.claim("partner_id", "abc"), "PARTNER");
    }

    /** AUTHZ-19: Partner A's user who was also given an internal role. */
    public static JwtRequestPostProcessor partnerAWithInternalRole(String internalRole) {
        return caller(PARTNER_SUBJECT,
                token -> token.claim("partner_id", Fixtures.PARTNER_A.toString()), "PARTNER", internalRole);
    }

    /** AUTHZ-04: only a realm role the API doesn't map. */
    public static JwtRequestPostProcessor unmappedRole() {
        return caller("superuser-subject", token -> { }, "SUPERUSER");
    }

    private static JwtRequestPostProcessor caller(String subject, Consumer<Jwt.Builder> claims, String... roles) {
        List<GrantedAuthority> authorities = Arrays.stream(roles)
                .<GrantedAuthority>map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
        return jwt()
                .jwt(token -> {
                    token.subject(subject).claim("realm_access", Map.of("roles", List.of(roles)));
                    claims.accept(token);
                })
                .authorities(authorities);
    }
}
