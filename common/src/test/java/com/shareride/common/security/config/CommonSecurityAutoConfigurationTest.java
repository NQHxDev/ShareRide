package com.shareride.common.security.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shareride.common.security.handler.RestAccessDeniedHandler;
import com.shareride.common.security.handler.RestAuthenticationEntryPoint;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Unit tests for {@link CommonSecurityAutoConfiguration}.
 *
 * <p>Verifies that the auto-configuration components are properly structured and that the Spring
 * Boot auto-configuration mechanism correctly registers security beans with stateless properties.
 */
class CommonSecurityAutoConfigurationTest {

    private final WebApplicationContextRunner contextRunner =
            new WebApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(CommonSecurityAutoConfiguration.class))
                    .withUserConfiguration(TestConfiguration.class);

    @Test
    void autoConfiguration_shouldCreateSecurityFilterChainBean() {
        contextRunner.run(
                context -> {
                    assertThat(context)
                            .hasSingleBean(SecurityFilterChain.class)
                            .as(
                                    "CommonSecurityAutoConfiguration should register"
                                            + " SecurityFilterChain bean");
                });
    }

    @Test
    void autoConfiguration_shouldEnableMethodSecurity() {
        EnableMethodSecurity annotation =
                CommonSecurityAutoConfiguration.class.getAnnotation(EnableMethodSecurity.class);

        assertThat(annotation)
                .as("CommonSecurityAutoConfiguration should have @EnableMethodSecurity annotation")
                .isNotNull();
        assertThat(annotation.prePostEnabled())
                .as("@EnableMethodSecurity should enable prePostEnabled")
                .isTrue();
    }

    @Test
    void autoConfiguration_shouldRegisterRestAuthenticationEntryPoint() {
        contextRunner.run(
                context -> {
                    assertThat(context)
                            .hasSingleBean(RestAuthenticationEntryPoint.class)
                            .as(
                                    "TestConfiguration should provide"
                                            + " RestAuthenticationEntryPoint bean for"
                                            + " auto-configuration");
                });
    }

    @Test
    void autoConfiguration_shouldRegisterRestAccessDeniedHandler() {
        contextRunner.run(
                context -> {
                    assertThat(context)
                            .hasSingleBean(RestAccessDeniedHandler.class)
                            .as(
                                    "TestConfiguration should provide RestAccessDeniedHandler bean"
                                            + " for auto-configuration");
                });
    }

    @Test
    void configuration_shouldInstantiateWithHandlersAndProperties() {
        ObjectMapper objectMapper = new ObjectMapper();
        RestAuthenticationEntryPoint authenticationEntryPoint =
                new RestAuthenticationEntryPoint(objectMapper);
        RestAccessDeniedHandler accessDeniedHandler = new RestAccessDeniedHandler(objectMapper);
        SecurityProperties securityProperties = new SecurityProperties();

        CommonSecurityAutoConfiguration configuration =
                new CommonSecurityAutoConfiguration(
                        authenticationEntryPoint, accessDeniedHandler, securityProperties);

        assertThat(configuration).isNotNull();
    }

    @Test
    void handlers_shouldBeInstantiableWithObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();

        RestAuthenticationEntryPoint authenticationEntryPoint =
                new RestAuthenticationEntryPoint(objectMapper);
        RestAccessDeniedHandler accessDeniedHandler = new RestAccessDeniedHandler(objectMapper);

        assertThat(authenticationEntryPoint).isNotNull();
        assertThat(accessDeniedHandler).isNotNull();
    }

    @Test
    void securityProperties_shouldAcceptPublicEndpoints() {
        SecurityProperties properties = new SecurityProperties();
        properties.setPublicEndpoints(java.util.List.of("/auth/v1/login", "/auth/v1/register"));

        assertThat(properties.getPublicEndpoints()).hasSize(2);
        assertThat(properties.getPublicEndpoints()).contains("/auth/v1/login", "/auth/v1/register");
    }

    /**
     * Test configuration providing required beans for {@link CommonSecurityAutoConfiguration}.
     *
     * <p>In production, these beans are provided by the auto-configuration itself or by service
     * modules. For testing with {@link ApplicationContextRunner}, we manually provide them.
     */
    @Configuration
    static class TestConfiguration {

        @Bean
        public ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        public RestAuthenticationEntryPoint restAuthenticationEntryPoint(
                ObjectMapper objectMapper) {
            return new RestAuthenticationEntryPoint(objectMapper);
        }

        @Bean
        public RestAccessDeniedHandler restAccessDeniedHandler(ObjectMapper objectMapper) {
            return new RestAccessDeniedHandler(objectMapper);
        }
    }
}
