package com.partnerguard.common.error;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * Replaces Spring Boot's BasicErrorController for the servlet error dispatch (filters calling
 * sendError, exceptions thrown outside Spring MVC). Always answers application/problem+json,
 * whatever the Accept header says, so neither an HTML error page nor Boot's default error
 * attributes ever reach a client (ADR-0008, spec ERR-08).
 */
@Hidden
@RestController
public class ProblemDetailsErrorController implements ErrorController {

    private static final Logger log = LoggerFactory.getLogger(ProblemDetailsErrorController.class);

    @RequestMapping("${spring.web.error.path:/error}")
    public ResponseEntity<ProblemDetail> handleError(HttpServletRequest request) {
        Object statusCode = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        if (statusCode == null) {
            // Not an error dispatch (e.g. a client calling GET /error): there is no such resource.
            return problem(ProblemType.NOT_FOUND.toProblemDetail(), request.getRequestURI());
        }

        HttpStatusCode status = toErrorStatus(statusCode);
        if (status.is5xxServerError()
                && request.getAttribute(RequestDispatcher.ERROR_EXCEPTION) instanceof Throwable failure) {
            // Thrown outside Spring MVC (in a filter), so ApiExceptionHandler never saw it (ERR-06).
            log.error("event=unhandled_exception correlationId={}", MDC.get(CorrelationIdFilter.MDC_KEY), failure);
        }
        ProblemDetail problem = ProblemType.forStatus(status)
                .map(ProblemType::toProblemDetail)
                .orElseGet(() -> ProblemDetail.forStatus(status));
        return problem(problem, (String) request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI));
    }

    private static ResponseEntity<ProblemDetail> problem(ProblemDetail problem, @Nullable String path) {
        problem.setInstance(toUri(path));
        return ResponseEntity.status(problem.getStatus())
                // A preset content type makes Spring MVC skip Accept negotiation.
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    private static HttpStatusCode toErrorStatus(Object statusCode) {
        if (statusCode instanceof Integer code && code >= 400 && code <= 599) {
            return HttpStatusCode.valueOf(code);
        }
        return ProblemType.INTERNAL_ERROR.status();
    }

    private static @Nullable URI toUri(@Nullable String path) {
        if (path == null) {
            return null;
        }
        try {
            return URI.create(path);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
