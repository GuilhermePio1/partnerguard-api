package com.partnerguard.common.caller;

import com.partnerguard.common.ownership.OwnershipScope;

import java.util.Optional;
import java.util.UUID;

/**
 * Who is calling, as far as data access is concerned. Controllers ask this bean for the
 * caller's ownership scope, role and partner identity; nothing else in the code reads them from
 * the request (ADR-0003).
 *
 * <p>Every method fails closed: a caller that should never have reached a controller (no
 * mapped role, or a misconfigured partner) gets an access-denied error, never a broader scope.
 */
public interface CallerContext {

    /** Partner callers see their own rows only; ADMIN and ANALYST see everything. */
    OwnershipScope ownershipScope();

    /** The strongest role the caller holds (ADMIN, then ANALYST, then PARTNER). */
    Role role();

    /** The caller's partner id, taken from the validated token. Empty for internal callers. */
    Optional<UUID> partnerId();

    /** The validated token's {@code sub}, for the audit log (specs/audit.md). */
    String subject();
}
