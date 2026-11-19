package com.shareride.template.grpc;

import com.shareride.common.communication.grpc.server.GrpcService;
import com.shareride.common.grpc.template.v1.GetTemplateRequest;
import com.shareride.common.grpc.template.v1.GetTemplateResponse;
import com.shareride.common.grpc.template.v1.TemplateHealthCheckRequest;
import com.shareride.common.grpc.template.v1.TemplateHealthCheckResponse;
import com.shareride.common.grpc.template.v1.TemplateServiceGrpc;
import com.shareride.common.grpc.template.v1.VerifyTemplateRequest;
import com.shareride.common.grpc.template.v1.VerifyTemplateResponse;
import io.grpc.stub.StreamObserver;
import java.lang.management.ManagementFactory;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Reference gRPC service implementation for service-template demonstrating best practices for
 * synchronous inter-service communication per Constitution Principle IV.
 *
 * <p>This service provides a canonical blueprint for future microservices created via {@code make
 * new-service}, showcasing:
 *
 * <ul>
 *   <li>gRPC server method implementations with logging and error handling
 *   <li>Integration with domain service layer
 *   <li>Health check implementation for Kubernetes readiness probes
 * </ul>
 *
 * <p>Per T041 (Phase 6 User Story 4).
 */
@Slf4j
@GrpcService
@RequiredArgsConstructor
public class TemplateGrpcService extends TemplateServiceGrpc.TemplateServiceImplBase {

    private final com.shareride.template.service.TemplateService templateService;

    @Override
    public void getTemplate(
            GetTemplateRequest request, StreamObserver<GetTemplateResponse> responseObserver) {
        log.debug(
                "gRPC GetTemplate request received for templateId: {}, locale: {}",
                request.getTemplateId(),
                request.getLocale());

        try {
            com.shareride.template.dto.TemplateItemResponse item =
                    templateService.getItemById(java.util.UUID.fromString(request.getTemplateId()));

            GetTemplateResponse response =
                    GetTemplateResponse.newBuilder()
                            .setTemplateId(item.getId().toString())
                            .setName(item.getName() != null ? item.getName() : "")
                            .setContent(item.getDescription() != null ? item.getDescription() : "")
                            .setVersion(
                                    item.getVersion() != null
                                            ? String.valueOf(item.getVersion())
                                            : "0")
                            .setCreatedAt(
                                    item.getCreatedAt() != null
                                            ? item.getCreatedAt().toString()
                                            : Instant.now().toString())
                            .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

            log.info(
                    "gRPC GetTemplate completed successfully for templateId: {}",
                    request.getTemplateId());
        } catch (Exception e) {
            log.error("gRPC GetTemplate failed for templateId: {}", request.getTemplateId(), e);
            responseObserver.onError(e);
        }
    }

    @Override
    public void verifyTemplate(
            VerifyTemplateRequest request,
            StreamObserver<VerifyTemplateResponse> responseObserver) {
        log.debug(
                "gRPC VerifyTemplate request received for templateId: {}, checksum: {}",
                request.getTemplateId(),
                request.getChecksum());

        try {
            java.util.UUID templateId = java.util.UUID.fromString(request.getTemplateId());
            com.shareride.template.dto.TemplateItemResponse item =
                    templateService.getItemById(templateId);

            String actualChecksum = calculateChecksum(item.getDescription());
            boolean isValid = actualChecksum.equals(request.getChecksum());

            VerifyTemplateResponse response =
                    VerifyTemplateResponse.newBuilder()
                            .setIsValid(isValid)
                            .setStatus(isValid ? "VALID" : "INVALID")
                            .setMessage(
                                    isValid
                                            ? "Template checksum verified successfully"
                                            : "Template checksum mismatch")
                            .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

            log.info(
                    "gRPC VerifyTemplate completed for templateId: {}, isValid: {}",
                    request.getTemplateId(),
                    isValid);
        } catch (Exception e) {
            log.error("gRPC VerifyTemplate failed for templateId: {}", request.getTemplateId(), e);
            responseObserver.onError(e);
        }
    }

    @Override
    public void healthCheck(
            TemplateHealthCheckRequest request,
            StreamObserver<TemplateHealthCheckResponse> responseObserver) {
        log.debug("gRPC HealthCheck request received");

        try {
            long uptimeSeconds = ManagementFactory.getRuntimeMXBean().getUptime() / 1000;

            TemplateHealthCheckResponse response =
                    TemplateHealthCheckResponse.newBuilder()
                            .setStatus("SERVING")
                            .setUptimeSeconds(uptimeSeconds)
                            .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

            log.debug("gRPC HealthCheck completed, uptime: {}s", uptimeSeconds);
        } catch (Exception e) {
            log.error("gRPC HealthCheck failed", e);
            responseObserver.onError(e);
        }
    }

    private String calculateChecksum(String content) {
        if (content == null || content.isEmpty()) {
            return "";
        }
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (Exception e) {
            log.warn("Failed to calculate checksum", e);
            return "";
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }
}
