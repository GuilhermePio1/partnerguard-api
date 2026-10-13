package com.partnerguard;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.repository.Repository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.core.domain.JavaCall.Predicates.target;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.name;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.nameMatching;
import static com.tngtech.archunit.core.domain.properties.HasOwner.Predicates.With.owner;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/** Package-by-feature rules (ADR-0011), the @PreAuthorize rule (ADR-0005) and the query rule (HRD-08). */
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
    @DisplayName("HRD-08 no query is built from a runtime string: no createQuery(String), createNativeQuery, JdbcTemplate or JDBC statements")
    void queriesAreNeverBuiltFromStrings() {
        // ArchUnit can't see string concatenation, so the rule bans every API that takes a query
        // string at runtime. @Query values are compile-time constants by Java's rules, so neither
        // concatenation of request data nor String.format can reach them. Criteria queries
        // (createQuery(CriteriaQuery)) and Spring Data derived queries stay allowed.
        DescribedPredicate<JavaCall<?>> jdbcStatement = target(owner(assignableTo(java.sql.Connection.class)))
                .and(target(nameMatching("prepareStatement|prepareCall|createStatement")));
        noClasses()
                .should().callMethod(EntityManager.class, "createQuery", String.class)
                .orShould().callMethod(EntityManager.class, "createQuery", String.class, Class.class)
                .orShould().callMethodWhere(target(owner(assignableTo(EntityManager.class)))
                        .and(target(name("createNativeQuery"))))
                .orShould().callMethodWhere(jdbcStatement)
                .orShould().dependOnClassesThat().belongToAnyOf(JdbcTemplate.class, NamedParameterJdbcTemplate.class,
                        java.sql.Statement.class)
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
