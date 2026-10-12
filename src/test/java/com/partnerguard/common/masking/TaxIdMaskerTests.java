package com.partnerguard.common.masking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TaxIdMaskerTests {

    @Test
    @DisplayName("PRT-04 only the two check digits stay visible")
    void masksAllButCheckDigits() {
        assertThat(TaxIdMasker.mask("11222333000181")).isEqualTo("**.***.***/****-81");
        assertThat(TaxIdMasker.mask("12ABC34501DE35")).isEqualTo("**.***.***/****-35");
    }

    @Test
    @DisplayName("PRT-04 refuses values that are not normalized, instead of leaking them")
    void refusesUnnormalizedInput() {
        assertThatThrownBy(() -> TaxIdMasker.mask("11.222.333/0001-81")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageNotContaining("11");
    }
}
