package com.partnerguard.common.error;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Writes a catalog problem straight to the servlet response, for errors raised in filters
 * before Spring MVC runs (authentication entry point, rate-limit filters).
 * Produces the same fields as the MVC handlers (ADR-0008).
 */
@Component
public class ProblemResponseWriter {

    private final JsonMapper jsonMapper;

    ProblemResponseWriter(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public void write(HttpServletResponse response, ProblemType type, String instance) throws IOException {
        // Read from the catalog entry itself: ProblemDetail's getters are @Nullable.
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", type.type().toString());
        body.put("title", type.title());
        body.put("status", type.status().value());
        body.put("detail", type.detail());
        body.put("instance", instance);

        response.setStatus(type.status().value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        jsonMapper.writeValue(response.getOutputStream(), body);
    }
}
