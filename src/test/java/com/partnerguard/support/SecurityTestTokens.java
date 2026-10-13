package com.partnerguard.support;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;

import java.util.List;
import java.util.Map;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

/**
 * Callers for MockMvc tests, as already-validated JWTs (the decoder is bypassed). Token
 * validation itself is tested end to end in {@code KeycloakAuthenticationTests}.
 */
public final class SecurityTestTokens {

    private SecurityTestTokens() {
    }

    public static JwtRequestPostProcessor admin() {
        return withRealmRole("admin-subject", "ADMIN");
    }

    private static JwtRequestPostProcessor withRealmRole(String subject, String role) {
        return jwt()
                .jwt(token -> token.subject(subject).claim("realm_access", Map.of("roles", List.of(role))))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
