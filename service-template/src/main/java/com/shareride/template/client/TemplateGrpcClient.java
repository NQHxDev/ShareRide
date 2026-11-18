package com.shareride.template.client;

import com.shareride.common.communication.grpc.client.GrpcChannelFactory;
import com.shareride.common.grpc.template.v1.GetTemplateRequest;
import com.shareride.common.grpc.template.v1.GetTemplateResponse;
import com.shareride.common.grpc.template.v1.TemplateHealthCheckRequest;
import com.shareride.common.grpc.template.v1.TemplateHealthCheckResponse;
import com.shareride.common.grpc.template.v1.TemplateServiceGrpc;
import com.shareride.common.grpc.template.v1.VerifyTemplateRequest;
import com.shareride.common.grpc.template.v1.VerifyTemplateResponse;
import io.grpc.Channel;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Reference gRPC client implementation for template-service demonstrating best practices for
 * calling downstream gRPC services.
 *
 * <p>This client provides a canonical blueprint for future microservices, showcasing:
 *
 * <ul>
 *   <li>Channel creation via {@link GrpcChannelFactory} with connection pooling
 *   <li>Deadline configuration for timeout enforcement
 *   <li>Blocking stub usage for synchronous RPC calls
 * </ul>
 *
 * <p>Per T042 (Phase 6 User Story 4).
 */
@Slf4j
@Component
public class TemplateGrpcClient {

    private final TemplateServiceGrpc.TemplateServiceBlockingStub blockingStub;
    private final long defaultDeadlineMs;

    public TemplateGrpcClient(
            GrpcChannelFactory channelFactory,
            @Value("${shareride.grpc.client.template-service.host:localhost}") String host,
            @Value("${shareride.grpc.client.template-service.port:9082}") int port,
            @Value("${shareride.grpc.client.default-deadline-ms:5000}") long defaultDeadlineMs) {
        Channel channel = channelFactory.getChannel(host, port);
        this.blockingStub = TemplateServiceGrpc.newBlockingStub(channel);
        this.defaultDeadlineMs = defaultDeadlineMs;
        log.info(
                "TemplateGrpcClient initialized for target {}:{} with deadline {}ms",
                host,
                port,
                defaultDeadlineMs);
    }

    /**
     * Retrieves a template by ID with optional locale.
     *
     * @param templateId the template UUID
     * @param locale optional locale (e.g., "vi", "en")
     * @return GetTemplateResponse containing template details
     */
    public GetTemplateResponse getTemplate(String templateId, String locale) {
        log.debug("Calling gRPC GetTemplate for templateId: {}, locale: {}", templateId, locale);

        GetTemplateRequest request =
                GetTemplateRequest.newBuilder()
                        .setTemplateId(templateId)
                        .setLocale(locale != null ? locale : "")
                        .build();

        try {
            GetTemplateResponse response =
                    blockingStub
                            .withDeadlineAfter(defaultDeadlineMs, TimeUnit.MILLISECONDS)
                            .getTemplate(request);

            log.info("gRPC GetTemplate succeeded for templateId: {}", templateId);
            return response;
        } catch (Exception e) {
            log.error("gRPC GetTemplate failed for templateId: {}", templateId, e);
            throw e;
        }
    }

    /**
     * Verifies a template's checksum.
     *
     * @param templateId the template UUID
     * @param checksum the expected SHA-256 checksum
     * @return VerifyTemplateResponse indicating validity
     */
    public VerifyTemplateResponse verifyTemplate(String templateId, String checksum) {
        log.debug(
                "Calling gRPC VerifyTemplate for templateId: {}, checksum: {}",
                templateId,
                checksum);

        VerifyTemplateRequest request =
                VerifyTemplateRequest.newBuilder()
                        .setTemplateId(templateId)
                        .setChecksum(checksum)
                        .build();

        try {
            VerifyTemplateResponse response =
                    blockingStub
                            .withDeadlineAfter(defaultDeadlineMs, TimeUnit.MILLISECONDS)
                            .verifyTemplate(request);

            log.info(
                    "gRPC VerifyTemplate succeeded for templateId: {}, isValid: {}",
                    templateId,
                    response.getIsValid());
            return response;
        } catch (Exception e) {
            log.error("gRPC VerifyTemplate failed for templateId: {}", templateId, e);
            throw e;
        }
    }

    /**
     * Performs a health check on the template service.
     *
     * @return TemplateHealthCheckResponse with service status and uptime
     */
    public TemplateHealthCheckResponse healthCheck() {
        log.debug("Calling gRPC HealthCheck");

        TemplateHealthCheckRequest request = TemplateHealthCheckRequest.newBuilder().build();

        try {
            TemplateHealthCheckResponse response =
                    blockingStub
                            .withDeadlineAfter(defaultDeadlineMs, TimeUnit.MILLISECONDS)
                            .healthCheck(request);

            log.debug("gRPC HealthCheck succeeded, status: {}", response.getStatus());
            return response;
        } catch (Exception e) {
            log.error("gRPC HealthCheck failed", e);
            throw e;
        }
    }
}
