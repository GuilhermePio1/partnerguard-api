package com.partnerguard.support;

import java.util.UUID;

/** Seed values from specs/fixtures.md. Keep in sync with db/seed/R__seed_fixtures.sql. */
public final class Fixtures {

    public static final UUID PARTNER_A = UUID.fromString("10000000-0000-4000-8000-00000000000a");
    public static final UUID PARTNER_B = UUID.fromString("10000000-0000-4000-8000-00000000000b");

    public static final UUID CONTRACT_A = UUID.fromString("30000000-0000-4000-8000-00000000000a");
    public static final UUID CONTRACT_B = UUID.fromString("30000000-0000-4000-8000-00000000000b");

    /** Partner A, SUBMITTED. */
    public static final UUID CLAIM_A1 = UUID.fromString("40000000-0000-4000-8000-00000000a001");
    /** Partner A, UNDER_REVIEW. */
    public static final UUID CLAIM_A2 = UUID.fromString("40000000-0000-4000-8000-00000000a002");
    /** Partner B, SUBMITTED. */
    public static final UUID CLAIM_B1 = UUID.fromString("40000000-0000-4000-8000-00000000b001");

    public static final UUID NEVER_EXISTS = UUID.fromString("40000000-0000-4000-8000-000000000000");

    public static final String POLICY_1 = "POL-2026-000001";
    public static final String UNKNOWN_POLICY = "POL-2026-999999";

    private Fixtures() {
    }
}
