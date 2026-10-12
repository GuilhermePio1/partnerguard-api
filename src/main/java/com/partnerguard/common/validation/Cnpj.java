package com.partnerguard.common.validation;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Brazilian company registration number (CNPJ), numeric or alphanumeric (ADR-0010).
 *
 * <p>The first 12 characters may be letters or digits, the last 2 are numeric check digits.
 * Each character counts as its ASCII code minus 48, so digits keep their value and
 * {@code A = 17} ... {@code Z = 42}.
 */
public final class Cnpj {

    private static final Pattern FORMAT =
            Pattern.compile("^[0-9A-Z]{2}\\.?[0-9A-Z]{3}\\.?[0-9A-Z]{3}/?[0-9A-Z]{4}-?[0-9]{2}$");
    private static final int[] FIRST_WEIGHTS = {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
    private static final int[] SECOND_WEIGHTS = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};

    private Cnpj() {
    }

    /**
     * Returns the normalized CNPJ (14 uppercase characters, no punctuation) if the input is a
     * valid CNPJ, or empty if it isn't.
     */
    public static Optional<String> normalize(String input) {
        if (input == null) {
            return Optional.empty();
        }
        String upper = input.toUpperCase(Locale.ROOT);
        if (!FORMAT.matcher(upper).matches()) {
            return Optional.empty();
        }
        String raw = upper.replaceAll("[./-]", "");
        if (raw.chars().distinct().count() == 1) {
            return Optional.empty(); // e.g. 00000000000000
        }
        char first = checkDigit(raw.substring(0, 12), FIRST_WEIGHTS);
        char second = checkDigit(raw.substring(0, 12) + first, SECOND_WEIGHTS);
        return raw.charAt(12) == first && raw.charAt(13) == second ? Optional.of(raw) : Optional.empty();
    }

    private static char checkDigit(String base, int[] weights) {
        int sum = 0;
        for (int i = 0; i < base.length(); i++) {
            sum += (base.charAt(i) - '0') * weights[i];
        }
        int remainder = sum % 11;
        return remainder < 2 ? '0' : (char) ('0' + 11 - remainder);
    }
}
