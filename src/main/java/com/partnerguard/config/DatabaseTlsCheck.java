package com.partnerguard.config;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Profiles;

import java.util.HashMap;
import java.util.Map;

/**
 * Refuses to start when a database connection could run without verified TLS
 * (specs/hardening.md rules 13 to 15, ADR-0018). Outside the {@code local} and {@code test}
 * profiles, {@code spring.datasource.url} and, when set, {@code spring.flyway.url} must carry
 * {@code sslmode=verify-full} and {@code sslrootcert}.
 *
 * <p>Runs as an environment post-processor, before any bean (Flyway included) can open a
 * connection, and after the config files are read, so the active profiles are known. Errors
 * name the property but never echo its value, which may hold credentials.
 *
 * <p>Registered in {@code META-INF/spring.factories}.
 */
public class DatabaseTlsCheck implements EnvironmentPostProcessor, Ordered {

    private static final Profiles EXEMPT = Profiles.of("local", "test");

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (environment.acceptsProfiles(EXEMPT)) {
            return;
        }
        requireVerifiedTls("spring.datasource.url", environment.getProperty("spring.datasource.url"), true);
        requireVerifiedTls("spring.flyway.url", environment.getProperty("spring.flyway.url"), false);
    }

    @Override
    public int getOrder() {
        // After ConfigDataEnvironmentPostProcessor, which loads the config files and the profiles.
        return Ordered.LOWEST_PRECEDENCE;
    }

    private static void requireVerifiedTls(String property, String url, boolean required) {
        if (url == null || url.isBlank()) {
            if (required) {
                throw new IllegalStateException(property + " must be set outside the local and test profiles"
                        + " (specs/hardening.md, ADR-0018)");
            }
            return;
        }
        Map<String, String> parameters = queryParameters(url);
        String rootCert = parameters.get("sslrootcert");
        if (!"verify-full".equals(parameters.get("sslmode")) || rootCert == null || rootCert.isBlank()) {
            throw new IllegalStateException(property + " must use sslmode=verify-full and set sslrootcert"
                    + " outside the local and test profiles (specs/hardening.md, ADR-0018)");
        }
    }

    private static Map<String, String> queryParameters(String url) {
        Map<String, String> parameters = new HashMap<>();
        int query = url.indexOf('?');
        if (query < 0) {
            return parameters;
        }
        for (String pair : url.substring(query + 1).split("&")) {
            int equals = pair.indexOf('=');
            if (equals > 0) {
                parameters.put(pair.substring(0, equals), pair.substring(equals + 1));
            }
        }
        return parameters;
    }
}
