package com.partnerguard.api;

import com.partnerguard.common.web.SortOptions;
import com.partnerguard.support.PostgresIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.Sort;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.yaml.snakeyaml.Yaml;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DOC-01: what the running application documents (springdoc, built from the controllers)
 * matches contracts/openapi.yaml. Also keeps the sort allow-lists in sync with the contract.
 */
@SpringBootTest(properties = "springdoc.api-docs.enabled=true")
@AutoConfigureMockMvc
class ContractDriftTests extends PostgresIntegrationTest {

    @Autowired
    MockMvcTester mvc;

    @Test
    @DisplayName("DOC-01 runtime API docs match the contract's paths, methods and status codes")
    @SuppressWarnings("unchecked")
    void runtimeDocsMatchContract() throws IOException {
        MvcTestResult result = mvc.get().uri("/v3/api-docs").exchange();
        assertThat(result).hasStatusOk();
        Map<String, Object> runtime = JsonMapper.builder().build()
                .readValue(result.getResponse().getContentAsString(), Map.class);

        Map<String, Set<String>> expected = operations(contract());
        assertThat(expected).as("contract operations").hasSizeGreaterThanOrEqualTo(9);
        assertThat(operations(runtime)).isEqualTo(expected);
    }

    @Test
    @DisplayName("API-02 sort allow-lists in code equal the contract's sort enums")
    @SuppressWarnings("unchecked")
    void sortAllowListsMatchContract() throws IOException {
        Map<String, Object> parameters = (Map<String, Object>) ((Map<String, Object>) contract().get("components"))
                .get("parameters");
        Map<String, Map<String, Sort>> code = Map.of(
                "PartnerSort", SortOptions.PARTNERS,
                "ContractSort", SortOptions.CONTRACTS,
                "ClaimSort", SortOptions.CLAIMS);

        code.forEach((name, allowList) -> {
            Map<String, Object> schema = (Map<String, Object>) ((Map<String, Object>) parameters.get(name)).get("schema");
            assertThat(allowList.keySet()).as(name).containsExactlyInAnyOrderElementsOf((List<String>) schema.get("enum"));
            assertThat(allowList).as(name + " default").containsKey((String) schema.get("default"));
        });
    }

    /** "METHOD path" → documented status codes. */
    @SuppressWarnings("unchecked")
    private static Map<String, Set<String>> operations(Map<String, Object> openApi) {
        Map<String, Set<String>> operations = new TreeMap<>();
        ((Map<String, Map<String, Object>>) openApi.get("paths")).forEach((path, methods) ->
                methods.forEach((method, operation) -> {
                    if (operation instanceof Map<?, ?> op && op.get("responses") instanceof Map<?, ?> responses) {
                        Set<String> codes = new TreeSet<>();
                        responses.keySet().forEach(code -> codes.add(String.valueOf(code)));
                        operations.put(method.toUpperCase() + " " + path, codes);
                    }
                }));
        return operations;
    }

    private static Map<String, Object> contract() throws IOException {
        return new Yaml().load(Files.readString(Path.of("contracts/openapi.yaml")));
    }
}
