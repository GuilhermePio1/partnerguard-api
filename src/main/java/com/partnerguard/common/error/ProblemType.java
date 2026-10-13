package com.partnerguard.common.error;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

import java.net.URI;
import java.util.Arrays;
import java.util.Optional;

/**
 * The Problem Details catalog from specs/errors.md (ADR-0008). Titles and details are
 * fixed text: they never carry exception messages or other internal information.
 */
public enum ProblemType {

    VALIDATION_ERROR("validation-error", HttpStatus.BAD_REQUEST,
            "Invalid request", "The request is invalid."),
    UNAUTHORIZED("unauthorized", HttpStatus.UNAUTHORIZED,
            "Authentication required", "A valid access token is required."),
    FORBIDDEN("forbidden", HttpStatus.FORBIDDEN,
            "Access denied", "You are not allowed to perform this operation."),
    NOT_FOUND("not-found", HttpStatus.NOT_FOUND,
            "Resource not found", "The requested resource was not found."),
    DUPLICATE_TAX_ID("duplicate-tax-id", HttpStatus.CONFLICT,
            "Partner already exists", "A partner with this tax ID already exists."),
    INVALID_STATUS_TRANSITION("invalid-status-transition", HttpStatus.CONFLICT,
            "Invalid status transition", "The claim cannot move to the requested status."),
    CLAIM_NOT_EDITABLE("claim-not-editable", HttpStatus.CONFLICT,
            "Claim can no longer be edited", "The claim can no longer be edited."),
    UNSUPPORTED_MEDIA_TYPE("unsupported-media-type", HttpStatus.UNSUPPORTED_MEDIA_TYPE,
            "Unsupported media type", "The request body must be application/json."),
    RATE_LIMIT_EXCEEDED("rate-limit-exceeded", HttpStatus.TOO_MANY_REQUESTS,
            "Too many requests", "Rate limit exceeded. Retry later."),
    INTERNAL_ERROR("internal-error", HttpStatus.INTERNAL_SERVER_ERROR,
            "Internal error", "An unexpected error occurred.");

    public static final String BASE_URI = "https://problems.partnerguard.example/";

    private final URI type;
    private final HttpStatus status;
    private final String title;
    private final String detail;

    ProblemType(String slug, HttpStatus status, String title, String detail) {
        this.type = URI.create(BASE_URI + slug);
        this.status = status;
        this.title = title;
        this.detail = detail;
    }

    public URI type() {
        return type;
    }

    public HttpStatus status() {
        return status;
    }

    public String title() {
        return title;
    }

    public String detail() {
        return detail;
    }

    public ProblemDetail toProblemDetail() {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(type);
        problem.setTitle(title);
        return problem;
    }

    /**
     * The catalog entry used when only the status is known (no domain context).
     * Domain-specific types such as {@link #DUPLICATE_TAX_ID} are never chosen here.
     */
    public static Optional<ProblemType> forStatus(HttpStatusCode status) {
        return Arrays.stream(values())
                .filter(type -> type.status.value() == status.value())
                .filter(type -> type.status != HttpStatus.CONFLICT)
                .findFirst();
    }
}
