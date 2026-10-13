package com.partnerguard.common.error;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Gives every request a correlation id (specs/errors.md rule 3, ADR-0023): returned in
 * {@code X-Correlation-Id} and put in the logging context as {@code correlationId}, so a
 * client quoting it leads to the matching log lines.
 *
 * <p>The id is always generated here, never read from the request: a client must not choose
 * what goes into the server's logs. Runs first, ahead of Spring Security, so 401, 403 and 429
 * answered by filters carry it too. The error dispatch of the same request reuses it.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";
    private static final String ATTRIBUTE = CorrelationIdFilter.class.getName() + ".id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // A request attribute is server-side state, so the error dispatch finds the same id.
        String id = request.getAttribute(ATTRIBUTE) instanceof String existing ? existing : UUID.randomUUID().toString();
        request.setAttribute(ATTRIBUTE, id);
        response.setHeader(HEADER, id);
        MDC.put(MDC_KEY, id);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    /** The error dispatch also needs the id, in the response and in the logging context. */
    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return false;
    }
}
