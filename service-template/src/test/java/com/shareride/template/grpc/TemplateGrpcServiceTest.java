package com.shareride.template.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shareride.common.grpc.template.v1.GetTemplateRequest;
import com.shareride.common.grpc.template.v1.GetTemplateResponse;
import com.shareride.common.grpc.template.v1.TemplateHealthCheckRequest;
import com.shareride.common.grpc.template.v1.TemplateHealthCheckResponse;
import com.shareride.common.grpc.template.v1.VerifyTemplateRequest;
import com.shareride.common.grpc.template.v1.VerifyTemplateResponse;
import com.shareride.common.util.UuidUtils;
import com.shareride.template.dto.TemplateItemResponse;
import com.shareride.template.service.TemplateService;
import io.grpc.stub.StreamObserver;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for TemplateGrpcService verifying all 3 RPC handlers with mock domain services. Per
 * T038 (Phase 6 User Story 4).
 */
@ExtendWith(MockitoExtension.class)
class TemplateGrpcServiceTest {

    @Mock private TemplateService templateService;

    @Mock private StreamObserver<GetTemplateResponse> getTemplateResponseObserver;

    @Mock private StreamObserver<VerifyTemplateResponse> verifyTemplateResponseObserver;

    @Mock private StreamObserver<TemplateHealthCheckResponse> healthCheckResponseObserver;

    @Captor private ArgumentCaptor<GetTemplateResponse> getTemplateResponseCaptor;

    @Captor private ArgumentCaptor<VerifyTemplateResponse> verifyTemplateResponseCaptor;

    @Captor private ArgumentCaptor<TemplateHealthCheckResponse> healthCheckResponseCaptor;

    private TemplateGrpcService templateGrpcService;

    @BeforeEach
    void setUp() {
        templateGrpcService = new TemplateGrpcService(templateService);
    }

    @Test
    @DisplayName("GetTemplate: should retrieve template successfully")
    void testGetTemplate() {
        UUID templateId = UuidUtils.v7();
        TemplateItemResponse mockResponse = new TemplateItemResponse();
        mockResponse.setId(templateId);
        mockResponse.setName("Test Template");
        mockResponse.setDescription("Template content");
        mockResponse.setVersion(1L);
        mockResponse.setCreatedAt(Instant.now());

        when(templateService.getItemById(templateId)).thenReturn(mockResponse);

        GetTemplateRequest request =
                GetTemplateRequest.newBuilder()
                        .setTemplateId(templateId.toString())
                        .setLocale("en")
                        .build();

        templateGrpcService.getTemplate(request, getTemplateResponseObserver);

        verify(getTemplateResponseObserver).onNext(getTemplateResponseCaptor.capture());
        verify(getTemplateResponseObserver).onCompleted();

        GetTemplateResponse response = getTemplateResponseCaptor.getValue();
        assertThat(response.getTemplateId()).isEqualTo(templateId.toString());
        assertThat(response.getName()).isEqualTo("Test Template");
        assertThat(response.getContent()).isEqualTo("Template content");
        assertThat(response.getVersion()).isEqualTo("1");
    }

    @Test
    @DisplayName("GetTemplate: should handle error when template not found")
    void testGetTemplateNotFound() {
        UUID templateId = UuidUtils.v7();

        when(templateService.getItemById(templateId))
                .thenThrow(new RuntimeException("Template not found"));

        GetTemplateRequest request =
                GetTemplateRequest.newBuilder().setTemplateId(templateId.toString()).build();

        templateGrpcService.getTemplate(request, getTemplateResponseObserver);

        verify(getTemplateResponseObserver).onError(any(Exception.class));
    }

    @Test
    @DisplayName("VerifyTemplate: should verify template checksum successfully")
    void testVerifyTemplateValid() {
        UUID templateId = UuidUtils.v7();
        String content = "Test content";

        // Calculate the actual SHA-256 checksum for "Test content"
        String expectedChecksum = calculateTestChecksum(content);

        TemplateItemResponse mockResponse = new TemplateItemResponse();
        mockResponse.setId(templateId);
        mockResponse.setDescription(content);

        when(templateService.getItemById(templateId)).thenReturn(mockResponse);

        VerifyTemplateRequest request =
                VerifyTemplateRequest.newBuilder()
                        .setTemplateId(templateId.toString())
                        .setChecksum(expectedChecksum)
                        .build();

        templateGrpcService.verifyTemplate(request, verifyTemplateResponseObserver);

        verify(verifyTemplateResponseObserver).onNext(verifyTemplateResponseCaptor.capture());
        verify(verifyTemplateResponseObserver).onCompleted();

        VerifyTemplateResponse response = verifyTemplateResponseCaptor.getValue();
        assertThat(response.getIsValid()).isTrue();
        assertThat(response.getStatus()).isEqualTo("VALID");
    }

    private String calculateTestChecksum(String content) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte b : hash) {
                result.append(String.format("%02x", b));
            }
            return result.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("VerifyTemplate: should detect invalid checksum")
    void testVerifyTemplateInvalid() {
        UUID templateId = UuidUtils.v7();
        String content = "Test content";
        String wrongChecksum = "wrongchecksum";

        TemplateItemResponse mockResponse = new TemplateItemResponse();
        mockResponse.setId(templateId);
        mockResponse.setDescription(content);

        when(templateService.getItemById(templateId)).thenReturn(mockResponse);

        VerifyTemplateRequest request =
                VerifyTemplateRequest.newBuilder()
                        .setTemplateId(templateId.toString())
                        .setChecksum(wrongChecksum)
                        .build();

        templateGrpcService.verifyTemplate(request, verifyTemplateResponseObserver);

        verify(verifyTemplateResponseObserver).onNext(verifyTemplateResponseCaptor.capture());
        verify(verifyTemplateResponseObserver).onCompleted();

        VerifyTemplateResponse response = verifyTemplateResponseCaptor.getValue();
        assertThat(response.getIsValid()).isFalse();
        assertThat(response.getStatus()).isEqualTo("INVALID");
    }

    @Test
    @DisplayName("HealthCheck: should return serving status with uptime")
    void testHealthCheck() {
        TemplateHealthCheckRequest request = TemplateHealthCheckRequest.newBuilder().build();

        templateGrpcService.healthCheck(request, healthCheckResponseObserver);

        verify(healthCheckResponseObserver).onNext(healthCheckResponseCaptor.capture());
        verify(healthCheckResponseObserver).onCompleted();

        TemplateHealthCheckResponse response = healthCheckResponseCaptor.getValue();
        assertThat(response.getStatus()).isEqualTo("SERVING");
        assertThat(response.getUptimeSeconds()).isGreaterThanOrEqualTo(0);
    }
}
