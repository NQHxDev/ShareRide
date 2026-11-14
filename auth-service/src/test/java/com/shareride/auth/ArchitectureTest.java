package com.shareride.auth;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ArchUnit tests enforcing architectural compliance per Constitution Principles II and V.
 * Controllers must not inject repositories directly; all data access must go through service layer.
 */
@DisplayName("Auth Service Architecture Tests")
class ArchitectureTest {

    private static JavaClasses classes;

    @BeforeAll
    static void setup() {
        classes =
                new ClassFileImporter()
                        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                        .importPackages("com.shareride.auth");
    }

    @Test
    @DisplayName("Controllers must not directly inject repositories")
    void controllers_mustNotInjectRepositories() {
        noClasses()
                .that()
                .resideInAPackage("..controller..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("..repository..")
                .because(
                        "Controllers must not bypass service layer - violates Constitution"
                                + " Principle II (Enterprise Service Layer Pattern)")
                .check(classes);
    }

    @Test
    @DisplayName("Controllers must be annotated with @RestController")
    void controllers_mustHaveRestControllerAnnotation() {
        classes()
                .that()
                .resideInAPackage("..controller..")
                .and()
                .haveSimpleNameEndingWith("Controller")
                .should()
                .beAnnotatedWith(org.springframework.web.bind.annotation.RestController.class)
                .because("All controllers must use @RestController annotation")
                .check(classes);
    }

    @Test
    @DisplayName("Service implementations must reside in service.impl package")
    void serviceImplementations_mustResideInImplPackage() {
        classes()
                .that()
                .haveSimpleNameEndingWith("ServiceImpl")
                .should()
                .resideInAPackage("..service.impl..")
                .because("Service implementations must be in service.impl package per Constitution")
                .check(classes);
    }

    @Test
    @DisplayName("Service interfaces must reside in service package")
    void serviceInterfaces_mustResideInServicePackage() {
        classes()
                .that()
                .areInterfaces()
                .and()
                .haveSimpleNameEndingWith("Service")
                .should()
                .resideInAPackage("..service..")
                .because("Service interfaces must be in service package per Constitution")
                .check(classes);
    }

    @Test
    @DisplayName("Repositories must be interfaces")
    void repositories_mustBeInterfaces() {
        classes()
                .that()
                .resideInAPackage("..repository..")
                .and()
                .haveSimpleNameEndingWith("Repository")
                .should()
                .beInterfaces()
                .because("Repositories should be Spring Data interfaces, not concrete classes")
                .check(classes);
    }

    @Test
    @DisplayName("Domain entities must reside in domain package")
    void domainEntities_mustResideInDomainPackage() {
        classes()
                .that()
                .areAnnotatedWith(jakarta.persistence.Entity.class)
                .should()
                .resideInAPackage("..domain..")
                .because("JPA entities must be in domain package for clear separation")
                .check(classes);
    }

    @Test
    @DisplayName("No cycles in package dependencies")
    void noCyclesInPackageDependencies() {
        com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices()
                .matching("com.shareride.auth.(*)..")
                .should()
                .beFreeOfCycles()
                .because("Circular dependencies make code hard to maintain and test")
                .check(classes);
    }
}
