package com.shareride.gateway;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;

/**
 * Architecture guardrails for ShareRide API Gateway using ArchUnit.
 *
 * <p>Enforces strict naming conventions, layering, and Spring annotations across controllers,
 * services, reactive filters, configurations, and prevents forbidden blocking dependencies.
 */
@AnalyzeClasses(
        packages = "com.shareride.gateway",
        importOptions = {ImportOption.DoNotIncludeTests.class})
public class GatewayArchitectureTest {

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
                            "All gateway controllers must end with 'Controller' and be annotated"
                                    + " with @RestController");

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
    public static final ArchRule filters_naming_and_interface =
            classes()
                    .that()
                    .resideInAPackage("..filter..")
                    .should()
                    .haveSimpleNameEndingWith("Filter")
                    .andShould()
                    .beAssignableTo(GlobalFilter.class)
                    .because(
                            "All gateway filters must end with 'Filter' and implement"
                                    + " GlobalFilter");

    @ArchTest
    public static final ArchRule configs_naming_and_annotation =
            classes()
                    .that()
                    .resideInAPackage("..config..")
                    .should()
                    .haveSimpleNameEndingWith("Config")
                    .andShould()
                    .beAnnotatedWith(Configuration.class)
                    .because(
                            "All configuration classes in .config must end with 'Config' and be"
                                    + " annotated with @Configuration");

    @ArchTest
    public static final ArchRule no_blocking_servlet_or_jpa_in_gateway =
            noClasses()
                    .that()
                    .resideInAPackage("com.shareride.gateway..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(
                            "jakarta.servlet..", "jakarta.persistence..", "org.hibernate..")
                    .because(
                            "Gateway is a reactive WebFlux service and must not import blocking"
                                    + " Servlet or JPA dependencies");
}
