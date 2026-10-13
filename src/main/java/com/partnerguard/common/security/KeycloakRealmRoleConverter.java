package com.partnerguard.common.security;

import com.partnerguard.common.caller.Role;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Keycloak realm roles ({@code realm_access.roles}) to authorities (specs/authorization.md
 * rule 1). Only the exact names ADMIN, ANALYST and PARTNER are mapped; every other realm role,
 * client roles and scopes grant nothing.
 */
public class KeycloakRealmRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        if (!(jwt.getClaims().get("realm_access") instanceof Map<?, ?> realmAccess)
                || !(realmAccess.get("roles") instanceof List<?> roles)) {
            return List.of();
        }
        return Arrays.stream(Role.values())
                .filter(role -> roles.contains(role.name()))
                .<GrantedAuthority>map(role -> new SimpleGrantedAuthority(role.authority()))
                .toList();
    }
}
