package com.partnerguard.common.validation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class CnpjTests {

    @ParameterizedTest
    @ValueSource(strings = {"11222333000181", "98765432000198", "12ABC34501DE35"})
    @DisplayName("PRT-13 reference CNPJs are valid and changing any single character invalidates them")
    void referenceValuesAndSingleCharacterChanges(String cnpj) {
        assertThat(Cnpj.normalize(cnpj)).contains(cnpj);

        for (int i = 0; i < cnpj.length(); i++) {
            char original = cnpj.charAt(i);
            // Replace with a different character allowed at that position.
            char replacement = i < 12 ? (original == 'Z' ? 'Y' : original == '9' ? '8' : (char) (original + 1))
                    : (original == '9' ? '0' : (char) (original + 1));
            String changed = cnpj.substring(0, i) + replacement + cnpj.substring(i + 1);
            assertThat(Cnpj.normalize(changed)).as("changed position %d: %s", i, changed).isEmpty();
        }
    }

    @Test
    @DisplayName("PRT-13 punctuation is optional and letters are uppercased")
    void normalizesFormatting() {
        assertThat(Cnpj.normalize("11.222.333/0001-81")).contains("11222333000181");
        assertThat(Cnpj.normalize("12.abc.345/01de-35")).contains("12ABC34501DE35");
    }

    @Test
    @DisplayName("PRT-06 repeated characters and malformed values are invalid")
    void rejectsRepeatedAndMalformed() {
        assertThat(Cnpj.normalize("00000000000000")).isEmpty();
        assertThat(Cnpj.normalize("1122233300018")).isEmpty();
        assertThat(Cnpj.normalize("11222333000181X")).isEmpty();
        assertThat(Cnpj.normalize("12ABC34501DEAB")).isEmpty(); // check digits must be numeric
        assertThat(Cnpj.normalize(null)).isEmpty();
    }
}
