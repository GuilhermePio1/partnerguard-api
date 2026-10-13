package com.partnerguard.common.ratelimit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Layer 1 (specs/rate-limiting.md rule 1, ADR-0006): a flood ceiling per client IP for every
 * request, before the token is even looked at, so a flood of forged tokens is refused without
 * spending CPU on signature checks. It never reads the token.
 *
 * <p>Not a Spring bean: {@code SecurityConfig} adds it to the security filter chain, and a
 * bean would also be registered as a plain servlet filter and count every request twice.
 */
public class PreAuthRateLimitFilter extends OncePerRequestFilter {

    private final RateLimitBuckets buckets;
    private final RateLimitRejections rejections;

    public PreAuthRateLimitFilter(RateLimitBuckets buckets, RateLimitRejections rejections) {
        this.buckets = buckets;
        this.rejections = rejections;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // The socket address (rule 6). X-Forwarded-For counts only through a trusted proxy setup
        // (server.forward-headers-strategy), which rewrites getRemoteAddr() before this runs.
        String ip = request.getRemoteAddr();
        RateLimitDecision decision = buckets.tryConsume(RateLimit.IP_CEILING, ip);
        if (!decision.allowed()) {
            rejections.reject(RateLimit.IP_CEILING, ip, decision, request, response);
            return;
        }
        chain.doFilter(request, response);
    }
}
