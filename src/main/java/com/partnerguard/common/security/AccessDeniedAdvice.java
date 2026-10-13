package com.partnerguard.common.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.io.IOException;

/**
 * Access denials raised inside Spring MVC (@PreAuthorize, field-level checks, fail-closed
 * caller checks) get the same 403 and log line as those raised by the filter chain. Ordered
 * first, because the catch-all handler for unexpected exceptions would turn them into 500s.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class AccessDeniedAdvice {

    private final ProblemAccessDeniedHandler accessDeniedHandler;

    AccessDeniedAdvice(ProblemAccessDeniedHandler accessDeniedHandler) {
        this.accessDeniedHandler = accessDeniedHandler;
    }

    @ExceptionHandler(AccessDeniedException.class)
    void handleAccessDenied(AccessDeniedException denied, HttpServletRequest request,
                            HttpServletResponse response) throws IOException {
        accessDeniedHandler.handle(request, response, denied);
    }
}
