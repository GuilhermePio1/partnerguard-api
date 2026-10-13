/**
 * Spring Security plumbing shared by every feature: how realm roles become authorities, the
 * caller gate in front of {@code /api/**}, and how authentication and authorization failures
 * are answered and logged (ADR-0002, ADR-0005, ADR-0021).
 *
 * <p>Null-marked like Spring Framework 7: types are non-null unless annotated
 * {@code @Nullable}.
 */
@NullMarked
package com.partnerguard.common.security;

import org.jspecify.annotations.NullMarked;
