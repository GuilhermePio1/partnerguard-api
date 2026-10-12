package com.partnerguard.partner;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PartnerTests {

    @Test
    @DisplayName("PRT-14 toString never contains the tax ID")
    void toStringExcludesTaxId() {
        Partner partner = Partner.create("Oficina Alfa Ltda", "11222333000181");

        assertThat(partner.toString())
                .contains(partner.getId().toString())
                .doesNotContain("11222333000181")
                .doesNotContain("0181");
    }
}
