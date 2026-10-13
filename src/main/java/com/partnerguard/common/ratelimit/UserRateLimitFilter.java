package com.partnerguard.common.ratelimit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * Layer 2 (specs/rate-limiting.md rule 2, ADR-0006, ADR-0022): runs after JWT validation and
 * before authorization. Authenticated callers are keyed on the verified {@code sub} only,
 * so a forged token can neither dodge its own limit nor drain someone else's: it was already
 * refused with 401 and never gets here. Everything without an authentication shares the
 * anonymous bucket of its IP.
 *
 * <p>Not a Spring bean, for the same reason as {@link PreAuthRateLimitFilter}.
 */
public class UserRateLimitFilter extends OncePerRequestFilter {

    private static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final RateLimitBuckets buckets;
    private final RateLimitRejections rejections;

    public UserRateLimitFilter(RateLimitBuckets buckets, RateLimitRejections rejections) {
        this.buckets = buckets;
        this.rejections = rejections;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean authenticated = authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
        boolean allowed = authenticated
                ? consumeAsUser(authentication.getName(), request, response)
                : consume(RateLimit.ANONYMOUS, request.getRemoteAddr(), request, response);
        if (allowed) {
            chain.doFilter(request, response);
        }
    }

    /**
     * Writes take a token from the write bucket and from the user bucket, or from neither:
     * a refused request takes nothing from this layer (rule 4).
     */
    private boolean consumeAsUser(String subject, HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        if (!WRITE_METHODS.contains(request.getMethod())) {
            return consume(RateLimit.USER, subject, request, response);
        }
        if (!consume(RateLimit.USER_WRITES, subject, request, response)) {
            return false;
        }
        if (!consume(RateLimit.USER, subject, request, response)) {
            buckets.refund(RateLimit.USER_WRITES, subject);
            return false;
        }
        return true;
    }

    private boolean consume(RateLimit limit, String key, HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        RateLimitDecision decision = buckets.tryConsume(limit, key);
        if (!decision.allowed()) {
            rejections.reject(limit, key, decision, request, response);
        }
        return decision.allowed();
    }
}
