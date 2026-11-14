package com.shareride.notification;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.library.Architectures;
import org.junit.jupiter.api.Test;

/**
 * ArchUnit architecture test enforcing ShareRide enterprise layered pattern, package boundaries,
 * and zero-tolerance code quality policies.
 */
class NotificationArchitectureTest {

    private static final String BASE_PACKAGE = "com.shareride.notification";

    private final JavaClasses importedClasses =
            new ClassFileImporter()
                    .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                    .importPackages(BASE_PACKAGE);

    @Test
    void shouldEnforceLayeredArchitecture() {
        Architectures.layeredArchitecture()
                .consideringAllDependencies()
                .layer("Controller")
                .definedBy(BASE_PACKAGE + ".controller..")
                .layer("Service")
                .definedBy(BASE_PACKAGE + ".service..")
                .layer("Repository")
                .definedBy(BASE_PACKAGE + ".repository..")
                .layer("Domain")
                .definedBy(BASE_PACKAGE + ".domain..")
                .layer("DTO")
                .definedBy(BASE_PACKAGE + ".dto..")
                .layer("Client")
                .definedBy(BASE_PACKAGE + ".client..")
                .layer("Consumer")
                .definedBy(BASE_PACKAGE + ".consumer..")
                .layer("Publisher")
                .definedBy(BASE_PACKAGE + ".publisher..")
                .whereLayer("Controller")
                .mayNotBeAccessedByAnyLayer()
                .whereLayer("Service")
                .mayOnlyBeAccessedByLayers("Controller", "Service", "Consumer")
                .whereLayer("Repository")
                .mayOnlyBeAccessedByLayers("Service", "Publisher")
                .whereLayer("Domain")
                .mayOnlyBeAccessedByLayers(
                        "Service",
                        "Repository",
                        "Controller",
                        "Client",
                        "Consumer",
                        "Publisher",
                        "DTO")
                .whereLayer("DTO")
                .mayOnlyBeAccessedByLayers(
                        "Controller", "Service", "Consumer", "Publisher", "Client")
                .whereLayer("Consumer")
                .mayNotBeAccessedByAnyLayer()
                .whereLayer("Publisher")
                .mayNotBeAccessedByAnyLayer()
                .check(importedClasses);
    }

    @Test
    void controllersShouldNotInjectRepositories() {
        noClasses()
                .that()
                .resideInAPackage("..controller..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("..repository..")
                .because(
                        "Controllers must delegate to Service layer, not access Repositories"
                                + " directly")
                .check(importedClasses);
    }

    @Test
    void controllersShouldBeAnnotatedWithRestController() {
        classes()
                .that()
                .resideInAPackage("..controller..")
                .and()
                .haveSimpleNameEndingWith("Controller")
                .should()
                .beAnnotatedWith(org.springframework.web.bind.annotation.RestController.class)
                .because("All controllers must use @RestController annotation")
                .check(importedClasses);
    }

    @Test
    void serviceInterfacesShouldResideInServicePackage() {
        classes()
                .that()
                .resideInAPackage("..service..")
                .and()
                .areInterfaces()
                .and()
                .haveSimpleNameEndingWith("Service")
                .should()
                .resideInAPackage("..service")
                .because("Service interfaces must reside in .service package, not .service.impl")
                .check(importedClasses);
    }

    @Test
    void serviceImplementationsShouldResideInServiceImplPackage() {
        classes()
                .that()
                .resideInAPackage("..service..")
                .and()
                .haveSimpleNameEndingWith("ServiceImpl")
                .should()
                .resideInAPackage("..service.impl")
                .because("Service implementations must reside in .service.impl package")
                .check(importedClasses);
    }

    @Test
    void repositoriesShouldBeInterfaces() {
        classes()
                .that()
                .resideInAPackage("..repository..")
                .and()
                .haveSimpleNameEndingWith("Repository")
                .should()
                .beInterfaces()
                .because("Repositories must be Spring Data JPA interfaces")
                .check(importedClasses);
    }

    @Test
    void domainEntitiesShouldExtendBaseEntity() {
        classes()
                .that()
                .resideInAPackage("..domain..")
                .and()
                .areAnnotatedWith(jakarta.persistence.Entity.class)
                .should()
                .beAssignableTo(com.shareride.common.persistence.BaseEntity.class)
                .because("All domain entities must extend BaseEntity for UUIDv7 and audit fields")
                .check(importedClasses);
    }

    @Test
    void servicesShouldNotDependOnControllers() {
        noClasses()
                .that()
                .resideInAPackage("..service..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("..controller..")
                .because("Service layer must not depend on Controller layer")
                .check(importedClasses);
    }

    @Test
    void repositoriesShouldNotDependOnServices() {
        noClasses()
                .that()
                .resideInAPackage("..repository..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("..service..")
                .because("Repository layer must not depend on Service layer")
                .check(importedClasses);
    }
}
