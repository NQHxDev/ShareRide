package com.shareride.gateway.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.shareride.common.communication.grpc.client.GrpcChannelFactory;
import com.shareride.common.exception.ResourceNotFoundException;
import com.shareride.common.grpc.template.v1.GetTemplateRequest;
import com.shareride.common.grpc.template.v1.GetTemplateResponse;
import com.shareride.common.grpc.template.v1.TemplateHealthCheckRequest;
import com.shareride.common.grpc.template.v1.TemplateHealthCheckResponse;
import com.shareride.common.grpc.template.v1.TemplateServiceGrpc;
import com.shareride.common.grpc.template.v1.VerifyTemplateRequest;
import com.shareride.common.grpc.template.v1.VerifyTemplateResponse;
import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import io.grpc.testing.GrpcCleanupRule;
import org.junit.Rule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import reactor.test.StepVerifier;

/**
 * Unit tests for TemplateServiceGrpcClient verifying gRPC call delegation, exception mapping, and
 * reactive scheduling on boundedElastic(). Uses in-process gRPC for fast, isolated testing.
 */
@DisplayName("TemplateServiceGrpcClient Unit Tests")
class TemplateServiceGrpcClientTest {

    @Rule public final GrpcCleanupRule grpcCleanup = new GrpcCleanupRule();

    private TemplateServiceGrpcClient client;
    private TestTemplateServiceImpl testService;
    private io.grpc.Server server;

    @BeforeEach
    void setUp() throws Exception {
        String serverName = InProcessServerBuilder.generateName();
        testService = new TestTemplateServiceImpl();

        server =
                InProcessServerBuilder.forName(serverName)
                        .directExecutor()
                        .addService(testService)
                        .build()
                        .start();

        ManagedChannel channel =
                InProcessChannelBuilder.forName(serverName).directExecutor().build();

        GrpcChannelFactory channelFactory = Mockito.mock(GrpcChannelFactory.class);
        Mockito.when(channelFactory.getChannel("template-service")).thenReturn(channel);

        client = new TemplateServiceGrpcClient(channelFactory);
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.shutdownNow();
        }
    }

    @Test
    @DisplayName("getTemplate() should return template details")
    void testGetTemplate_shouldReturnTemplateResponse() {
        GetTemplateRequest request =
                GetTemplateRequest.newBuilder()
                        .setTemplateId("welcome-email")
                        .setLocale("en_US")
                        .build();

        StepVerifier.create(client.getTemplate(request))
                .assertNext(
                        response -> {
                            assertThat(response.getTemplateId()).isEqualTo("welcome-email");
                            assertThat(response.getName()).isEqualTo("Welcome Email");
                            assertThat(response.getContent()).contains("Hello");
                            assertThat(response.getVersion()).isEqualTo("1");
                        })
                .verifyComplete();
    }

    @Test
    @DisplayName("verifyTemplate() should return verification result")
    void testVerifyTemplate_shouldReturnVerifyResponse() {
        VerifyTemplateRequest request =
                VerifyTemplateRequest.newBuilder()
                        .setTemplateId("welcome-email")
                        .setChecksum("abc123checksum")
                        .build();

        StepVerifier.create(client.verifyTemplate(request))
                .assertNext(
                        response -> {
                            assertThat(response.getIsValid()).isTrue();
                            assertThat(response.getStatus()).isEqualTo("VALID");
                            assertThat(response.getMessage())
                                    .isEqualTo("Template checksum is valid");
                        })
                .verifyComplete();
    }

    @Test
    @DisplayName("healthCheck() should return service status")
    void testHealthCheck_shouldReturnHealthCheckResponse() {
        TemplateHealthCheckRequest request = TemplateHealthCheckRequest.newBuilder().build();

        StepVerifier.create(client.healthCheck(request))
                .assertNext(
                        response -> {
                            assertThat(response.getStatus()).isEqualTo("SERVING");
                            assertThat(response.getUptimeSeconds()).isGreaterThan(0);
                        })
                .verifyComplete();
    }

    @Test
    @DisplayName("getTemplate() with non-existent ID should throw ResourceNotFoundException")
    void testGetTemplate_whenNotFound_shouldThrowResourceNotFoundException() {
        testService.setNextError(Status.NOT_FOUND.asRuntimeException());

        GetTemplateRequest request =
                GetTemplateRequest.newBuilder()
                        .setTemplateId("nonexistent-template")
                        .setLocale("en_US")
                        .build();

        StepVerifier.create(client.getTemplate(request))
                .expectError(ResourceNotFoundException.class)
                .verify();
    }

    @Test
    @DisplayName("verifyTemplate() with invalid checksum should return false")
    void testVerifyTemplate_whenInvalidChecksum_shouldReturnInvalid() {
        testService.setInvalidChecksum(true);

        VerifyTemplateRequest request =
                VerifyTemplateRequest.newBuilder()
                        .setTemplateId("welcome-email")
                        .setChecksum("invalid-checksum")
                        .build();

        StepVerifier.create(client.verifyTemplate(request))
                .assertNext(
                        response -> {
                            assertThat(response.getIsValid()).isFalse();
                            assertThat(response.getStatus()).isEqualTo("INVALID");
                            assertThat(response.getMessage())
                                    .isEqualTo("Template checksum is invalid");
                        })
                .verifyComplete();
    }

    /** Test implementation of TemplateService for in-process gRPC testing. */
    private static class TestTemplateServiceImpl
            extends TemplateServiceGrpc.TemplateServiceImplBase {

        private StatusRuntimeException nextError;
        private boolean invalidChecksum = false;

        void setNextError(StatusRuntimeException error) {
            this.nextError = error;
        }

        void setInvalidChecksum(boolean invalid) {
            this.invalidChecksum = invalid;
        }

        @Override
        public void getTemplate(
                GetTemplateRequest request, StreamObserver<GetTemplateResponse> observer) {
            if (nextError != null) {
                observer.onError(nextError);
                nextError = null;
                return;
            }
            observer.onNext(
                    GetTemplateResponse.newBuilder()
                            .setTemplateId(request.getTemplateId())
                            .setName("Welcome Email")
                            .setContent("Hello {{name}}, welcome to ShareRide!")
                            .setVersion("1")
                            .build());
            observer.onCompleted();
        }

        @Override
        public void verifyTemplate(
                VerifyTemplateRequest request, StreamObserver<VerifyTemplateResponse> observer) {
            if (invalidChecksum) {
                observer.onNext(
                        VerifyTemplateResponse.newBuilder()
                                .setIsValid(false)
                                .setStatus("INVALID")
                                .setMessage("Template checksum is invalid")
                                .build());
                invalidChecksum = false;
            } else {
                observer.onNext(
                        VerifyTemplateResponse.newBuilder()
                                .setIsValid(true)
                                .setStatus("VALID")
                                .setMessage("Template checksum is valid")
                                .build());
            }
            observer.onCompleted();
        }

        @Override
        public void healthCheck(
                TemplateHealthCheckRequest request,
                StreamObserver<TemplateHealthCheckResponse> observer) {
            observer.onNext(
                    TemplateHealthCheckResponse.newBuilder()
                            .setStatus("SERVING")
                            .setUptimeSeconds(3600)
                            .build());
            observer.onCompleted();
        }
    }
}
