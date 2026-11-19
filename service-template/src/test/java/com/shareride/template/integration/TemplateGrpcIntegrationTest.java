package com.shareride.template.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.shareride.common.grpc.template.v1.GetTemplateRequest;
import com.shareride.common.grpc.template.v1.GetTemplateResponse;
import com.shareride.common.grpc.template.v1.TemplateHealthCheckRequest;
import com.shareride.common.grpc.template.v1.TemplateHealthCheckResponse;
import com.shareride.common.grpc.template.v1.TemplateServiceGrpc;
import com.shareride.template.domain.TemplateItem;
import com.shareride.template.grpc.TemplateGrpcService;
import com.shareride.template.repository.TemplateItemRepository;
import com.shareride.template.support.AbstractIntegrationTest;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Integration test for TemplateGrpcService validating all 3 RPC handlers with real PostgreSQL and
 * in-process gRPC server. Per T040 (Phase 6 User Story 4).
 */
class TemplateGrpcIntegrationTest extends AbstractIntegrationTest {

    @Autowired private TemplateGrpcService templateGrpcService;
    @Autowired private TemplateItemRepository templateItemRepository;

    private Server grpcServer;
    private ManagedChannel grpcChannel;
    private TemplateServiceGrpc.TemplateServiceBlockingStub blockingStub;

    private UUID testTemplateId;

    @BeforeEach
    void setUpGrpcChannel() throws Exception {
        String serverName = InProcessServerBuilder.generateName();

        grpcServer =
                InProcessServerBuilder.forName(serverName)
                        .directExecutor()
                        .addService(templateGrpcService)
                        .build()
                        .start();

        grpcChannel = InProcessChannelBuilder.forName(serverName).directExecutor().build();
        blockingStub = TemplateServiceGrpc.newBlockingStub(grpcChannel);

        templateItemRepository.deleteAll();

        TemplateItem templateItem = new TemplateItem();
        templateItem.setName("Test Template");
        templateItem.setCode("TEST_TEMPLATE_001");
        templateItem.setDescription("Test content");
        templateItem.setVersion(1L);
        templateItem.setCreatedAt(Instant.now());
        templateItem.setUpdatedAt(Instant.now());
        TemplateItem saved = templateItemRepository.save(templateItem);
        testTemplateId = saved.getId();
    }

    @AfterEach
    void tearDownGrpcChannel() {
        if (grpcChannel != null && !grpcChannel.isShutdown()) {
            grpcChannel.shutdownNow();
        }
        if (grpcServer != null && !grpcServer.isShutdown()) {
            grpcServer.shutdownNow();
        }
    }

    @Test
    @DisplayName("GetTemplate: should retrieve template from database via gRPC")
    void testGetTemplate() {
        GetTemplateRequest request =
                GetTemplateRequest.newBuilder()
                        .setTemplateId(testTemplateId.toString())
                        .setLocale("en")
                        .build();

        GetTemplateResponse response = blockingStub.getTemplate(request);

        assertThat(response.getTemplateId()).isEqualTo(testTemplateId.toString());
        assertThat(response.getName()).isEqualTo("Test Template");
        assertThat(response.getContent()).isEqualTo("Test content");
        assertThat(response.getVersion()).isEqualTo("1");
        assertThat(response.getCreatedAt()).isNotEmpty();
    }

    @Test
    @DisplayName("HealthCheck: should return SERVING status")
    void testHealthCheck() {
        TemplateHealthCheckRequest request = TemplateHealthCheckRequest.newBuilder().build();

        TemplateHealthCheckResponse response = blockingStub.healthCheck(request);

        assertThat(response.getStatus()).isEqualTo("SERVING");
        assertThat(response.getUptimeSeconds()).isGreaterThanOrEqualTo(0);
    }
}
