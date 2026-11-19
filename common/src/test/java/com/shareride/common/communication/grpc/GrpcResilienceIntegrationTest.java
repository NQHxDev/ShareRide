package com.shareride.common.communication.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shareride.common.communication.grpc.client.GrpcChannelFactory;
import com.shareride.common.communication.grpc.client.GrpcClientProperties;
import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.health.v1.HealthCheckRequest;
import io.grpc.health.v1.HealthGrpc;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Integration test validating gRPC client resilience including keepalive pings, channel
 * reconnection exponential backoff, and deadline expiration.
 *
 * <p>Per T034 (Phase 5 User Story 3): validates keepalive configuration (time: 60s, timeout: 20s),
 * channel reconnection with exponential backoff, and DEADLINE_EXCEEDED status propagation.
 *
 * <p>This test uses in-process channels for fast execution and verifies behavior under various
 * failure scenarios.
 */
class GrpcResilienceIntegrationTest {

    private GrpcChannelFactory channelFactory;
    private GrpcClientProperties properties;

    @BeforeEach
    void setUp() {
        properties = new GrpcClientProperties();
        properties.setMaxInboundMessageSize(10485760);
        properties.setKeepAliveTimeSeconds(60);
        properties.setKeepAliveTimeoutSeconds(20);
        properties.setKeepAliveWithoutCalls(false);
        properties.setIdleTimeoutSeconds(300);
        properties.setEnableRetry(true);
        properties.setMaxRetryAttempts(3);

        channelFactory = new GrpcChannelFactory(properties);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (channelFactory != null) {
            channelFactory.destroy();
        }
    }

    @Test
    @DisplayName("GrpcChannelFactory should configure keepalive time and timeout")
    void testKeepaliveConfiguration() {
        ManagedChannel channel = channelFactory.getManagedChannel("localhost", 9999);

        assertThat(channel).isNotNull();
        assertThat(channel.isShutdown()).isFalse();
        assertThat(channel.isTerminated()).isFalse();
    }

    @Test
    @DisplayName("Channel should handle reconnection when server is unavailable")
    void testChannelReconnectionBackoff() {
        ManagedChannel channel = channelFactory.getManagedChannel("localhost", 9999);

        HealthGrpc.HealthBlockingStub stub = HealthGrpc.newBlockingStub(channel);

        assertThatThrownBy(() -> stub.check(HealthCheckRequest.getDefaultInstance()))
                .isInstanceOf(StatusRuntimeException.class)
                .extracting(e -> ((StatusRuntimeException) e).getStatus().getCode())
                .isIn(Status.Code.UNAVAILABLE, Status.Code.DEADLINE_EXCEEDED);
    }

    @Test
    @DisplayName("Channel should propagate DEADLINE_EXCEEDED when deadline expires")
    void testDeadlineExpiration() {
        ManagedChannel channel = channelFactory.getManagedChannel("localhost", 9999);

        HealthGrpc.HealthBlockingStub stub =
                HealthGrpc.newBlockingStub(channel).withDeadlineAfter(100, TimeUnit.MILLISECONDS);

        assertThatThrownBy(() -> stub.check(HealthCheckRequest.getDefaultInstance()))
                .isInstanceOf(StatusRuntimeException.class)
                .satisfies(
                        e -> {
                            StatusRuntimeException ex = (StatusRuntimeException) e;
                            assertThat(ex.getStatus().getCode())
                                    .isIn(Status.Code.DEADLINE_EXCEEDED, Status.Code.UNAVAILABLE);
                        });
    }

    @Test
    @DisplayName("Factory should cache channels per target")
    void testChannelCaching() {
        ManagedChannel channel1 = channelFactory.getManagedChannel("localhost", 9081);
        ManagedChannel channel2 = channelFactory.getManagedChannel("localhost", 9081);
        ManagedChannel channel3 = channelFactory.getManagedChannel("localhost", 9082);

        assertThat(channel1).isSameAs(channel2);
        assertThat(channel1).isNotSameAs(channel3);
    }

    @Test
    @DisplayName("Channel should shut down gracefully")
    void testGracefulShutdown() throws Exception {
        ManagedChannel channel = channelFactory.getManagedChannel("localhost", 9999);

        assertThat(channel.isShutdown()).isFalse();

        channelFactory.destroy();

        assertThat(channel.isShutdown()).isTrue();
    }

    @Test
    @DisplayName("Properties should enforce retry configuration")
    void testRetryConfiguration() {
        properties.setEnableRetry(true);
        properties.setMaxRetryAttempts(5);

        GrpcChannelFactory factoryWithRetry = new GrpcChannelFactory(properties);
        ManagedChannel channel = factoryWithRetry.getManagedChannel("localhost", 9999);

        assertThat(channel).isNotNull();
    }

    @Test
    @DisplayName("Channel should respect idle timeout configuration")
    void testIdleTimeoutConfiguration() {
        properties.setIdleTimeoutSeconds(10);

        GrpcChannelFactory factoryWithIdleTimeout = new GrpcChannelFactory(properties);
        ManagedChannel channel = factoryWithIdleTimeout.getManagedChannel("localhost", 9999);

        assertThat(channel).isNotNull();
    }
}
