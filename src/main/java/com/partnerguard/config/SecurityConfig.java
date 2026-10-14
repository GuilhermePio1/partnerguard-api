package com.partnerguard.config;

import com.partnerguard.common.ratelimit.PreAuthRateLimitFilter;
import com.partnerguard.common.ratelimit.RateLimitBuckets;
import com.partnerguard.common.ratelimit.RateLimitProperties;
import com.partnerguard.common.ratelimit.RateLimitRejections;
import com.partnerguard.common.ratelimit.UserRateLimitFilter;
import com.partnerguard.common.security.ApiCallerAuthorizationManager;
import com.partnerguard.common.security.DenyByDefault;
import com.partnerguard.common.security.KeycloakRealmRoleConverter;
import com.partnerguard.common.security.ProblemAccessDeniedHandler;
import com.partnerguard.common.security.ProblemAuthenticationEntryPoint;
import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.JwkSetUriJwtDecoderBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.header.HeaderWriter;
import org.springframework.security.web.header.writers.DelegatingRequestMatcherHeaderWriter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.time.Clock;
import java.time.Duration;

/**
 * Stateless JWT resource server (ADR-0002, ADR-0020, specs/authentication.md) that denies
 * by default (ADR-0005, specs/authorization.md): outside the public paths and {@code /api/**}
 * everything is refused, every {@code /api/**} request passes the caller gate
 * ({@link ApiCallerAuthorizationManager}), and each controller method names its roles with
 * {@code @PreAuthorize}. Two rate-limit filters (ADR-0006) sit around JWT validation: a per-IP
 * ceiling before it and per-user limits after it.
 *
 * <p>The {@code JwtDecoder} is Spring Boot's, built from
 * {@code spring.security.oauth2.resourceserver.jwt.issuer-uri} and {@code audiences}: it checks
 * the signature against the realm's JWKS, the issuer and the audience. This class narrows the
 * algorithm to RS256 and replaces the default expiry check with one that has the specified
 * skew and reads the application {@link Clock}.
 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
@EnableConfigurationProperties(RateLimitProperties.class)
class SecurityConfig {

    /** Allowed clock skew for {@code exp} and {@code nbf} (rule 2). */
    static final Duration CLOCK_SKEW = Duration.ofSeconds(30);

    private static final String CONTENT_SECURITY_POLICY = "Content-Security-Policy";
    private static final String STRICT_CSP = "default-src 'none'; frame-ancestors 'none'";
    private static final String SWAGGER_UI_CSP = "default-src 'none'; script-src 'self'; style-src 'self'; "
            + "img-src 'self' data:; connect-src 'self'; frame-ancestors 'none'";

    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http, ProblemAuthenticationEntryPoint entryPoint,
                                    ProblemAccessDeniedHandler accessDeniedHandler,
                                    ApiCallerAuthorizationManager apiCallers,
                                    RateLimitBuckets rateLimitBuckets, RateLimitRejections rateLimitRejections,
                                    @Value("${springdoc.api-docs.enabled:false}") boolean apiDocsEnabled) {
        http
                // No sessions and no cookies (rule 4), so there is nothing for CSRF to ride on.
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(AbstractHttpConfigurer::disable)
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                // No browser front end in scope, so no CORS: cross-origin browsers get no ACAO (rule 11).
                .cors(AbstractHttpConfigurer::disable)
                .headers(headers -> headers
                        .frameOptions(HeadersConfigurer.FrameOptionsConfig::deny)
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.NO_REFERRER))
                        // Spring's default is "no-cache, no-store, max-age=0, must-revalidate" plus Pragma
                        // and Expires on every response; the spec wants exactly no-store, on /api/** only.
                        .cacheControl(HeadersConfigurer.CacheControlConfig::disable)
                        .addHeaderWriter(new DelegatingRequestMatcherHeaderWriter(
                                PathPatternRequestMatcher.withDefaults().matcher("/api/**"),
                                new StaticHeadersWriter(HttpHeaders.CACHE_CONTROL, "no-store")))
                        .addHeaderWriter(contentSecurityPolicy(apiDocsEnabled)))
                // Rate limits (ADR-0006): a per-IP ceiling before the token is read, then per-user
                // limits on the verified sub, before any authorization decision.
                .addFilterBefore(new PreAuthRateLimitFilter(rateLimitBuckets, rateLimitRejections),
                        BearerTokenAuthenticationFilter.class)
                .addFilterBefore(new UserRateLimitFilter(rateLimitBuckets, rateLimitRejections),
                        AuthorizationFilter.class)
                .authorizeHttpRequests(requests -> {
                    // Error dispatches only render a status that was already decided (ADR-0008).
                    requests.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll();
                    requests.requestMatchers(HttpMethod.GET, "/actuator/health").permitAll();
                    // API docs are enabled only in the local profile (ADR-0005, DOC-02).
                    if (apiDocsEnabled) {
                        // /openapi.yaml is the contract itself, which Swagger UI renders (ADR-0024).
                        requests.requestMatchers("/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**", "/openapi.yaml")
                                .permitAll();
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

    /**
     * One Content-Security-Policy header per response (browsers intersect several): the strict
     * policy everywhere, except Swagger UI in the local profile, which needs its own scripts,
     * styles and images (specs/hardening.md, header table).
     */
    private static HeaderWriter contentSecurityPolicy(boolean apiDocsEnabled) {
        HeaderWriter strict = new StaticHeadersWriter(CONTENT_SECURITY_POLICY, STRICT_CSP);
        if (!apiDocsEnabled) {
            return strict;
        }
        HeaderWriter swaggerUi = new StaticHeadersWriter(CONTENT_SECURITY_POLICY, SWAGGER_UI_CSP);
        RequestMatcher swaggerUiPaths = new OrRequestMatcher(
                PathPatternRequestMatcher.withDefaults().matcher("/swagger-ui.html"),
                PathPatternRequestMatcher.withDefaults().matcher("/swagger-ui/**"));
        return (request, response) -> (swaggerUiPaths.matches(request) ? swaggerUi : strict)
                .writeHeaders(request, response);
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
