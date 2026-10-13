package com.partnerguard.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.io.support.SpringFactoriesLoader;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** specs/hardening.md rules 13 to 15 (ADR-0018). */
@ExtendWith(OutputCaptureExtension.class)
class DatabaseTlsCheckTests {

    private static final String VERIFIED =
            "jdbc:postgresql://db.internal:5432/partnerguard?sslmode=verify-full&sslrootcert=/etc/partnerguard/pg-ca.crt";

    private final DatabaseTlsCheck check = new DatabaseTlsCheck();

    @ParameterizedTest(name = "HRD-13 without local/test, a datasource URL with {0} stops the application, naming spring.datasource.url")
    @ValueSource(strings = {"sslmode=require&sslrootcert=/ca.crt", "sslmode=verify-ca&sslrootcert=/ca.crt",
            "sslmode=prefer&sslrootcert=/ca.crt", "sslmode=allow&sslrootcert=/ca.crt",
            "sslmode=disable&sslrootcert=/ca.crt", "sslrootcert=/ca.crt", "sslmode=verify-full", ""})
    @DisplayName("HRD-13 without local/test, a datasource URL without verified TLS stops the application")
    void datasourceWithoutVerifiedTlsFails(String query) {
        MockEnvironment environment = environment("jdbc:postgresql://db.internal:5432/partnerguard?" + query, VERIFIED);

        assertThatThrownBy(() -> process(environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("spring.datasource.url")
                .hasMessageNotContaining("spring.flyway.url");
    }

    @Test
    @DisplayName("HRD-13 the check is registered, so it runs on every application start")
    void checkIsRegistered() {
        // Boot's own post-processors need constructor arguments; only ours matters here.
        SpringFactoriesLoader.FailureHandler skipOthers = (factoryType, implementation, failure) -> { };
        assertThat(SpringFactoriesLoader.forDefaultResourceLocation()
                .load(EnvironmentPostProcessor.class, null, skipOthers))
                .hasAtLeastOneElementOfType(DatabaseTlsCheck.class);
    }

    @Test
    @DisplayName("HRD-13 without local/test, a missing datasource URL stops the application")
    void missingDatasourceUrlFails() {
        MockEnvironment environment = new MockEnvironment();

        assertThatThrownBy(() -> process(environment)).hasMessageContaining("spring.datasource.url");
    }

    @Test
    @DisplayName("HRD-14 without local/test, a Flyway URL without sslrootcert stops the application, naming spring.flyway.url")
    void flywayWithoutRootCertFails() {
        MockEnvironment environment = environment(VERIFIED,
                "jdbc:postgresql://db.internal:5432/partnerguard?sslmode=verify-full");

        assertThatThrownBy(() -> process(environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("spring.flyway.url")
                .hasMessageNotContaining("spring.datasource.url");
    }

    @Test
    @DisplayName("HRD-15 without local/test, both URLs with sslmode=verify-full and sslrootcert pass")
    void verifiedTlsPasses() {
        assertThatCode(() -> process(environment(VERIFIED, VERIFIED))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("HRD-15 an unset Flyway URL means Flyway uses the datasource, which is checked")
    void unsetFlywayUrlPasses() {
        MockEnvironment environment = new MockEnvironment().withProperty("spring.datasource.url", VERIFIED);

        assertThatCode(() -> process(environment)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("HRD-16 a failing check on a URL with password=secret puts the secret neither in the error nor in the log")
    void failureDoesNotEchoCredentials(CapturedOutput output) {
        MockEnvironment environment = environment(
                "jdbc:postgresql://app:secret@db.internal:5432/partnerguard?user=app&password=secret&sslmode=disable",
                VERIFIED);

        assertThatThrownBy(() -> process(environment))
                .hasMessageContaining("spring.datasource.url")
                .hasMessageNotContaining("secret")
                .hasMessageNotContaining("db.internal");
        assertThat(output.getAll()).doesNotContain("secret");
    }

    @ParameterizedTest(name = "HRD-17 with the {0} profile, a URL without sslmode passes")
    @ValueSource(strings = {"local", "test"})
    @DisplayName("HRD-17 the local and test profiles are exempt")
    void localAndTestProfilesAreExempt(String profile) {
        MockEnvironment environment = environment("jdbc:postgresql://localhost:5433/partnerguard",
                "jdbc:postgresql://localhost:5433/partnerguard");
        environment.setActiveProfiles(profile);

        assertThatCode(() -> process(environment)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("HRD-17 another profile (prod) is not exempt")
    void otherProfilesAreNotExempt() {
        MockEnvironment environment = environment("jdbc:postgresql://localhost:5433/partnerguard", VERIFIED);
        environment.setActiveProfiles("prod");

        assertThatThrownBy(() -> process(environment)).hasMessageContaining("spring.datasource.url");
    }

    private void process(MockEnvironment environment) {
        check.postProcessEnvironment(environment, new SpringApplication());
    }

    private static MockEnvironment environment(String datasourceUrl, String flywayUrl) {
        return new MockEnvironment()
                .withProperty("spring.datasource.url", datasourceUrl)
                .withProperty("spring.flyway.url", flywayUrl);
    }
}
