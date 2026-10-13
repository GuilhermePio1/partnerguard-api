package com.partnerguard.common.security;

import com.partnerguard.common.error.ProblemResponseWriter;
import com.partnerguard.common.error.ProblemType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.authorization.ExpressionAuthorizationDecision;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Answers every 403, from the filter chain and from @PreAuthorize or field checks alike
 * (specs/authorization.md rule 10, ADR-0008). The client gets the same {@code forbidden}
 * Problem Details whatever the reason; the reason goes to the {@code access_denied} log.
 */
@Component
public class ProblemAccessDeniedHandler implements AccessDeniedHandler {

    private static final Logger log = LoggerFactory.getLogger(ProblemAccessDeniedHandler.class);

    private final ProblemResponseWriter problems;

    ProblemAccessDeniedHandler(ProblemResponseWriter problems) {
        this.problems = problems;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException denied) throws IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        log.warn("event=access_denied sub={} method={} path={} reason=\"{}\"",
                LogValues.loggable(authentication == null ? null : authentication.getName()),
                request.getMethod(), LogValues.loggable(request.getRequestURI()), LogValues.loggable(reason(denied)));

        problems.write(response, ProblemType.FORBIDDEN, request.getRequestURI());
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
