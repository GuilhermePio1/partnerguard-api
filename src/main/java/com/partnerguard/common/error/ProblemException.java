package com.partnerguard.common.error;

import java.util.List;

/**
 * A failure that maps to one entry of the Problem Details catalog. The exception message is
 * only the type name: data from the request never ends up in it, nor in the response.
 */
public class ProblemException extends RuntimeException {

    private final ProblemType type;
    private final List<FieldViolation> violations;

    public ProblemException(ProblemType type) {
        this(type, List.of());
    }

    public ProblemException(ProblemType type, List<FieldViolation> violations) {
        // No stack trace: these are expected outcomes, not bugs.
        super(type.name(), null, false, false);
        this.type = type;
        this.violations = List.copyOf(violations);
    }

    public static ProblemException invalid(String field, String message) {
        return new ProblemException(ProblemType.VALIDATION_ERROR, List.of(new FieldViolation(field, message)));
    }

    public ProblemType type() {
        return type;
    }

    public List<FieldViolation> violations() {
        return violations;
    }
}
