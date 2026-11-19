package com.shareride.auth;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.shareride.common.persistence.BaseEntity;
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
                    .allowEmptyShould(true)
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
                    .allowEmptyShould(true)
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
                    .allowEmptyShould(true)
                    .because(
                            "AccountVerificationRepository must only be injected in .service.impl,"
                                    + " never in controllers");

    @ArchTest
    public static final ArchRule synchronous_inter_service_calls_must_use_grpc =
            noClasses()
                    .that()
                    .resideInAPackage("com.shareride.auth..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(
                            "org.springframework.web.client..",
                            "org.springframework.web.reactive.function.client..",
                            "java.net.http..")
                    .because(
                            "Constitution Principle IV: All internal synchronous inter-service"
                                    + " communication MUST use gRPC, not RestTemplate, WebClient,"
                                    + " RestClient, or HttpClient");

    @ArchTest
    public static final ArchRule downstream_services_must_not_expose_business_rest_controllers =
            noClasses()
                    .that()
                    .areAnnotatedWith(RestController.class)
                    .should()
                    .resideInAPackage("..controller..")
                    .allowEmptyShould(true)
                    .because(
                            "Constitution Principle IV: Downstream services must expose business"
                                    + " operations via gRPC only. HTTP port is reserved exclusively"
                                    + " for Spring Boot Actuator management endpoints");

    @ArchTest
    public static final ArchRule grpc_clients_must_use_channel_factory =
            classes()
                    .that()
                    .resideInAPackage("..client..")
                    .and()
                    .haveSimpleNameEndingWith("GrpcClient")
                    .should()
                    .dependOnClassesThat()
                    .haveSimpleName("GrpcChannelFactory")
                    .allowEmptyShould(true)
                    .because(
                            "All gRPC client wrappers must obtain managed channels via"
                                    + " GrpcChannelFactory for connection pooling and lifecycle"
                                    + " management");
}
