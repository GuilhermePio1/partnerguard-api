package com.partnerguard;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.repository.Repository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/** Package-by-feature rules (ADR-0011) and the @PreAuthorize rule (ADR-0005). */
class ArchitectureTests {

    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.partnerguard");

    private static final String[] FEATURES = {"..partner..", "..contract..", "..claim..", "..policy..", "..audit.."};

    @Test
    @DisplayName("ARCH-01 feature packages have no dependency cycles")
    void noCyclesBetweenFeatures() {
        slices().matching("com.partnerguard.(*)..").should().beFreeOfCycles().check(CLASSES);
    }

    @Test
    @DisplayName("ARCH-02 repositories are package-private, so only their own feature can use them")
    void repositoriesStayInsideTheirFeature() {
        classes().that().areAssignableTo(Repository.class).should().notBePublic().check(CLASSES);
    }

    @Test
    @DisplayName("ARCH-03 controllers don't depend on repositories")
    void controllersDontUseRepositories() {
        noClasses().that().areAnnotatedWith(RestController.class)
                .should().dependOnClassesThat().areAssignableTo(Repository.class)
                .check(CLASSES);
    }

    @Test
    @DisplayName("AUTHZ-01 every public controller method has @PreAuthorize")
    void controllerMethodsArePreAuthorized() {
        // The error controller only renders statuses already decided elsewhere (authorization.md rule 9).
        methods().that().arePublic()
                .and().areDeclaredInClassesThat().areAnnotatedWith(RestController.class)
                .and().areDeclaredInClassesThat().doNotHaveSimpleName("ProblemDetailsErrorController")
                .should().beAnnotatedWith(PreAuthorize.class)
                .check(CLASSES);
    }

    @Test
    @DisplayName("ADR-0011 common and config don't depend on feature packages")
    void sharedCodeDoesntDependOnFeatures() {
        noClasses().that().resideInAnyPackage("..common..", "..config..")
                .should().dependOnClassesThat().resideInAnyPackage(FEATURES)
                .check(CLASSES);
    }
}
