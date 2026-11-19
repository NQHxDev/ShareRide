package com.shareride.common.communication.grpc.interceptor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.Status;
import java.io.InputStream;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GrpcLoggingInterceptorTest {

    @Mock private Channel mockChannel;

    @Mock private ClientCall<Object, Object> mockClientCall;

    @Mock private ServerCall<Object, Object> mockServerCall;

    @Mock private ServerCallHandler<Object, Object> mockServerCallHandler;

    @Mock private ServerCall.Listener<Object> mockServerCallListener;

    @Captor private ArgumentCaptor<ClientCall.Listener<Object>> listenerCaptor;
    @Captor private ArgumentCaptor<ServerCall<Object, Object>> callCaptor;

    private io.grpc.MethodDescriptor<Object, Object> methodDescriptor;

    private GrpcClientLoggingInterceptor clientLoggingInterceptor;
    private GrpcServerLoggingInterceptor serverLoggingInterceptor;

    @BeforeEach
    void setUp() {
        clientLoggingInterceptor = new GrpcClientLoggingInterceptor();
        serverLoggingInterceptor = new GrpcServerLoggingInterceptor();

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
    void testClientLoggingInterceptorLogsOutgoingCall() {
        when(mockChannel.newCall(any(), any())).thenReturn(mockClientCall);

        ClientCall<Object, Object> interceptedCall =
                clientLoggingInterceptor.interceptCall(
                        methodDescriptor, CallOptions.DEFAULT, mockChannel);

        interceptedCall.start(new ClientCall.Listener<Object>() {}, new Metadata());

        verify(mockClientCall).start(any(), any(Metadata.class));
    }

    @Test
    void testServerLoggingInterceptorLogsIncomingCall() {
        Metadata metadata = new Metadata();

        when(mockServerCall.getMethodDescriptor()).thenReturn(methodDescriptor);
        when(mockServerCallHandler.startCall(any(), any())).thenReturn(mockServerCallListener);

        serverLoggingInterceptor.interceptCall(mockServerCall, metadata, mockServerCallHandler);

        verify(mockServerCallHandler).startCall(any(), any());
    }

    @Test
    void testServerLoggingInterceptorLogsSuccessfulCompletion() {
        Metadata metadata = new Metadata();

        when(mockServerCall.getMethodDescriptor()).thenReturn(methodDescriptor);
        when(mockServerCallHandler.startCall(any(), any())).thenReturn(mockServerCallListener);

        ServerCall.Listener<Object> listener =
                serverLoggingInterceptor.interceptCall(
                        mockServerCall, metadata, mockServerCallHandler);

        listener.onComplete();

        verify(mockServerCallHandler).startCall(any(), any());
    }

    @Test
    void testServerLoggingInterceptorLogsCancellation() {
        Metadata metadata = new Metadata();

        when(mockServerCall.getMethodDescriptor()).thenReturn(methodDescriptor);
        when(mockServerCallHandler.startCall(any(), any())).thenReturn(mockServerCallListener);

        ServerCall.Listener<Object> listener =
                serverLoggingInterceptor.interceptCall(
                        mockServerCall, metadata, mockServerCallHandler);

        listener.onCancel();

        verify(mockServerCallHandler).startCall(any(), any());
    }

    @Test
    void testClientLoggingInterceptorOnCloseOkAndError() {
        when(mockChannel.newCall(any(), any())).thenReturn(mockClientCall);

        ClientCall<Object, Object> interceptedCall =
                clientLoggingInterceptor.interceptCall(
                        methodDescriptor, CallOptions.DEFAULT, mockChannel);

        interceptedCall.start(new ClientCall.Listener<Object>() {}, new Metadata());
        verify(mockClientCall).start(listenerCaptor.capture(), any(Metadata.class));

        ClientCall.Listener<Object> forwardingListener = listenerCaptor.getValue();
        forwardingListener.onClose(Status.OK, new Metadata());
        forwardingListener.onClose(
                Status.INTERNAL.withDescription("Internal error"), new Metadata());
    }

    @Test
    void testServerLoggingInterceptorCloseOkAndErrorAndOnCompleteException() {
        Metadata metadata = new Metadata();

        when(mockServerCall.getMethodDescriptor()).thenReturn(methodDescriptor);

        when(mockServerCallHandler.startCall(callCaptor.capture(), any()))
                .thenReturn(mockServerCallListener);

        ServerCall.Listener<Object> listener =
                serverLoggingInterceptor.interceptCall(
                        mockServerCall, metadata, mockServerCallHandler);

        ServerCall<Object, Object> monitoringCall = callCaptor.getValue();
        monitoringCall.close(Status.OK, new Metadata());
        monitoringCall.close(Status.UNAVAILABLE.withDescription("Service down"), new Metadata());

        // onComplete throwing exception
        doThrow(new RuntimeException("Completion failed"))
                .when(mockServerCallListener)
                .onComplete();

        Assertions.assertThatThrownBy(() -> listener.onComplete())
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Completion failed");
    }
}
