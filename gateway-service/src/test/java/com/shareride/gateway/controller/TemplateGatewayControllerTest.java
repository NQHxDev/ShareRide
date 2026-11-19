package com.shareride.gateway.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.protobuf.Message;
import com.shareride.common.grpc.template.v1.GetTemplateRequest;
import com.shareride.common.grpc.template.v1.GetTemplateResponse;
import com.shareride.common.grpc.template.v1.VerifyTemplateRequest;
import com.shareride.common.grpc.template.v1.VerifyTemplateResponse;
import com.shareride.gateway.client.TemplateServiceGrpcClient;
import com.shareride.gateway.serializer.ProtobufMessageSerializer;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.codec.json.JacksonJsonDecoder;
import org.springframework.http.codec.json.JacksonJsonEncoder;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

/**
 * Unit tests for TemplateGatewayController - Phase 3 User Story 1 (T018). Tests HTTP-to-gRPC
 * translation logic for template operations.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TemplateGatewayController Unit Tests")
class TemplateGatewayControllerTest {

    @Mock private TemplateServiceGrpcClient templateServiceGrpcClient;

    @InjectMocks private TemplateGatewayController templateGatewayController;

    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        SimpleModule protobufModule = new SimpleModule("ProtobufMessageModule");
        protobufModule.addSerializer(Message.class, new ProtobufMessageSerializer());
        JsonMapper jsonMapper = JsonMapper.builder().addModule(protobufModule).build();

        webTestClient =
                WebTestClient.bindToController(templateGatewayController)
                        .httpMessageCodecs(
                                configurer -> {
                                    configurer
                                            .defaultCodecs()
                                            .jacksonJsonEncoder(new JacksonJsonEncoder(jsonMapper));
                                    configurer
                                            .defaultCodecs()
                                            .jacksonJsonDecoder(new JacksonJsonDecoder(jsonMapper));
                                })
                        .build();
    }

    @Test
    @DisplayName("GET /api/v1/templates/{id} should return template details")
    void testGetTemplate_shouldReturn200() {
        GetTemplateResponse grpcResponse =
                GetTemplateResponse.newBuilder()
                        .setTemplateId("template-123")
                        .setName("Welcome Email")
                        .setContent("Hello {{name}}")
                        .setVersion("1.0.0")
                        .setCreatedAt("2026-11-18T00:00:00Z")
                        .build();

        when(templateServiceGrpcClient.getTemplate(any(GetTemplateRequest.class)))
                .thenReturn(Mono.just(grpcResponse));

        webTestClient
                .get()
                .uri("/api/v1/templates/template-123?locale=vi-VN")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.success")
                .isEqualTo(true)
                .jsonPath("$.data.templateId")
                .isEqualTo("template-123")
                .jsonPath("$.data.name")
                .isEqualTo("Welcome Email");

        verify(templateServiceGrpcClient).getTemplate(any(GetTemplateRequest.class));
    }

    @Test
    @DisplayName("GET /api/v1/templates/{id} with default locale should return 200")
    void testGetTemplate_withDefaultLocale_shouldReturn200() {
        GetTemplateResponse grpcResponse =
                GetTemplateResponse.newBuilder()
                        .setTemplateId("template-123")
                        .setName("Welcome Email")
                        .setContent("Hello {{name}}")
                        .setVersion("1.0.0")
                        .setCreatedAt("2026-11-18T00:00:00Z")
                        .build();

        when(templateServiceGrpcClient.getTemplate(any(GetTemplateRequest.class)))
                .thenReturn(Mono.just(grpcResponse));

        webTestClient
                .get()
                .uri("/api/v1/templates/template-123")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.success")
                .isEqualTo(true)
                .jsonPath("$.data.templateId")
                .isEqualTo("template-123");

        verify(templateServiceGrpcClient).getTemplate(any(GetTemplateRequest.class));
    }

    @Test
    @DisplayName("POST /api/v1/templates/{id}/verify should verify template")
    void testVerifyTemplate_shouldReturn200() {
        VerifyTemplateResponse grpcResponse =
                VerifyTemplateResponse.newBuilder()
                        .setIsValid(true)
                        .setStatus("VALID")
                        .setMessage("Template checksum matches")
                        .build();

        when(templateServiceGrpcClient.verifyTemplate(any(VerifyTemplateRequest.class)))
                .thenReturn(Mono.just(grpcResponse));

        Map<String, String> verifyRequest = Map.of("checksum", "checksum-abc");

        webTestClient
                .post()
                .uri("/api/v1/templates/template-123/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(verifyRequest)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.success")
                .isEqualTo(true)
                .jsonPath("$.data.isValid")
                .isEqualTo(true)
                .jsonPath("$.data.status")
                .isEqualTo("VALID");

        verify(templateServiceGrpcClient).verifyTemplate(any(VerifyTemplateRequest.class));
    }
}
