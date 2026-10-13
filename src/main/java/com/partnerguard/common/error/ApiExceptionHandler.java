package com.partnerguard.common.error;

import jakarta.servlet.http.HttpServletRequest;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.TypeMismatchException;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.Errors;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.exc.InvalidNullException;
import tools.jackson.databind.exc.UnrecognizedPropertyException;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * Turns every exception raised while handling a request into a Problem Details response from
 * the catalog (ADR-0008). Spring's own messages (which can echo parser or framework details)
 * are never sent: titles and details come from {@link ProblemType}, and validation problems
 * list only field names with fixed messages.
 */
@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ProblemException.class)
    ResponseEntity<Object> handleProblem(ProblemException ex, WebRequest request) {
        ProblemDetail problem = ex.type().toProblemDetail();
        addViolations(problem, ex.violations());
        return respond(problem, request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> handleUnexpected(Exception ex, WebRequest request) {
        // The correlation id is also in the X-Correlation-Id response header (ERR-06).
        log.error("event=unhandled_exception correlationId={}", MDC.get(CorrelationIdFilter.MDC_KEY), ex);
        return respond(ProblemType.INTERNAL_ERROR.toProblemDetail(), request);
    }

    /** Every exception Spring MVC itself handles (400, 404, 405, 415...) ends up here. */
    @Override
    protected @Nullable ResponseEntity<Object> handleExceptionInternal(
            Exception ex, @Nullable Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = ProblemType.forStatus(status)
                .map(ProblemType::toProblemDetail)
                .orElseGet(() -> ProblemDetail.forStatus(status));
        addViolations(problem, violationsOf(ex));
        problem.setInstance(pathOf(request));
        return super.handleExceptionInternal(ex, problem, headers, status, request);
    }

    private ResponseEntity<Object> respond(ProblemDetail problem, WebRequest request) {
        problem.setInstance(pathOf(request));
        return ResponseEntity.status(problem.getStatus()).body(problem);
    }

    private static List<FieldViolation> violationsOf(Exception ex) {
        return switch (ex) {
            case MethodArgumentNotValidException e -> fieldViolations(e.getBindingResult());
            case HandlerMethodValidationException e -> methodViolations(e);
            case TypeMismatchException e -> e.getPropertyName() == null
                    ? List.of()
                    : List.of(new FieldViolation(e.getPropertyName(), "has an invalid value"));
            case HttpMessageNotReadableException e -> bodyViolations(e);
            default -> List.of();
        };
    }

    private static List<FieldViolation> methodViolations(HandlerMethodValidationException ex) {
        List<FieldViolation> violations = new ArrayList<>();
        ex.getParameterValidationResults().forEach(result -> {
            if (result instanceof ParameterErrors errors) {
                violations.addAll(fieldViolations(errors));
            } else {
                String parameter = result.getMethodParameter().getParameterName();
                if (parameter != null) {
                    result.getResolvableErrors().forEach(error -> violations.add(new FieldViolation(parameter, messageOf(error))));
                }
            }
        });
        return violations;
    }

    private static List<FieldViolation> fieldViolations(Errors errors) {
        return errors.getFieldErrors().stream()
                .map(error -> new FieldViolation(error.getField(), messageOf(error)))
                .toList();
    }

    /** Bean Validation's default messages are fixed English text without the rejected value. */
    private static String messageOf(MessageSourceResolvable error) {
        return error.getDefaultMessage() != null ? error.getDefaultMessage() : "is invalid";
    }

    /** Unreadable bodies: report which field failed, never Jackson's message. */
    private static List<FieldViolation> bodyViolations(HttpMessageNotReadableException ex) {
        if (!(ex.getCause() instanceof DatabindException cause) || cause.getPath().isEmpty()) {
            return List.of(); // malformed JSON
        }
        String field = cause.getPath().getLast().getPropertyName();
        if (field == null) {
            return List.of();
        }
        String message = switch (cause) {
            case UnrecognizedPropertyException ignored -> "is not a known property";
            case InvalidNullException ignored -> "must not be null";
            default -> "has an invalid value";
        };
        return List.of(new FieldViolation(field, message));
    }

    private static void addViolations(ProblemDetail problem, List<FieldViolation> violations) {
        if (!violations.isEmpty()) {
            problem.setProperty("errors", violations);
        }
    }

    private static @Nullable URI pathOf(WebRequest request) {
        if (request instanceof NativeWebRequest nativeRequest
                && nativeRequest.getNativeRequest() instanceof HttpServletRequest servletRequest) {
            try {
                return URI.create(servletRequest.getRequestURI());
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        return null;
    }
}
