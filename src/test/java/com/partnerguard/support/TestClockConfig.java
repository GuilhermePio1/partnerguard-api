package com.partnerguard.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Replaces the application's clock with a {@link MutableClock}, so JWT timestamp checks and
 * rate-limit refills follow the test instead of the wall clock. Tests that pin it reset it
 * in {@code @AfterEach}.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestClockConfig {

    @Bean
    @Primary
    MutableClock testClock() {
        return new MutableClock();
    }
}
