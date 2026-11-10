package com.shareride.common.openapi;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Standardized Auto-Configuration for OpenAPI 3.0 / Swagger documentation across ShareRide
 * Servlet-based microservices.
 *
 * <p>Enforces enterprise API governance:
 *
 * <ul>
 *   <li>Living code-first documentation (OAS 3.0)
 *   <li>Asymmetric EdDSA (Ed25519) Bearer JWT security scheme definition
 *   <li>Distributed tracing correlation identifier (X-Correlation-Id) header
 *   <li>Relative default server URI ("/") for seamless Gateway aggregation without CORS issues
 * </ul>
 */
@AutoConfiguration
@ConditionalOnClass(OpenAPI.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(
        name = "shareride.openapi.enabled",
        havingValue = "true",
        matchIfMissing = true)
@EnableConfigurationProperties(OpenApiProperties.class)
public class OpenApiAutoConfiguration {

    public static final String SECURITY_SCHEME_BEARER = "bearerAuth";
    public static final String SECURITY_SCHEME_CORRELATION_ID = "correlationId";

    @Bean
    @ConditionalOnMissingBean
    public OpenAPI customOpenAPI(OpenApiProperties properties) {
        return new OpenAPI()
                .info(
                        new Info()
                                .title(properties.getTitle())
                                .description(properties.getDescription())
                                .version(properties.getVersion())
                                .contact(
                                        new Contact()
                                                .name(properties.getContactName())
                                                .email(properties.getContactEmail()))
                                .license(
                                        new License()
                                                .name(properties.getLicenseName())
                                                .url(properties.getLicenseUrl()))
                                .termsOfService(properties.getTermsOfService()))
                .servers(
                        List.of(
                                new Server()
                                        .url("/")
                                        .description("Current Host / Gateway Reverse Proxy")))
                .components(
                        new Components()
                                .addSecuritySchemes(
                                        SECURITY_SCHEME_BEARER,
                                        new SecurityScheme()
                                                .name(SECURITY_SCHEME_BEARER)
                                                .type(SecurityScheme.Type.HTTP)
                                                .scheme("bearer")
                                                .bearerFormat("JWT")
                                                .description(
                                                        "Asymmetric EdDSA (Ed25519) cryptographic"
                                                                + " JWT access token (RFC 7519)"))
                                .addSecuritySchemes(
                                        SECURITY_SCHEME_CORRELATION_ID,
                                        new SecurityScheme()
                                                .name("X-Correlation-Id")
                                                .type(SecurityScheme.Type.APIKEY)
                                                .in(SecurityScheme.In.HEADER)
                                                .description(
                                                        "Distributed tracing correlation identifier"
                                                                + " (RFC 4122 / UUIDv7)")))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_BEARER))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_CORRELATION_ID));
    }
}
