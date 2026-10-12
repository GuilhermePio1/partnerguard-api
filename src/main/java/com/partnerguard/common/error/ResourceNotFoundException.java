package com.partnerguard.common.error;

/**
 * The resource doesn't exist, or it belongs to another partner. The two cases are
 * deliberately indistinguishable (ADR-0004).
 */
public class ResourceNotFoundException extends ProblemException {

    public ResourceNotFoundException() {
        super(ProblemType.NOT_FOUND);
    }
}
