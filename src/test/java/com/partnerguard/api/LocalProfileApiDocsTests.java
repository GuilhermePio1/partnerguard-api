package com.partnerguard.api;

import com.partnerguard.support.PostgresIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** DOC-02 and DOC-04, positive halves: in the local profile, API docs and Swagger UI are served. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class LocalProfileApiDocsTests extends PostgresIntegrationTest {

    @Autowired
    MockMvcTester mvc;

    @Test
    @DisplayName("DOC-02 the local profile serves API docs and Swagger UI")
    void localProfileServesDocs() {
        assertThat(mvc.get().uri("/v3/api-docs").exchange()).hasStatusOk();
        assertThat(mvc.get().uri("/swagger-ui/index.html").exchange()).hasStatusOk();
    }

    @Test
    @DisplayName("DOC-04 the local profile serves contracts/openapi.yaml unchanged at /openapi.yaml, without a token")
    void localProfileServesTheContract() throws IOException {
        MvcTestResult result = mvc.get().uri("/openapi.yaml").exchange();

        assertThat(result).hasStatusOk();
        assertThat(result.getResponse().getContentAsByteArray())
                .isEqualTo(Files.readAllBytes(Path.of("contracts/openapi.yaml")));
    }

    @Test
    @DisplayName("DOC-04 Swagger UI's configuration points to /openapi.yaml, not to the generated docs")
    void swaggerUiRendersTheContract() {
        MvcTestResult config = mvc.get().uri("/v3/api-docs/swagger-config").exchange();

        assertThat(config).hasStatusOk();
        assertThat(config).bodyJson().extractingPath("$.url").isEqualTo("/openapi.yaml");
    }
}
