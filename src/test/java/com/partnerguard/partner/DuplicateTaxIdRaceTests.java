package com.partnerguard.partner;

import com.partnerguard.support.ApiIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

/**
 * specs/errors.md ERR-04: the unique constraint, not the pre-check, decides a race between two
 * creates with the same CNPJ. The spy makes the pre-check miss, as if the other insert
 * committed right after it ran.
 */
class DuplicateTaxIdRaceTests extends ApiIntegrationTest {

    @MockitoSpyBean
    PartnerRepository partners;

    @Test
    @DisplayName("ERR-04 a DB unique violation on taxId is a 409 duplicate-tax-id that doesn't name the constraint")
    void uniqueViolationIsAConflict() {
        PartnerRepository spy = AopTestUtils.getUltimateTargetObject(partners);
        doReturn(false).when(spy).existsByTaxId(anyString());

        MvcTestResult result = mvc.post().uri("/api/v1/partners").contentType(MediaType.APPLICATION_JSON)
                .content("{\"legalName\":\"Another Alfa\",\"taxId\":\"11.222.333/0001-81\"}").exchange();

        assertProblem(result, 409, "duplicate-tax-id");
        assertThat(body(result))
                .doesNotContain("uq_partner_tax_id")
                .doesNotContain("constraint")
                .doesNotContain("11222333000181")
                .doesNotContain("SQL");
        // The 409 came from the constraint: the insert was attempted after the pre-check missed.
        verify(spy).saveAndFlush(any(Partner.class));
    }
}
