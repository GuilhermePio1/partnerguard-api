package com.partnerguard.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
class ClockConfig {

    /**
     * The one source of "now" for security decisions (token expiry, rate-limit refills),
     * so tests can move time with a fake clock instead of sleeping.
     */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
