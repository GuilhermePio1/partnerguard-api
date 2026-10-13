package com.partnerguard.config;

import com.partnerguard.common.security.ApiCallerAuthorizationManager;
import com.partnerguard.common.security.DenyByDefault;
import com.partnerguard.common.security.KeycloakRealmRoleConverter;
import com.partnerguard.common.security.ProblemAccessDeniedHandler;
import com.partnerguard.common.security.ProblemAuthenticationEntryPoint;
import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.JwkSetUriJwtDecoderBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.time.Clock;
import java.time.Duration;

/**
 * Stateless JWT resource server (ADR-0002, ADR-0020, specs/authentication.md) that denies
 * by default (ADR-0005, specs/authorization.md): outside the public paths and {@code /api/**}
 * everything is refused, every {@code /api/**} request passes the caller gate
 * ({@link ApiCallerAuthorizationManager}), and each controller method names its roles with
 * {@code @PreAuthorize}.
 *
 * <p>The {@code JwtDecoder} is Spring Boot's, built from
 * {@code spring.security.oauth2.resourceserver.jwt.issuer-uri} and {@code audiences}: it checks
 * the signature against the realm's JWKS, the issuer and the audience. This class narrows the
 * algorithm to RS256 and replaces the default expiry check with one that has the specified
 * skew and reads the application {@link Clock}.
 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
class SecurityConfig {

    /** Allowed clock skew for {@code exp} and {@code nbf} (rule 2). */
    static final Duration CLOCK_SKEW = Duration.ofSeconds(30);

    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http, ProblemAuthenticationEntryPoint entryPoint,
                                    ProblemAccessDeniedHandler accessDeniedHandler,
                                    ApiCallerAuthorizationManager apiCallers,
                                    @Value("${springdoc.api-docs.enabled:false}") boolean apiDocsEnabled) {
        http
                // No sessions and no cookies (rule 4), so there is nothing for CSRF to ride on.
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(AbstractHttpConfigurer::disable)
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(requests -> {
                    // Error dispatches only render a status that was already decided (ADR-0008).
                    requests.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll();
                    requests.requestMatchers(HttpMethod.GET, "/actuator/health").permitAll();
                    // API docs are enabled only in the local profile (ADR-0005, DOC-02).
                    if (apiDocsEnabled) {
                        requests.requestMatchers("/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**").permitAll();
                    }
                    requests.requestMatchers("/api/**").access(apiCallers);
                    requests.anyRequest().access(new DenyByDefault());
                })
                // Only the Authorization header is read: tokens in the query string, a form body or
                // a cookie are ignored (rule 3), which is the default bearer token resolver.
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(keycloakRoles()))
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler));
        return http.build();
    }

    /** Realm roles become authorities; the principal name stays the token's {@code sub}. */
    private static JwtAuthenticationConverter keycloakRoles() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new KeycloakRealmRoleConverter());
        return converter;
    }

    /** Boot adds this to its issuer and audience validators, in place of the default expiry check. */
    @Bean
    OAuth2TokenValidator<Jwt> jwtTimestampValidator(Clock clock) {
        JwtTimestampValidator validator = new JwtTimestampValidator(CLOCK_SKEW);
        validator.setClock(clock);
        return validator;
    }

    /** RS256 only, whatever algorithms the JWKS advertises (ADR-0002). */
    @Bean
    JwkSetUriJwtDecoderBuilderCustomizer rs256Only() {
        return builder -> builder.jwsAlgorithm(SignatureAlgorithm.RS256);
    }
}
