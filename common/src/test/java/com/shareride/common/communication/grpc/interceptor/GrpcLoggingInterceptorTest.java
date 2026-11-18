package com.shareride.common.communication.grpc.interceptor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import java.io.InputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GrpcLoggingInterceptorTest {

    @Mock private Channel mockChannel;

    @Mock private ClientCall<Object, Object> mockClientCall;

    @Mock private ServerCall<Object, Object> mockServerCall;

    @Mock private ServerCallHandler<Object, Object> mockServerCallHandler;

    @Mock private ServerCall.Listener<Object> mockServerCallListener;

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
}
