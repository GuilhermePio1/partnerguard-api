package com.partnerguard.api;

import com.partnerguard.api.model.ClaimUpdateRequest;
import com.partnerguard.api.model.PartnerCreateRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the contract-first build (ADR-0007): the generated code matches
 * contracts/openapi.yaml and carries its validation constraints.
 */
class GeneratedContractTests {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void openValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @Test
    @DisplayName("ADR-0007 every operationId in the contract is a generated API method")
    @SuppressWarnings("unchecked")
    void everyOperationIsGenerated() throws IOException {
        Map<String, Object> contract = new Yaml().load(Files.readString(Path.of("contracts/openapi.yaml")));
        Map<String, Map<String, Map<String, Object>>> paths =
                (Map<String, Map<String, Map<String, Object>>>) contract.get("paths");
        Set<String> operationIds = paths.values().stream()
                .flatMap(methods -> methods.values().stream())
                .map(operation -> (String) operation.get("operationId"))
                .collect(Collectors.toSet());

        Set<String> generated = Stream.of(PartnersApi.class, ContractsApi.class, ClaimsApi.class, PoliciesApi.class)
                .flatMap(api -> Arrays.stream(api.getDeclaredMethods()))
                .map(Method::getName)
                .collect(Collectors.toSet());

        assertThat(generated).isEqualTo(operationIds);
    }

    @Test
    @DisplayName("ADR-0013 generated DTOs reject markup in free text")
    void freeTextRejectsMarkup() {
        ClaimUpdateRequest request = new ClaimUpdateRequest().description("<script>alert(1)</script>");

        assertThat(violatedFields(validator.validate(request))).containsExactly("description");
    }

    @Test
    @DisplayName("ADR-0007 generated DTOs carry the contract's numeric limits")
    void amountLimitsComeFromTheContract() {
        assertThat(violatedFields(validator.validate(new ClaimUpdateRequest().amount(new BigDecimal("0.00")))))
                .containsExactly("amount");
        assertThat(validator.validate(new ClaimUpdateRequest().amount(new BigDecimal("1250.50")))).isEmpty();
    }

    @Test
    @DisplayName("ADR-0007 required fields and the CNPJ format come from the contract")
    void requiredFieldsAndCnpjFormat() {
        PartnerCreateRequest missingTaxId = new PartnerCreateRequest();
        missingTaxId.setLegalName("Oficina Gama Ltda");
        assertThat(violatedFields(validator.validate(missingTaxId))).containsExactly("taxId");

        PartnerCreateRequest valid = new PartnerCreateRequest();
        valid.setLegalName("Oficina Gama Ltda");
        valid.setTaxId("12.ABC.345/01DE-35");
        assertThat(validator.validate(valid)).isEmpty();
    }

    private static <T> Set<String> violatedFields(Set<ConstraintViolation<T>> violations) {
        return violations.stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }
}
