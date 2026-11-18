package com.shareride.template.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.shareride.common.communication.grpc.client.GrpcChannelFactory;
import com.shareride.common.grpc.template.v1.GetTemplateRequest;
import com.shareride.common.grpc.template.v1.GetTemplateResponse;
import com.shareride.common.grpc.template.v1.TemplateHealthCheckRequest;
import com.shareride.common.grpc.template.v1.TemplateHealthCheckResponse;
import com.shareride.common.grpc.template.v1.TemplateServiceGrpc;
import com.shareride.common.grpc.template.v1.VerifyTemplateRequest;
import com.shareride.common.grpc.template.v1.VerifyTemplateResponse;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for TemplateGrpcClient verifying gRPC client stub usage with mock server. Per T039
 * (Phase 6 User Story 4).
 */
class TemplateGrpcClientTest {

    private Server server;
    private ManagedChannel channel;
    private TemplateGrpcClient templateGrpcClient;

    @BeforeEach
    void setUp() throws Exception {
        String serverName = InProcessServerBuilder.generateName();

        TemplateServiceGrpc.TemplateServiceImplBase mockService =
                new TemplateServiceGrpc.TemplateServiceImplBase() {
                    @Override
                    public void getTemplate(
                            GetTemplateRequest request,
                            StreamObserver<GetTemplateResponse> responseObserver) {
                        GetTemplateResponse response =
                                GetTemplateResponse.newBuilder()
                                        .setTemplateId(request.getTemplateId())
                                        .setName("Mock Template")
                                        .setContent("Mock content")
                                        .setVersion("1")
                                        .setCreatedAt("2026-11-17T00:00:00Z")
                                        .build();
                        responseObserver.onNext(response);
                        responseObserver.onCompleted();
                    }

                    @Override
                    public void verifyTemplate(
                            VerifyTemplateRequest request,
                            StreamObserver<VerifyTemplateResponse> responseObserver) {
                        VerifyTemplateResponse response =
                                VerifyTemplateResponse.newBuilder()
                                        .setIsValid(true)
                                        .setStatus("VALID")
                                        .setMessage("Checksum verified")
                                        .build();
                        responseObserver.onNext(response);
                        responseObserver.onCompleted();
                    }

                    @Override
                    public void healthCheck(
                            TemplateHealthCheckRequest request,
                            StreamObserver<TemplateHealthCheckResponse> responseObserver) {
                        TemplateHealthCheckResponse response =
                                TemplateHealthCheckResponse.newBuilder()
                                        .setStatus("SERVING")
                                        .setUptimeSeconds(100)
                                        .build();
                        responseObserver.onNext(response);
                        responseObserver.onCompleted();
                    }
                };

        server =
                InProcessServerBuilder.forName(serverName)
                        .directExecutor()
                        .addService(mockService)
                        .build()
                        .start();

        channel = InProcessChannelBuilder.forName(serverName).directExecutor().build();

        GrpcChannelFactory mockChannelFactory = mock(GrpcChannelFactory.class);
        when(mockChannelFactory.getChannel(any(String.class), any(Integer.class)))
                .thenReturn(channel);

        templateGrpcClient = new TemplateGrpcClient(mockChannelFactory, "localhost", 9082, 5000);
    }

    @AfterEach
    void tearDown() {
        if (channel != null && !channel.isShutdown()) {
            channel.shutdownNow();
        }
        if (server != null && !server.isShutdown()) {
            server.shutdownNow();
        }
    }

    @Test
    @DisplayName("GetTemplate: should call downstream service and return response")
    void testGetTemplate() {
        UUID templateId = UUID.randomUUID();

        GetTemplateResponse response = templateGrpcClient.getTemplate(templateId.toString(), "en");

        assertThat(response).isNotNull();
        assertThat(response.getTemplateId()).isEqualTo(templateId.toString());
        assertThat(response.getName()).isEqualTo("Mock Template");
        assertThat(response.getContent()).isEqualTo("Mock content");
        assertThat(response.getVersion()).isEqualTo("1");
    }

    @Test
    @DisplayName("VerifyTemplate: should call downstream service and return validation result")
    void testVerifyTemplate() {
        UUID templateId = UUID.randomUUID();
        String checksum = "abcdef123456";

        VerifyTemplateResponse response =
                templateGrpcClient.verifyTemplate(templateId.toString(), checksum);

        assertThat(response).isNotNull();
        assertThat(response.getIsValid()).isTrue();
        assertThat(response.getStatus()).isEqualTo("VALID");
        assertThat(response.getMessage()).isEqualTo("Checksum verified");
    }

    @Test
    @DisplayName("HealthCheck: should call downstream service and return status")
    void testHealthCheck() {
        TemplateHealthCheckResponse response = templateGrpcClient.healthCheck();

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo("SERVING");
        assertThat(response.getUptimeSeconds()).isEqualTo(100);
    }
}
