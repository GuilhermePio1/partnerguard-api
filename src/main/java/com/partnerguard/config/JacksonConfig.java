package com.partnerguard.config;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class JacksonConfig {

    /**
     * An explicit {@code null} in a request body is rejected (400) instead of being treated
     * like an absent field. No request DTO accepts null (CLM-20); together with
     * FAIL_ON_UNKNOWN_PROPERTIES this keeps PATCH bodies unambiguous.
     */
    @Bean
    JsonMapperBuilderCustomizer rejectExplicitNulls() {
        return builder -> builder.changeDefaultNullHandling(ignored -> JsonSetter.Value.forValueNulls(Nulls.FAIL));
    }
}
