package com.partnerguard.common.masking;

/** Masks a normalized CNPJ for every response: only the two check digits stay visible (ADR-0010). */
public final class TaxIdMasker {

    private TaxIdMasker() {
    }

    public static String mask(String normalizedTaxId) {
        if (normalizedTaxId == null || normalizedTaxId.length() != 14) {
            throw new IllegalArgumentException("not a normalized CNPJ");
        }
        return "**.***.***/****-" + normalizedTaxId.substring(12);
    }
}
