package com.partnerguard.common.error;

/** One entry of the {@code errors} array of a validation problem (contract schema FieldError). */
public record FieldViolation(String field, String message) {
}
