package com.partnerguard.api;

import com.partnerguard.support.PostgresIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;

/** DOC-02, positive half: in the local profile, API docs and Swagger UI are served. */
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
}
