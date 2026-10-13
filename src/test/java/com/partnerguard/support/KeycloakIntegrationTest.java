package com.partnerguard.support;

import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Base class for end-to-end authentication tests (E2E-auth in specs/README.md): the API
 * validates real tokens issued by a Keycloak container that imports
 * {@code keycloak/realm-export.json}, the same realm as docker-compose.yml.
 *
 * <p>One container is shared by all test classes. Only the issuer comes from here; the
 * audience and every other check come from the application's own configuration.
 *
 * <p>The API's clock is a {@link MutableClock}, so tests can move past a token's expiry
 * without sleeping. It is reset after each test.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(KeycloakIntegrationTest.ClockConfig.class)
public abstract class KeycloakIntegrationTest extends PostgresIntegrationTest {

    /** Same image as docker-compose.yml. Keep the two in sync. */
    private static final String KEYCLOAK_IMAGE = "quay.io/keycloak/keycloak:26.8.1";
    private static final int KEYCLOAK_PORT = 8080;
    protected static final String REALM = "partnerguard";
    protected static final String CLIENT_ID = "partnerguard-cli";
    /** Admin of the master realm, used only to set up test clients. */
    private static final String KEYCLOAK_ADMIN = "test-admin";
    private static final String KEYCLOAK_ADMIN_PASSWORD = "test-admin-password";

    protected static final GenericContainer<?> KEYCLOAK = new GenericContainer<>(KEYCLOAK_IMAGE)
            .withCommand("start-dev", "--import-realm")
            .withEnv("KC_BOOTSTRAP_ADMIN_USERNAME", KEYCLOAK_ADMIN)
            .withEnv("KC_BOOTSTRAP_ADMIN_PASSWORD", KEYCLOAK_ADMIN_PASSWORD)
            .withCopyFileToContainer(
                    MountableFile.forHostPath("keycloak/realm-export.json"),
                    "/opt/keycloak/data/import/realm-export.json")
            .withExposedPorts(KEYCLOAK_PORT)
            .waitingFor(Wait.forHttp("/realms/" + REALM + "/.well-known/openid-configuration")
                    .forPort(KEYCLOAK_PORT)
                    .forStatusCode(200)
                    .withStartupTimeout(Duration.ofMinutes(4)));

    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final JsonMapper JSON = JsonMapper.builder().build();

    static {
        KEYCLOAK.start();
    }

    @DynamicPropertySource
    static void keycloakProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", KeycloakIntegrationTest::issuer);
    }

    @Autowired
    protected MockMvcTester mvc;

    @Autowired
    protected MutableClock clock;

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    /** Keycloak's base URL as reached through {@code host}. Keycloak derives the issuer from it. */
    protected static String baseUrl(String host) {
        return "http://" + host + ":" + KEYCLOAK.getMappedPort(KEYCLOAK_PORT);
    }

    /** The issuer the API is configured with. */
    protected static String issuer() {
        return baseUrl(KEYCLOAK.getHost()) + "/realms/" + REALM;
    }

    /** An access token for a fixture user (password = username, specs/fixtures.md). */
    protected static String token(String username) {
        return passwordGrant(baseUrl(KEYCLOAK.getHost()), CLIENT_ID, username);
    }

    /** Password grant against the realm, through {@code baseUrl}, for {@code clientId}. */
    protected static String passwordGrant(String baseUrl, String clientId, String username) {
        JsonNode response = postForm(baseUrl + "/realms/" + REALM + "/protocol/openid-connect/token", Map.of(
                "grant_type", "password",
                "client_id", clientId,
                "username", username,
                "password", username));
        return response.path("access_token").asString();
    }

    /**
     * Creates a public client with direct access grants and no protocol mappers, so its
     * tokens lack the {@code partnerguard-api} audience. Idempotent.
     */
    protected static void ensureClientWithoutAudience(String clientId) {
        String adminToken = postForm(baseUrl(KEYCLOAK.getHost()) + "/realms/master/protocol/openid-connect/token",
                Map.of("grant_type", "password",
                        "client_id", "admin-cli",
                        "username", KEYCLOAK_ADMIN,
                        "password", KEYCLOAK_ADMIN_PASSWORD))
                .path("access_token").asString();
        String client = """
                {"clientId":"%s","enabled":true,"publicClient":true,"standardFlowEnabled":false,\
                "directAccessGrantsEnabled":true}""".formatted(clientId);
        HttpResponse<String> response = send(HttpRequest.newBuilder(
                        URI.create(baseUrl(KEYCLOAK.getHost()) + "/admin/realms/" + REALM + "/clients"))
                .header("Authorization", "Bearer " + adminToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(client))
                .build());
        if (response.statusCode() != 201 && response.statusCode() != 409) {
            throw new IllegalStateException("Creating client " + clientId + " failed: " + response.statusCode());
        }
    }

    /** The decoded JOSE header of a compact JWT. */
    protected static JsonNode header(String token) {
        return decodePart(token, 0);
    }

    /** The decoded claims of a compact JWT. */
    protected static JsonNode claims(String token) {
        return decodePart(token, 1);
    }

    private static JsonNode decodePart(String token, int index) {
        return JSON.readTree(Base64.getUrlDecoder().decode(token.split("\\.")[index]));
    }

    private static JsonNode postForm(String url, Map<String, String> form) {
        String body = form.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "="
                        + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
        HttpResponse<String> response = send(HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("POST " + url + " failed: " + response.statusCode() + " " + response.body());
        }
        return JSON.readTree(response.body());
    }

    private static HttpResponse<String> send(HttpRequest request) {
        try {
            return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ClockConfig {

        /** Replaces the application's clock, so JWT timestamp checks follow the test. */
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock();
        }
    }
}
