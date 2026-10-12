package com.partnerguard.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

/**
 * Base class for integration tests that need PostgreSQL.
 *
 * <p>One container is shared by all test classes. It runs the same init script as
 * docker-compose.yml, so tests use the same two roles as local development: Flyway
 * migrates as the schema owner, the application connects as the non-owner role (ADR-0015).
 */
public abstract class PostgresIntegrationTest {

    protected static final String APP_DB_USER = "partnerguard_app";
    private static final String APP_DB_PASSWORD = "test-app-password";

    protected static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName("partnerguard")
            .withEnv("APP_DB_USER", APP_DB_USER)
            .withEnv("APP_DB_PASSWORD", APP_DB_PASSWORD)
            .withCopyFileToContainer(
                    MountableFile.forHostPath("docker/postgres/init/01-create-app-role.sh"),
                    "/docker-entrypoint-initdb.d/01-create-app-role.sh");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> APP_DB_USER);
        registry.add("spring.datasource.password", () -> APP_DB_PASSWORD);
        registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
        registry.add("spring.flyway.placeholders.appDbUser", () -> APP_DB_USER);
        // Fixtures from specs/fixtures.md. Tests that change them must roll back (@Transactional).
        registry.add("spring.flyway.locations", () -> "classpath:db/migration,classpath:db/seed");
    }
}
