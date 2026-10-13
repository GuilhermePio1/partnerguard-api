package com.partnerguard.common.security;

import org.jspecify.annotations.Nullable;

/** Client-influenced text going into a log line. */
public final class LogValues {

    private static final int MAX_LENGTH = 200;

    private LogValues() {
    }

    /** No control characters or quotes (log forging) and a bounded length. */
    public static String loggable(@Nullable String value) {
        if (value == null) {
            return "";
        }
        String clean = value.replaceAll("[\\p{Cntrl}\"]", "_");
        return clean.length() > MAX_LENGTH ? clean.substring(0, MAX_LENGTH) + "..." : clean;
    }
}
