package com.partnerguard.security;

import com.partnerguard.support.PostgresIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static com.partnerguard.support.Fixtures.PARTNER_A;
import static com.partnerguard.support.ProblemAssertions.assertProblem;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * specs/authorization.md rule 1, through the application's real claim-to-authority mapping:
 * the stub decoder hands over Keycloak-shaped claims and nothing else is faked. The matrix
 * tests build authorities directly, so this is the one place the mapping itself is proven.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RoleMappingTests extends PostgresIntegrationTest {

    @MockitoBean
    JwtDecoder jwtDecoder;

    @Autowired
    MockMvcTester mvc;

    @ParameterizedTest(name = "ADR-0003 realm role {0} grants {1}")
    @CsvSource({
            "ADMIN,   /api/v1/partners",
            "ANALYST, /api/v1/policies/POL-2026-000001",
            "PARTNER, /api/v1/partners/me"})
    @DisplayName("ADR-0003 the realm roles ADMIN, ANALYST and PARTNER become the API's roles")
    void mappedRealmRoles(String role, String path) {
        // Keycloak also puts its default roles in realm_access; they must not get in the way.
        Jwt token = token(Map.of("realm_access", Map.of("roles", List.of(role, "offline_access", "uma_authorization")),
                "partner_id", PARTNER_A.toString()));

        assertThat(call(path, token)).hasStatusOk();
    }

    @ParameterizedTest(name = "AUTHZ-04 {0} grants nothing")
    @CsvSource(delimiter = '|', value = {
            "an unmapped realm role (SUPERUSER) | realm_access | SUPERUSER",
            "a role in the wrong case (admin)   | realm_access | admin",
            "ADMIN as a client role             | resource_access | ADMIN",
            "no roles at all                    | none | ''"})
    @DisplayName("AUTHZ-04 only the exact realm roles ADMIN, ANALYST and PARTNER are mapped")
    void unmappedRolesGrantNothing(String description, String claim, String role) {
        Map<String, Object> claims = switch (claim) {
            case "realm_access" -> Map.of("realm_access", Map.of("roles", List.of(role)));
            case "resource_access" -> Map.of("resource_access",
                    Map.of("partnerguard-cli", Map.of("roles", List.of(role))));
            default -> Map.of("scope", "profile email");
        };

        assertProblem(call("/api/v1/partners", token(claims)), 403, "forbidden");
    }

    private MvcTestResult call(String path, Jwt token) {
        when(jwtDecoder.decode(token.getTokenValue())).thenReturn(token);
        return mvc.get().uri(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + token.getTokenValue()).exchange();
    }

    private static Jwt token(Map<String, Object> claims) {
        Instant now = Instant.now();
        return Jwt.withTokenValue("token-" + claims.hashCode())
                .header("alg", "RS256")
                .subject("role-mapping-subject")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .claims(all -> all.putAll(claims))
                .build();
    }
}
