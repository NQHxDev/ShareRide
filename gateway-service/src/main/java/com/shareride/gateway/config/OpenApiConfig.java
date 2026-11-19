package com.shareride.gateway.config;

import com.shareride.gateway.serializer.ProtobufModelConverter;
import io.swagger.v3.core.converter.ModelConverter;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Standardized OpenAPI 3.0 / Swagger documentation configuration for Reactive API Gateway.
 *
 * <p>Configures Gateway API metadata, asymmetric EdDSA Bearer JWT authentication, and distributed
 * correlation ID header schemas for interactive API exploration.
 */
@Configuration
public class OpenApiConfig {

    public static final String SECURITY_SCHEME_BEARER = "bearerAuth";
    public static final String SECURITY_SCHEME_CORRELATION_ID = "correlationId";

    @Bean
    public ModelConverter protobufModelConverter() {
        return new ProtobufModelConverter();
    }

    @Bean
    public OpenAPI gatewayOpenAPI() {
        return new OpenAPI()
                .info(
                        new Info()
                                .title("ShareRide API Gateway")
                                .description(
                                        "Reactive API Gateway for ShareRide Ecosystem. Provides"
                                                + " routing, rate limiting, JWT verification,"
                                                + " and protocol translation to downstream gRPC"
                                                + " microservices.")
                                .version("v1.0")
                                .contact(
                                        new Contact()
                                                .name("ShareRide Engineering Team")
                                                .email("dev@shareride.com"))
                                .license(
                                        new License()
                                                .name("Apache 2.0")
                                                .url(
                                                        "https://www.apache.org/licenses/LICENSE-2.0")))
                .servers(List.of(new Server().url("/").description("Current API Gateway Host")))
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
                                                                + " (UUIDv7)")))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_BEARER))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_CORRELATION_ID));
    }
}
