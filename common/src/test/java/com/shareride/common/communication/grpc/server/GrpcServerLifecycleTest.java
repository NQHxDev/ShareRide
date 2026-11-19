package com.shareride.common.communication.grpc.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import io.grpc.BindableService;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.ServerServiceDefinition;
import java.net.ServerSocket;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;

@ExtendWith(MockitoExtension.class)
@DisplayName("GrpcServerLifecycle Unit Tests")
class GrpcServerLifecycleTest {

    @Mock private ApplicationContext applicationContext;

    private GrpcServerProperties properties;
    private GrpcServerLifecycle lifecycle;

    @BeforeEach
    void setUp() {
        properties = new GrpcServerProperties();
        lifecycle = new GrpcServerLifecycle(applicationContext, properties);
    }

    @Test
    @DisplayName("Should return early without starting when disabled")
    void testStart_Disabled() {
        properties.setEnabled(false);

        lifecycle.start();

        assertThat(lifecycle.isRunning()).isFalse();
        assertThat(lifecycle.getPort()).isEqualTo(-1);
    }

    @Test
    @DisplayName("Should return early without starting when no @GrpcService beans found")
    void testStart_NoServices() {
        properties.setEnabled(true);
        when(applicationContext.getBeansWithAnnotation(GrpcService.class)).thenReturn(Map.of());

        lifecycle.start();

        assertThat(lifecycle.isRunning()).isFalse();
        assertThat(lifecycle.getPort()).isEqualTo(-1);
    }

    @Test
    @DisplayName(
            "Should start server on dynamic port, register services and interceptors, and stop"
                    + " gracefully")
    void testStartAndStop_Success() {
        properties.setEnabled(true);
        properties.setPort(0); // Dynamic ephemeral port
        properties.setShutdownGracePeriodSeconds(1);

        BindableService dummyService =
                () -> ServerServiceDefinition.builder("DummyService").build();
        when(applicationContext.getBeansWithAnnotation(GrpcService.class))
                .thenReturn(Map.of("dummyService", dummyService));

        // Interceptors with different names to test compareInterceptors ordering
        ServerInterceptor traceInterceptor = new DummyTraceInterceptor();
        ServerInterceptor loggingInterceptor = new DummyLoggingInterceptor();
        ServerInterceptor exceptionInterceptor = new DummyExceptionInterceptor();
        ServerInterceptor otherInterceptor = new DummyOtherInterceptor();

        when(applicationContext.getBeansOfType(ServerInterceptor.class))
                .thenReturn(
                        Map.of(
                                "exception", exceptionInterceptor,
                                "other", otherInterceptor,
                                "trace", traceInterceptor,
                                "logging", loggingInterceptor));

        lifecycle.start();

        assertThat(lifecycle.isRunning()).isTrue();
        assertThat(lifecycle.getPort()).isGreaterThan(0);
        assertThat(lifecycle.getPhase()).isEqualTo(Integer.MAX_VALUE - 100);

        lifecycle.stop();

        assertThat(lifecycle.isRunning()).isFalse();
        assertThat(lifecycle.getPort()).isEqualTo(-1);
    }

    @Test
    @DisplayName("Should throw IllegalStateException when server fails to bind to port")
    void testStart_PortAlreadyBound_ThrowsIllegalStateException() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            int port = socket.getLocalPort();
            properties.setEnabled(true);
            properties.setPort(port);

            BindableService dummyService =
                    () -> ServerServiceDefinition.builder("DummyService").build();
            when(applicationContext.getBeansWithAnnotation(GrpcService.class))
                    .thenReturn(Map.of("dummyService", dummyService));

            assertThatThrownBy(() -> lifecycle.start())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("gRPC server startup failed");
        }
    }

    static class DummyTraceInterceptor implements ServerInterceptor {
        @Override
        public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
                ServerCall<ReqT, RespT> call,
                Metadata headers,
                ServerCallHandler<ReqT, RespT> next) {
            return next.startCall(call, headers);
        }
    }

    static class DummyLoggingInterceptor implements ServerInterceptor {
        @Override
        public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
                ServerCall<ReqT, RespT> call,
                Metadata headers,
                ServerCallHandler<ReqT, RespT> next) {
            return next.startCall(call, headers);
        }
    }

    static class DummyExceptionInterceptor implements ServerInterceptor {
        @Override
        public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
                ServerCall<ReqT, RespT> call,
                Metadata headers,
                ServerCallHandler<ReqT, RespT> next) {
            return next.startCall(call, headers);
        }
    }

    static class DummyOtherInterceptor implements ServerInterceptor {
        @Override
        public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
                ServerCall<ReqT, RespT> call,
                Metadata headers,
                ServerCallHandler<ReqT, RespT> next) {
            return next.startCall(call, headers);
        }
    }
}
