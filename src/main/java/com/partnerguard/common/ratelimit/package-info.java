/**
 * The two rate-limit layers (specs/rate-limiting.md, ADR-0006, ADR-0022): a per-IP ceiling
 * before JWT validation and per-user limits after it, on Bucket4j token buckets held in a
 * bounded Caffeine cache.
 *
 * <p>Null-marked like Spring Framework 7: types are non-null unless annotated
 * {@code @Nullable}.
 */
@NullMarked
package com.partnerguard.common.ratelimit;

import org.jspecify.annotations.NullMarked;
