package com.partnerguard.common.caller;

import com.partnerguard.common.ownership.OwnershipScope;

import java.util.Optional;
import java.util.UUID;

/**
 * Who is calling, as far as data access is concerned. Controllers ask this bean for the
 * caller's ownership scope and partner identity; nothing else in the code reads them from
 * the request (ADR-0003).
 */
public interface CallerContext {

    OwnershipScope ownershipScope();

    /** The caller's partner id, taken from the validated token. Empty for internal callers. */
    Optional<UUID> partnerId();
}
