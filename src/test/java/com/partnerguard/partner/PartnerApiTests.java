package com.partnerguard.partner;

import com.partnerguard.support.ApiIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static org.assertj.core.api.Assertions.assertThat;

class PartnerApiTests extends ApiIntegrationTest {

    @Test
    @DisplayName("PRT-01 lists partners sorted by legal name")
    void listsPartners() {
        MvcTestResult result = mvc.get().uri("/api/v1/partners").exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.content[*].legalName").asArray()
                .containsExactly("Oficina Alfa Ltda", "Oficina Beta Ltda");
    }

    @Test
    @DisplayName("API-03 lists default to page 0 and size 20")
    void defaultPaging() {
        MvcTestResult result = mvc.get().uri("/api/v1/partners").exchange();

        assertThat(result).bodyJson().extractingPath("$.page").isEqualTo(0);
        assertThat(result).bodyJson().extractingPath("$.size").isEqualTo(20);
        assertThat(result).bodyJson().extractingPath("$.totalElements").isEqualTo(2);
    }

    @Test
    @DisplayName("PRT-04 responses never contain an unmasked CNPJ")
    void taxIdIsMasked() {
        MvcTestResult result = mvc.get().uri("/api/v1/partners").exchange();

        assertThat(result).bodyJson().extractingPath("$.content[*].taxId").asArray()
                .containsExactly("**.***.***/****-81", "**.***.***/****-98");
        assertThat(body(result))
                .doesNotContainPattern("[0-9A-Z]{12}[0-9]{2}")
                .doesNotContainPattern("[0-9A-Z]{2}\\.[0-9A-Z]{3}\\.[0-9A-Z]{3}/[0-9A-Z]{4}-[0-9]{2}");
    }

    @Test
    @DisplayName("PRT-02 / API-07 creates a partner with an alphanumeric CNPJ")
    void createsPartner() {
        MvcTestResult result = post("""
                {"legalName":"Oficina Gama Ltda","taxId":"12.ABC.345/01DE-35"}""");

        assertThat(result).hasStatus(201);
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("ACTIVE");
        assertThat(result).bodyJson().extractingPath("$.taxId").isEqualTo("**.***.***/****-35");
        String id = com.jayway.jsonpath.JsonPath.read(body(result), "$.id");
        assertThat(result).headers().hasValue("Location", "/api/v1/partners/" + id);
    }

    @Test
    @DisplayName("PRT-03 accepts a CNPJ without punctuation")
    void acceptsUnpunctuatedCnpj() {
        assertThat(post("""
                {"legalName":"Oficina Gama Ltda","taxId":"33444555000181"}""")).hasStatus(201);
    }

    @Test
    @DisplayName("PRT-05 rejects a wrong check digit")
    void rejectsWrongCheckDigit() {
        assertInvalidField(post("""
                {"legalName":"Oficina Gama Ltda","taxId":"12.ABC.345/01DE-36"}"""), "taxId");
    }

    @Test
    @DisplayName("PRT-06 rejects a CNPJ of repeated characters")
    void rejectsRepeatedCnpj() {
        assertInvalidField(post("""
                {"legalName":"Oficina Gama Ltda","taxId":"00000000000000"}"""), "taxId");
    }

    @Test
    @DisplayName("PRT-07 a duplicate tax ID is a 409 that doesn't echo it")
    void rejectsDuplicateTaxId() {
        MvcTestResult result = post("""
                {"legalName":"Another Alfa","taxId":"11.222.333/0001-81"}""");

        assertProblem(result, 409, "duplicate-tax-id");
        assertThat(body(result))
                .doesNotContain("11222333000181")
                .doesNotContain("11.222.333")
                .doesNotContain("uq_partner_tax_id");
    }

    @Test
    @DisplayName("PRT-08 / HRD-04 rejects markup in the legal name")
    void rejectsMarkupInLegalName() {
        assertInvalidField(post("""
                {"legalName":"<script>alert(1)</script>","taxId":"33444555000181"}"""), "legalName");
    }

    @Test
    @DisplayName("PRT-09 / API-04 rejects unknown properties such as status")
    void rejectsUnknownProperty() {
        MvcTestResult result = post("""
                {"legalName":"Oficina Gama Ltda","taxId":"33444555000181","status":"SUSPENDED"}""");

        assertInvalidField(result, "status");
        assertThat(result).bodyJson().extractingPath("$.errors[0].message").isEqualTo("is not a known property");
    }

    @Test
    @DisplayName("API-05 a non-JSON body is a 415 Problem Details")
    void rejectsNonJsonBody() {
        MvcTestResult result = mvc.post().uri("/api/v1/partners")
                .contentType(MediaType.TEXT_PLAIN).content("legalName=x").exchange();

        assertProblem(result, 415, "unsupported-media-type");
    }

    @Test
    @DisplayName("ERR-02 malformed JSON is a 400 without the parser's message")
    void rejectsMalformedJson() {
        MvcTestResult result = post("{\"legalName\":");

        assertProblem(result, 400, "validation-error");
        assertThat(body(result))
                .doesNotContainIgnoringCase("unexpected")
                .doesNotContainIgnoringCase("jackson")
                .doesNotContain("line:");
    }

    @Test
    @DisplayName("API-01 page size above 100 is a 400 naming size")
    void rejectsOversizedPage() {
        assertInvalidField(mvc.get().uri("/api/v1/partners?size=101").exchange(), "size");
    }

    @Test
    @DisplayName("API-02 a sort value outside the allow-list is a 400")
    void rejectsUnknownSort() {
        assertInvalidField(mvc.get().uri("/api/v1/partners?sort=password,asc").exchange(), "sort");
    }

    @Test
    @DisplayName("ADR-0003 without a partner identity, /partners/me fails closed with 403")
    void currentPartnerRequiresPartnerIdentity() {
        assertProblem(mvc.get().uri("/api/v1/partners/me").exchange(), 403, "forbidden");
    }

    private MvcTestResult post(String json) {
        return mvc.post().uri("/api/v1/partners").contentType(MediaType.APPLICATION_JSON).content(json).exchange();
    }
}
