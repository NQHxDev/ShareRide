package com.shareride.common.communication.grpc.interceptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shareride.common.exception.BadRequestException;
import com.shareride.common.response.FieldErrorDetail;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.Status;
import io.grpc.StatusException;
import io.grpc.StatusRuntimeException;
import java.io.InputStream;
import java.util.List;
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

    private MethodDescriptor<Object, Object> methodDescriptor;

    private GrpcServerExceptionInterceptor exceptionInterceptor;

    @BeforeEach
    void setUp() {
        exceptionInterceptor = new GrpcServerExceptionInterceptor();

        methodDescriptor =
                MethodDescriptor.<Object, Object>newBuilder()
                        .setType(MethodDescriptor.MethodType.UNARY)
                        .setFullMethodName("TestService/TestMethod")
                        .setRequestMarshaller(
                                new MethodDescriptor.Marshaller<Object>() {
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
                                new MethodDescriptor.Marshaller<Object>() {
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

    @Test
    void testExceptionInterceptorPreservesStatusRuntimeExceptionTrailers() {
        Metadata metadata = new Metadata();
        Metadata existingTrailers = new Metadata();
        existingTrailers.put(
                Metadata.Key.of("error-tag", Metadata.ASCII_STRING_MARSHALLER),
                "validation_failed");
        StatusRuntimeException statusException =
                new StatusRuntimeException(
                        Status.INVALID_ARGUMENT.withDescription("Invalid payload"),
                        existingTrailers);

        when(mockServerCall.getMethodDescriptor()).thenReturn(methodDescriptor);
        org.mockito.Mockito.doAnswer(
                        inv -> {
                            throw statusException;
                        })
                .when(mockServerCallHandler)
                .startCall(any(), any());

        ArgumentCaptor<Status> statusCaptor = ArgumentCaptor.forClass(Status.class);
        ArgumentCaptor<Metadata> trailersCaptor = ArgumentCaptor.forClass(Metadata.class);

        exceptionInterceptor.interceptCall(mockServerCall, metadata, mockServerCallHandler);

        verify(mockServerCall).close(statusCaptor.capture(), trailersCaptor.capture());
        assertThat(statusCaptor.getValue().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
        assertThat(
                        trailersCaptor
                                .getValue()
                                .get(
                                        Metadata.Key.of(
                                                "error-tag", Metadata.ASCII_STRING_MARSHALLER)))
                .isEqualTo("validation_failed");
    }

    @Test
    void testExceptionInterceptorPreservesStatusExceptionTrailers() {
        Metadata metadata = new Metadata();
        Metadata existingTrailers = new Metadata();
        existingTrailers.put(
                Metadata.Key.of("detail-tag", Metadata.ASCII_STRING_MARSHALLER), "not_found");
        StatusException statusException =
                new StatusException(
                        Status.NOT_FOUND.withDescription("Resource missing"), existingTrailers);

        when(mockServerCall.getMethodDescriptor()).thenReturn(methodDescriptor);
        org.mockito.Mockito.doAnswer(
                        inv -> {
                            throw statusException;
                        })
                .when(mockServerCallHandler)
                .startCall(any(), any());

        ArgumentCaptor<Status> statusCaptor = ArgumentCaptor.forClass(Status.class);
        ArgumentCaptor<Metadata> trailersCaptor = ArgumentCaptor.forClass(Metadata.class);

        exceptionInterceptor.interceptCall(mockServerCall, metadata, mockServerCallHandler);

        verify(mockServerCall).close(statusCaptor.capture(), trailersCaptor.capture());
        assertThat(statusCaptor.getValue().getCode()).isEqualTo(Status.Code.NOT_FOUND);
        assertThat(
                        trailersCaptor
                                .getValue()
                                .get(
                                        Metadata.Key.of(
                                                "detail-tag", Metadata.ASCII_STRING_MARSHALLER)))
                .isEqualTo("not_found");
    }

    @Test
    void testExceptionInterceptorMapsBadRequestExceptionWithFieldErrors() {
        Metadata metadata = new Metadata();
        FieldErrorDetail fieldError =
                FieldErrorDetail.builder().field("email").message("invalid email format").build();
        BadRequestException badRequest =
                new BadRequestException(
                        "BAD_REQUEST", "Invalid request input", List.of(fieldError));

        when(mockServerCall.getMethodDescriptor()).thenReturn(methodDescriptor);
        when(mockServerCallHandler.startCall(any(), any())).thenThrow(badRequest);

        ArgumentCaptor<Status> statusCaptor = ArgumentCaptor.forClass(Status.class);

        exceptionInterceptor.interceptCall(mockServerCall, metadata, mockServerCallHandler);

        verify(mockServerCall).close(statusCaptor.capture(), any(Metadata.class));
        assertThat(statusCaptor.getValue().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
    }

    @Test
    void testListenerCallbacksExceptionHandling() {
        Metadata metadata = new Metadata();
        @SuppressWarnings("unchecked")
        ServerCall.Listener<Object> mockListener = mock(ServerCall.Listener.class);

        doThrow(new RuntimeException("Cancel error")).when(mockListener).onCancel();
        doThrow(new RuntimeException("Complete error")).when(mockListener).onComplete();
        doThrow(new RuntimeException("Ready error")).when(mockListener).onReady();

        when(mockServerCall.getMethodDescriptor()).thenReturn(methodDescriptor);
        when(mockServerCallHandler.startCall(any(), any())).thenReturn(mockListener);

        ServerCall.Listener<Object> wrappedListener =
                exceptionInterceptor.interceptCall(mockServerCall, metadata, mockServerCallHandler);

        wrappedListener.onCancel();
        wrappedListener.onComplete();
        wrappedListener.onReady();
    }

    @Test
    void testCallCloseThrowsException_HandledSafely() {
        Metadata metadata = new Metadata();

        when(mockServerCall.getMethodDescriptor()).thenReturn(methodDescriptor);
        when(mockServerCallHandler.startCall(any(), any()))
                .thenThrow(new RuntimeException("Startup error"));
        doThrow(new RuntimeException("Transport closed already"))
                .when(mockServerCall)
                .close(any(), any());

        exceptionInterceptor.interceptCall(mockServerCall, metadata, mockServerCallHandler);

        verify(mockServerCall).close(any(), any());
    }
}
