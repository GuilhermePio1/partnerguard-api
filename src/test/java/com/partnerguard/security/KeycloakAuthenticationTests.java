package com.partnerguard.security;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import com.partnerguard.support.KeycloakIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static com.partnerguard.support.Fixtures.PARTNER_A;
import static com.partnerguard.support.ProblemAssertions.assertUnauthorized;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * specs/authentication.md, end to end: real Keycloak tokens, and forgeries built from them,
 * against the API's real JWT validation.
 */
class KeycloakAuthenticationTests extends KeycloakIntegrationTest {

    private static final String CLAIMS = "/api/v1/claims";

    @Test
    @DisplayName("AUTHN-07 a valid Keycloak token for shop-a is accepted")
    void acceptsValidToken() {
        assertThat(get(token("shop-a"))).hasStatusOk();
    }

    @Test
    @DisplayName("AUTHN-02 a token with the real claims and kid but signed by another key is a 401")
    void rejectsTokenSignedByAnotherKey() throws Exception {
        String real = token("shop-a");
        RSAKey attackerKey = new RSAKeyGenerator(2048).keyID(kid(real)).generate();

        SignedJWT forged = new SignedJWT(rs256Header(kid(real)), claimsOf(real));
        forged.sign(new RSASSASigner(attackerKey));

        assertUnauthorized(get(forged.serialize()));
    }

    @Test
    @DisplayName("AUTHN-03 a Keycloak-signed token with a different iss is a 401")
    void rejectsTokenWithDifferentIssuer() {
        // Keycloak derives iss from the URL the token was requested through, so asking via the
        // other loopback name yields a genuinely signed token whose issuer the API wasn't
        // configured with. Assumes the Docker host is local (true locally and on CI).
        String token = passwordGrant(baseUrl(otherLoopbackName()), CLIENT_ID, "shop-a");
        assertThat(claims(token).path("iss").asString()).isNotEqualTo(issuer());

        assertUnauthorized(get(token));
    }

    @Test
    @DisplayName("AUTHN-04 a Keycloak-signed token without partnerguard-api in aud is a 401")
    void rejectsTokenWithoutAudience() {
        ensureClientWithoutAudience("authn04-no-audience");
        String token = passwordGrant(baseUrl(KEYCLOAK.getHost()), "authn04-no-audience", "shop-a");
        assertThat(audiences(token)).doesNotContain("partnerguard-api");

        assertUnauthorized(get(token));
    }

    @Test
    @DisplayName("AUTHN-05 a token 31 s past exp is a 401, while 29 s past exp is within the 30 s skew")
    void rejectsExpiredToken() {
        String token = token("shop-a");
        Instant exp = Instant.ofEpochSecond(claims(token).path("exp").asLong());

        clock.pin(exp.plusSeconds(29));
        assertThat(get(token)).hasStatusOk();

        clock.pin(exp.plusSeconds(31));
        assertUnauthorized(get(token));
    }

    @Test
    @DisplayName("AUTHN-06 an unsigned token (alg: none) with the real claims is a 401")
    void rejectsUnsignedToken() throws Exception {
        String unsigned = new PlainJWT(claimsOf(token("shop-a"))).serialize();

        assertUnauthorized(get(unsigned));
    }

    @Test
    @DisplayName("AUTHN-06 an HS256 token keyed with the realm's public key (key confusion) is a 401")
    void rejectsHs256TokenKeyedWithPublicKey() throws Exception {
        String real = token("shop-a");
        RSAKey realmKey = (RSAKey) JWKSet.load(URI.create(issuer() + "/protocol/openid-connect/certs").toURL())
                .getKeyByKeyId(kid(real));

        SignedJWT forged = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.HS256).keyID(kid(real)).type(JOSEObjectType.JWT).build(),
                claimsOf(real));
        forged.sign(new MACSigner(realmKey.toRSAPublicKey().getEncoded()));

        assertUnauthorized(get(forged.serialize()));
    }

    @Test
    @DisplayName("AUTHN-12 the shop-a access token carries partner_id, the partnerguard-api audience and a 300 s lifespan")
    void realmIssuesExpectedClaims() {
        String token = token("shop-a");
        JsonNode claims = claims(token);

        assertThat(claims.path("partner_id").asString()).isEqualTo(PARTNER_A.toString());
        assertThat(audiences(token)).contains("partnerguard-api");
        assertThat(claims.path("exp").asLong() - claims.path("iat").asLong()).isEqualTo(300);
    }

    private MvcTestResult get(String token) {
        return mvc.get().uri(CLAIMS).header("Authorization", "Bearer " + token).exchange();
    }

    private static String kid(String token) {
        return header(token).path("kid").asString();
    }

    private static JWSHeader rs256Header(String kid) {
        return new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(kid).type(JOSEObjectType.JWT).build();
    }

    private static JWTClaimsSet claimsOf(String token) throws Exception {
        return SignedJWT.parse(token).getJWTClaimsSet();
    }

    /** {@code aud} as a list; Keycloak sends a string for one audience and an array for several. */
    private static List<String> audiences(String token) {
        JsonNode aud = claims(token).path("aud");
        List<String> audiences = new ArrayList<>();
        if (aud.isArray()) {
            aud.forEach(value -> audiences.add(value.asString()));
        } else if (aud.isString()) {
            audiences.add(aud.asString());
        }
        return audiences;
    }

    private static String otherLoopbackName() {
        return switch (KEYCLOAK.getHost()) {
            case "localhost" -> "127.0.0.1";
            case "127.0.0.1" -> "localhost";
            default -> throw new IllegalStateException(
                    "AUTHN-03 needs a local Docker host, but Testcontainers uses " + KEYCLOAK.getHost());
        };
    }
}
