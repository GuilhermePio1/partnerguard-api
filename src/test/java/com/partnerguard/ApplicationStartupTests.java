package com.partnerguard;

import com.partnerguard.support.PostgresIntegrationTest;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@AutoConfigureMockMvc
class ApplicationStartupTests extends PostgresIntegrationTest {

    @Autowired
    Flyway flyway;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    MockMvcTester mvc;

    @Test
    @DisplayName("ADR-0015 Flyway applies the migrations as the schema owner")
    void flywayAppliesMigrationsAsOwner() {
        MigrationInfo[] applied = flyway.info().applied();

        assertThat(applied)
                .filteredOn(info -> info.getVersion() != null)
                .extracting(info -> info.getVersion().getVersion())
                .contains("1", "2", "3", "4", "5");
        // The seed is a repeatable migration (no version), applied after the schema (ADR-0019).
        assertThat(applied).filteredOn(info -> info.getVersion() == null)
                .extracting(MigrationInfo::getDescription)
                .containsExactly("seed fixtures");
        assertThat(applied).allSatisfy(info -> assertThat(info.getInstalledBy()).isEqualTo(POSTGRES.getUsername()));
    }

    @Test
    @DisplayName("ADR-0015 the application connects as the non-owner role")
    void applicationUsesNonOwnerRole() {
        assertThat(jdbcTemplate.queryForObject("select current_user", String.class)).isEqualTo(APP_DB_USER);
    }

    @Test
    @DisplayName("ADR-0015 the application role cannot create tables")
    void applicationRoleCannotCreateTables() {
        assertThatThrownBy(() -> jdbcTemplate.execute("create table should_not_exist (id int)"))
                .isInstanceOf(DataAccessException.class)
                .rootCause()
                .hasMessageContaining("permission denied for schema public");
    }

    @Test
    @DisplayName("ADR-0015 the application role can't delete rows or write read-only tables")
    void applicationRoleHasLeastPrivilege() {
        assertThatThrownBy(() -> jdbcTemplate.update("delete from claim"))
                .rootCause().hasMessageContaining("permission denied for table claim");
        assertThatThrownBy(() -> jdbcTemplate.update("update policy set status = 'EXPIRED'"))
                .rootCause().hasMessageContaining("permission denied for table policy");
        assertThatThrownBy(() -> jdbcTemplate.update("update partner set status = 'SUSPENDED'"))
                .rootCause().hasMessageContaining("permission denied for table partner");
    }

    @Test
    @DisplayName("AUTHZ-14 health is UP and shows no details")
    void healthIsUpWithoutDetails() {
        assertThat(mvc.get().uri("/actuator/health"))
                .hasStatusOk()
                .bodyJson().isStrictlyEqualTo("{\"status\":\"UP\"}");
    }

    @Test
    @DisplayName("DOC-02 outside the local profile, API docs and Swagger UI are not served")
    void apiDocsHiddenOutsideLocalProfile() {
        assertThat(mvc.get().uri("/v3/api-docs")).hasStatus(404);
        assertThat(mvc.get().uri("/swagger-ui/index.html")).hasStatus(404);
        assertThat(mvc.get().uri("/swagger-ui.html")).hasStatus(404);
    }

    @Test
    @DisplayName("AUTHZ-15 actuator exposes nothing but health")
    void otherActuatorEndpointsAreNotExposed() {
        for (String endpoint : new String[] {"env", "beans", "heapdump", "configprops", "mappings"}) {
            assertThat(mvc.get().uri("/actuator/" + endpoint)).hasStatus4xxClientError();
        }
    }
}
