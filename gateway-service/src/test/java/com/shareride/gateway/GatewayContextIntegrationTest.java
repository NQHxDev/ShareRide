package com.shareride.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

/**
 * Basic integration test verifying that the Gateway application context loads successfully with all
 * core beans properly configured.
 *
 * <p>This test starts the full Spring Boot application context including all auto-configurations,
 * filters, and service beans. It validates that all dependencies are satisfied and there are no
 * circular dependencies or configuration errors.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = {
            GatewayServiceApplication.class,
            com.shareride.gateway.config.TestGrpcConfiguration.class
        },
        properties = {
            "shareride.grpc.client.auth-service.host=localhost",
            "shareride.grpc.client.auth-service.port=9090",
            "shareride.grpc.client.template-service.host=localhost",
            "shareride.grpc.client.template-service.port=9091",
            // Disable Spring Cloud Gateway gRPC filter that requires external dependencies
            "spring.cloud.gateway.filter.json-to-grpc.enabled=false"
        })
@ActiveProfiles("test")
@DisplayName("Gateway Context Integration Test")
class GatewayContextIntegrationTest {

    @Autowired private ApplicationContext applicationContext;

    @Autowired private Environment environment;

    @Test
    @DisplayName("Application context loads successfully")
    void contextLoads() {
        assertThat(applicationContext).isNotNull();
        assertThat(applicationContext.getBeanDefinitionCount()).isGreaterThan(0);
    }

    @Test
    @DisplayName("Gateway service application is properly configured")
    void gatewayApplicationIsConfigured() {
        assertThat(applicationContext.getId()).contains("gateway");
        assertThat(environment.getProperty("spring.application.name"))
                .isEqualTo("gateway-service-test");
    }
}
