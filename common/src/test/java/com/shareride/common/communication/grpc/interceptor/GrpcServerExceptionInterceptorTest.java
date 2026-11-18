package com.shareride.common.communication.grpc.interceptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.Status;
import java.io.InputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GrpcServerExceptionInterceptorTest {

    @Mock private ServerCall<Object, Object> mockServerCall;

    @Mock private ServerCallHandler<Object, Object> mockServerCallHandler;

    @Mock private ServerCall.Listener<Object> mockServerCallListener;

    private io.grpc.MethodDescriptor<Object, Object> methodDescriptor;

    private GrpcServerExceptionInterceptor exceptionInterceptor;

    @BeforeEach
    void setUp() {
        exceptionInterceptor = new GrpcServerExceptionInterceptor();

        methodDescriptor =
                io.grpc.MethodDescriptor.<Object, Object>newBuilder()
                        .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
                        .setFullMethodName("TestService/TestMethod")
                        .setRequestMarshaller(
                                new io.grpc.MethodDescriptor.Marshaller<Object>() {
                                    @Override
                                    public InputStream stream(Object value) {
                                        return null;
                                    }

                                    @Override
                                    public Object parse(InputStream stream) {
                                        return null;
                                    }
                                })
                        .setResponseMarshaller(
                                new io.grpc.MethodDescriptor.Marshaller<Object>() {
                                    @Override
                                    public InputStream stream(Object value) {
                                        return null;
                                    }

                                    @Override
                                    public Object parse(InputStream stream) {
                                        return null;
                                    }
                                })
                        .build();
    }

    @Test
    void testExceptionInterceptorCatchesRuntimeException() {
        Metadata metadata = new Metadata();

        when(mockServerCall.getMethodDescriptor()).thenReturn(methodDescriptor);
        when(mockServerCallHandler.startCall(any(), any()))
                .thenThrow(new RuntimeException("Simulated error"));

        ArgumentCaptor<Status> statusCaptor = ArgumentCaptor.forClass(Status.class);

        try {
            exceptionInterceptor.interceptCall(mockServerCall, metadata, mockServerCallHandler);
        } catch (Exception e) {
            // Expected to catch and handle
        }

        verify(mockServerCall).close(statusCaptor.capture(), any(Metadata.class));

        Status capturedStatus = statusCaptor.getValue();
        assertThat(capturedStatus.getCode()).isEqualTo(Status.Code.INTERNAL);
    }

    @Test
    void testExceptionInterceptorPreservesCorrelationIdInTrailers() {
        Metadata metadata = new Metadata();
        metadata.put(
                Metadata.Key.of("x-correlation-id", Metadata.ASCII_STRING_MARSHALLER),
                "test-correlation-123");

        when(mockServerCall.getMethodDescriptor()).thenReturn(methodDescriptor);
        when(mockServerCallHandler.startCall(any(), any()))
                .thenThrow(new RuntimeException("Simulated error"));

        ArgumentCaptor<Metadata> trailersCaptor = ArgumentCaptor.forClass(Metadata.class);

        try {
            exceptionInterceptor.interceptCall(mockServerCall, metadata, mockServerCallHandler);
        } catch (Exception e) {
            // Expected to catch and handle
        }

        verify(mockServerCall).close(any(Status.class), trailersCaptor.capture());

        Metadata capturedTrailers = trailersCaptor.getValue();
        String correlationId =
                capturedTrailers.get(
                        Metadata.Key.of("x-correlation-id", Metadata.ASCII_STRING_MARSHALLER));
        assertThat(correlationId).isEqualTo("test-correlation-123");
    }

    @Test
    void testExceptionInterceptorSanitizesStackTraceFromResponse() {
        Metadata metadata = new Metadata();

        when(mockServerCall.getMethodDescriptor()).thenReturn(methodDescriptor);
        when(mockServerCallHandler.startCall(any(), any()))
                .thenThrow(
                        new RuntimeException(
                                "Internal error with sensitive info: database password=secret123"));

        ArgumentCaptor<Status> statusCaptor = ArgumentCaptor.forClass(Status.class);

        try {
            exceptionInterceptor.interceptCall(mockServerCall, metadata, mockServerCallHandler);
        } catch (Exception e) {
            // Expected to catch and handle
        }

        verify(mockServerCall).close(statusCaptor.capture(), any(Metadata.class));

        Status capturedStatus = statusCaptor.getValue();
        assertThat(capturedStatus.getDescription()).doesNotContain("database password=secret123");
        assertThat(capturedStatus.getDescription()).contains("Internal server error");
    }

    @Test
    void testExceptionInterceptorAllowsNormalCallsToPassThrough() {
        Metadata metadata = new Metadata();

        when(mockServerCall.getMethodDescriptor()).thenReturn(methodDescriptor);
        when(mockServerCallHandler.startCall(any(), any())).thenReturn(mockServerCallListener);

        ServerCall.Listener<Object> listener =
                exceptionInterceptor.interceptCall(mockServerCall, metadata, mockServerCallHandler);

        assertThat(listener).isNotNull();
        verify(mockServerCallHandler).startCall(any(), any());
    }
}
