package com.shareride.auth;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.shareride.common.persistence.BaseEntity;
import com.shareride.common.versioning.ApiVersion;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;

/**
 * Enterprise Architecture Guardrails using ArchUnit. Enforces strict layering, naming conventions,
 * and domain isolation.
 */
@AnalyzeClasses(
        packages = "com.shareride.auth",
        importOptions = {ImportOption.DoNotIncludeTests.class})
public class AuthArchitectureTest {

    @ArchTest
    public static final ArchRule controllers_should_not_depend_on_repositories =
            noClasses()
                    .that()
                    .resideInAPackage("..controller..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAPackage("..repository..")
                    .because(
                            "Controllers must interact with the Service layer, never directly with"
                                    + " Repositories");

    @ArchTest
    public static final ArchRule controllers_naming_and_annotation =
            classes()
                    .that()
                    .resideInAPackage("..controller..")
                    .should()
                    .haveSimpleNameEndingWith("Controller")
                    .andShould()
                    .beAnnotatedWith(RestController.class)
                    .because(
                            "All controllers must end with 'Controller' and be annotated with"
                                    + " @RestController");

    @ArchTest
    public static final ArchRule service_interfaces_naming =
            classes()
                    .that()
                    .resideInAPackage("..service")
                    .and()
                    .areInterfaces()
                    .should()
                    .haveSimpleNameEndingWith("Service")
                    .because(
                            "Service interfaces must reside in .service package and end with"
                                    + " 'Service'");

    @ArchTest
    public static final ArchRule service_implementations_naming_and_annotation =
            classes()
                    .that()
                    .resideInAPackage("..service.impl..")
                    .should()
                    .haveSimpleNameEndingWith("ServiceImpl")
                    .andShould()
                    .beAnnotatedWith(Service.class)
                    .because(
                            "Service implementations must reside in .service.impl, end with"
                                    + " 'ServiceImpl' and be annotated with @Service");

    @ArchTest
    public static final ArchRule repository_naming_and_annotation =
            classes()
                    .that()
                    .resideInAPackage("..repository..")
                    .and()
                    .areInterfaces()
                    .should()
                    .haveSimpleNameEndingWith("Repository")
                    .andShould()
                    .beAnnotatedWith(Repository.class)
                    .because(
                            "Repositories must end with 'Repository' and be annotated with"
                                    + " @Repository");

    @ArchTest
    public static final ArchRule entities_must_extend_base_entity =
            classes()
                    .that()
                    .resideInAPackage("..domain..")
                    .and()
                    .areAnnotatedWith(jakarta.persistence.Entity.class)
                    .should()
                    .beAssignableTo(BaseEntity.class)
                    .because(
                            "All domain entities must extend BaseEntity or SoftDeletableEntity for"
                                    + " UUIDv7 and audit support");

    @ArchTest
    public static final ArchRule verification_repository_should_only_be_injected_in_service_layer =
            noClasses()
                    .that()
                    .resideInAPackage("..controller..")
                    .should()
                    .dependOnClassesThat()
                    .haveSimpleName("AccountVerificationRepository")
                    .because(
                            "AccountVerificationRepository must only be injected in .service.impl,"
                                    + " never in controllers");

    @ArchTest
    public static final ArchRule auth_controller_should_have_api_version_annotation =
            classes()
                    .that()
                    .haveSimpleName("AuthController")
                    .should()
                    .beAnnotatedWith(ApiVersion.class)
                    .because("AuthController must declare @ApiVersion(\"v1\") for API versioning");
}
