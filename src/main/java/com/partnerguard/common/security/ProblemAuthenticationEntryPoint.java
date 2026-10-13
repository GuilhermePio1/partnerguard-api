package com.partnerguard.common.security;

import com.partnerguard.common.error.ProblemResponseWriter;
import com.partnerguard.common.error.ProblemType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Answers every failed authentication, missing or invalid token alike (specs/authentication.md
 * rules 5 and 6). The client gets the same {@code unauthorized} Problem Details and a bare
 * {@code WWW-Authenticate: Bearer} challenge whatever check failed; Spring's default challenge
 * would add an {@code error_description} naming it. The reason goes to the server log only.
 */
@Component
public class ProblemAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final Logger log = LoggerFactory.getLogger(ProblemAuthenticationEntryPoint.class);
    private static final int MAX_LOGGED_LENGTH = 200;

    private final ProblemResponseWriter problems;

    ProblemAuthenticationEntryPoint(ProblemResponseWriter problems) {
        this.problems = problems;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException failure) throws IOException {
        // The path comes without its query string, so a token sent as ?access_token= is never logged.
        log.warn("event=authentication_failed ip={} method={} path={} reason=\"{}\"",
                request.getRemoteAddr(), request.getMethod(),
                loggable(request.getRequestURI()), loggable(failure.getMessage()));

        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        problems.write(response, ProblemType.UNAUTHORIZED, request.getRequestURI());
    }

    /** Client-influenced text: no control characters (log forging) and a bounded length. */
    private static String loggable(@Nullable String value) {
        if (value == null) {
            return "";
        }
        String clean = value.replaceAll("[\\p{Cntrl}\"]", "_");
        return clean.length() > MAX_LOGGED_LENGTH ? clean.substring(0, MAX_LOGGED_LENGTH) + "..." : clean;
    }
}
