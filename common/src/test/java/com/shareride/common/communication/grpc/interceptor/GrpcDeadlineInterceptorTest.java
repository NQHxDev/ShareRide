package com.shareride.common.communication.grpc.interceptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.Deadline;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GrpcDeadlineInterceptorTest {

    @Mock private Channel mockChannel;

    @Mock private ClientCall<Object, Object> mockClientCall;

    private io.grpc.MethodDescriptor<Object, Object> methodDescriptor;

    private GrpcClientDeadlineInterceptor deadlineInterceptor;

    private static final long DEFAULT_DEADLINE_MS = 5000L;

    @BeforeEach
    void setUp() {
        deadlineInterceptor = new GrpcClientDeadlineInterceptor(DEFAULT_DEADLINE_MS);

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
    void testDeadlineInterceptorAddsDeadlineWhenNotPresent() {
        when(mockChannel.newCall(any(), any())).thenReturn(mockClientCall);

        ArgumentCaptor<CallOptions> callOptionsCaptor = ArgumentCaptor.forClass(CallOptions.class);

        deadlineInterceptor.interceptCall(methodDescriptor, CallOptions.DEFAULT, mockChannel);

        verify(mockChannel).newCall(any(), callOptionsCaptor.capture());

        CallOptions capturedOptions = callOptionsCaptor.getValue();
        assertThat(capturedOptions.getDeadline()).isNotNull();
    }

    @Test
    void testDeadlineInterceptorPreservesExistingDeadline() {
        Deadline existingDeadline = Deadline.after(10, TimeUnit.SECONDS);
        CallOptions optionsWithDeadline = CallOptions.DEFAULT.withDeadline(existingDeadline);

        when(mockChannel.newCall(any(), any())).thenReturn(mockClientCall);

        ArgumentCaptor<CallOptions> callOptionsCaptor = ArgumentCaptor.forClass(CallOptions.class);

        deadlineInterceptor.interceptCall(methodDescriptor, optionsWithDeadline, mockChannel);

        verify(mockChannel).newCall(any(), callOptionsCaptor.capture());

        CallOptions capturedOptions = callOptionsCaptor.getValue();
        assertThat(capturedOptions.getDeadline()).isEqualTo(existingDeadline);
    }

    @Test
    void testDeadlineInterceptorUsesConfiguredTimeout() {
        long customTimeout = 3000L;
        GrpcClientDeadlineInterceptor customInterceptor =
                new GrpcClientDeadlineInterceptor(customTimeout);

        when(mockChannel.newCall(any(), any())).thenReturn(mockClientCall);

        ArgumentCaptor<CallOptions> callOptionsCaptor = ArgumentCaptor.forClass(CallOptions.class);

        customInterceptor.interceptCall(methodDescriptor, CallOptions.DEFAULT, mockChannel);

        verify(mockChannel).newCall(any(), callOptionsCaptor.capture());

        CallOptions capturedOptions = callOptionsCaptor.getValue();
        assertThat(capturedOptions.getDeadline()).isNotNull();
    }
}
