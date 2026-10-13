package com.partnerguard.common.security;

import com.partnerguard.common.error.ProblemResponseWriter;
import com.partnerguard.common.error.ProblemType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.authorization.ExpressionAuthorizationDecision;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.stream.Collectors;

/**
 * Answers every 403, from the filter chain and from @PreAuthorize or field checks alike
 * (specs/authorization.md rule 10, ADR-0008). The client gets the same {@code forbidden}
 * Problem Details whatever the reason; the reason goes to the {@code access_denied} log.
 */
@Component
public class ProblemAccessDeniedHandler implements AccessDeniedHandler {

    private static final Logger log = LoggerFactory.getLogger(ProblemAccessDeniedHandler.class);
    private static final String ROLE_PREFIX = "ROLE_";

    private final ProblemResponseWriter problems;

    ProblemAccessDeniedHandler(ProblemResponseWriter problems) {
        this.problems = problems;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException denied) throws IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        log.warn("event=access_denied sub={} roles={} method={} path={} reason=\"{}\"",
                LogValues.loggable(authentication == null ? null : authentication.getName()), roles(authentication),
                request.getMethod(), LogValues.loggable(request.getRequestURI()), LogValues.loggable(reason(denied)));

        problems.write(response, ProblemType.FORBIDDEN, request.getRequestURI());
    }

    /** The mapped roles, e.g. {@code ANALYST}; other authorities (such as Spring's factor ones) are left out. */
    private static String roles(@Nullable Authentication authentication) {
        if (authentication == null) {
            return "";
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority != null && authority.startsWith(ROLE_PREFIX))
                .map(authority -> authority.substring(ROLE_PREFIX.length()))
                .sorted()
                .collect(Collectors.joining(","));
    }

    private static String reason(AccessDeniedException denied) {
        if (denied instanceof AuthorizationDeniedException authorization) {
            if (authorization.getAuthorizationResult() instanceof ReasonedDecision decision) {
                return decision.reason();
            }
            if (authorization.getAuthorizationResult() instanceof ExpressionAuthorizationDecision expression) {
                return "requires " + expression.getExpression().getExpressionString();
            }
        }
        return String.valueOf(denied.getMessage());
    }
}
