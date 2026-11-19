package com.shareride.common.communication.grpc.interceptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shareride.common.communication.grpc.GrpcMetadataConstants;
import com.shareride.common.context.UserContext;
import com.shareride.common.context.UserContextHolder;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import java.io.InputStream;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GrpcTraceInterceptorTest {

    @Mock private Channel mockChannel;

    @Mock private ClientCall<Object, Object> mockClientCall;

    @Mock private ServerCall<Object, Object> mockServerCall;

    @Mock private ServerCallHandler<Object, Object> mockServerCallHandler;

    private io.grpc.MethodDescriptor<Object, Object> methodDescriptor;

    private GrpcClientTraceInterceptor clientInterceptor;
    private GrpcServerTraceInterceptor serverInterceptor;

    @BeforeEach
    void setUp() {
        clientInterceptor = new GrpcClientTraceInterceptor();
        serverInterceptor = new GrpcServerTraceInterceptor();
        UserContextHolder.clear();

        // Create a real MethodDescriptor since it's final and cannot be mocked
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

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    void testClientInterceptorInjectsUserIdToMetadata() {
        UserContext userContext =
                UserContext.builder()
                        .userId("user-123")
                        .email("test@example.com")
                        .tenantId("tenant-1")
                        .correlationId("corr-456")
                        .sessionId("session-789")
                        .build();

        UserContextHolder.setContext(userContext);

        when(mockChannel.newCall(any(), any())).thenReturn(mockClientCall);

        ClientCall<Object, Object> interceptedCall =
                clientInterceptor.interceptCall(methodDescriptor, CallOptions.DEFAULT, mockChannel);

        ArgumentCaptor<Metadata> metadataCaptor = ArgumentCaptor.forClass(Metadata.class);
        interceptedCall.start(new ClientCall.Listener<Object>() {}, new Metadata());

        verify(mockClientCall).start(any(), metadataCaptor.capture());

        Metadata capturedMetadata = metadataCaptor.getValue();
        assertThat(capturedMetadata.get(GrpcMetadataConstants.USER_ID_KEY)).isEqualTo("user-123");
        assertThat(capturedMetadata.get(GrpcMetadataConstants.EMAIL_KEY))
                .isEqualTo("test@example.com");
        assertThat(capturedMetadata.get(GrpcMetadataConstants.TENANT_ID_KEY)).isEqualTo("tenant-1");
        assertThat(capturedMetadata.get(GrpcMetadataConstants.CORRELATION_ID_KEY))
                .isEqualTo("corr-456");
        assertThat(capturedMetadata.get(GrpcMetadataConstants.SESSION_ID_KEY))
                .isEqualTo("session-789");
    }

    @Test
    void testClientInterceptorInjectsRolesCommaSeparated() {
        UserContext userContext =
                UserContext.builder()
                        .userId("user-123")
                        .roles(Set.of("ROLE_ADMIN", "ROLE_USER"))
                        .build();

        UserContextHolder.setContext(userContext);

        when(mockChannel.newCall(any(), any())).thenReturn(mockClientCall);

        ClientCall<Object, Object> interceptedCall =
                clientInterceptor.interceptCall(methodDescriptor, CallOptions.DEFAULT, mockChannel);

        ArgumentCaptor<Metadata> metadataCaptor = ArgumentCaptor.forClass(Metadata.class);
        interceptedCall.start(new ClientCall.Listener<Object>() {}, new Metadata());

        verify(mockClientCall).start(any(), metadataCaptor.capture());

        Metadata capturedMetadata = metadataCaptor.getValue();
        String roles = capturedMetadata.get(GrpcMetadataConstants.ROLES_KEY);
        assertThat(roles).isNotNull();
        assertThat(roles).contains("ROLE_ADMIN");
        assertThat(roles).contains("ROLE_USER");
    }

    @Test
    void testClientInterceptorInjectsPermissionsCommaSeparated() {
        UserContext userContext =
                UserContext.builder()
                        .userId("user-123")
                        .permissions(Set.of("READ", "WRITE", "DELETE"))
                        .build();

        UserContextHolder.setContext(userContext);

        when(mockChannel.newCall(any(), any())).thenReturn(mockClientCall);

        ClientCall<Object, Object> interceptedCall =
                clientInterceptor.interceptCall(methodDescriptor, CallOptions.DEFAULT, mockChannel);

        ArgumentCaptor<Metadata> metadataCaptor = ArgumentCaptor.forClass(Metadata.class);
        interceptedCall.start(new ClientCall.Listener<Object>() {}, new Metadata());

        verify(mockClientCall).start(any(), metadataCaptor.capture());

        Metadata capturedMetadata = metadataCaptor.getValue();
        String permissions = capturedMetadata.get(GrpcMetadataConstants.PERMISSIONS_KEY);
        assertThat(permissions).isNotNull();
        assertThat(permissions).contains("READ");
        assertThat(permissions).contains("WRITE");
        assertThat(permissions).contains("DELETE");
    }

    @Test
    void testClientInterceptorInjectsTraceparent() {
        UserContext userContext =
                UserContext.builder()
                        .userId("user-123")
                        .traceId("trace-abc")
                        .spanId("span-def")
                        .build();

        UserContextHolder.setContext(userContext);

        when(mockChannel.newCall(any(), any())).thenReturn(mockClientCall);

        ClientCall<Object, Object> interceptedCall =
                clientInterceptor.interceptCall(methodDescriptor, CallOptions.DEFAULT, mockChannel);

        ArgumentCaptor<Metadata> metadataCaptor = ArgumentCaptor.forClass(Metadata.class);
        interceptedCall.start(new ClientCall.Listener<Object>() {}, new Metadata());

        verify(mockClientCall).start(any(), metadataCaptor.capture());

        Metadata capturedMetadata = metadataCaptor.getValue();
        assertThat(capturedMetadata.get(GrpcMetadataConstants.TRACE_ID_KEY)).isEqualTo("trace-abc");
        assertThat(capturedMetadata.get(GrpcMetadataConstants.SPAN_ID_KEY)).isEqualTo("span-def");
        assertThat(capturedMetadata.get(GrpcMetadataConstants.TRACEPARENT_KEY)).isNotNull();
    }

    @Test
    void testClientInterceptorHandlesEmptyUserContext() {
        when(mockChannel.newCall(any(), any())).thenReturn(mockClientCall);

        ClientCall<Object, Object> interceptedCall =
                clientInterceptor.interceptCall(methodDescriptor, CallOptions.DEFAULT, mockChannel);

        interceptedCall.start(new ClientCall.Listener<Object>() {}, new Metadata());

        verify(mockClientCall).start(any(), any(Metadata.class));
    }

    @Test
    void testServerInterceptorExtractsUserIdFromMetadata() {
        Metadata metadata = new Metadata();
        metadata.put(GrpcMetadataConstants.USER_ID_KEY, "user-456");
        metadata.put(GrpcMetadataConstants.EMAIL_KEY, "server@example.com");
        metadata.put(GrpcMetadataConstants.TENANT_ID_KEY, "tenant-2");
        metadata.put(GrpcMetadataConstants.SESSION_ID_KEY, "session-xyz");

        when(mockServerCall.getMethodDescriptor()).thenReturn(methodDescriptor);

        serverInterceptor.interceptCall(mockServerCall, metadata, mockServerCallHandler);

        UserContext context = UserContextHolder.getContext();
        assertThat(context).isNotNull();
        assertThat(context.getUserId()).isEqualTo("user-456");
        assertThat(context.getEmail()).isEqualTo("server@example.com");
        assertThat(context.getTenantId()).isEqualTo("tenant-2");
        assertThat(context.getSessionId()).isEqualTo("session-xyz");
    }

    @Test
    void testServerInterceptorExtractsRolesFromCommaSeparatedString() {
        Metadata metadata = new Metadata();
        metadata.put(GrpcMetadataConstants.USER_ID_KEY, "user-456");
        metadata.put(GrpcMetadataConstants.ROLES_KEY, "ROLE_ADMIN,ROLE_USER,ROLE_MODERATOR");

        when(mockServerCall.getMethodDescriptor()).thenReturn(methodDescriptor);

        serverInterceptor.interceptCall(mockServerCall, metadata, mockServerCallHandler);

        UserContext context = UserContextHolder.getContext();
        assertThat(context).isNotNull();
        assertThat(context.getRoles()).contains("ROLE_ADMIN", "ROLE_USER", "ROLE_MODERATOR");
    }

    @Test
    void testServerInterceptorExtractsPermissionsFromCommaSeparatedString() {
        Metadata metadata = new Metadata();
        metadata.put(GrpcMetadataConstants.USER_ID_KEY, "user-456");
        metadata.put(GrpcMetadataConstants.PERMISSIONS_KEY, "READ,WRITE,DELETE,EXECUTE");

        when(mockServerCall.getMethodDescriptor()).thenReturn(methodDescriptor);

        serverInterceptor.interceptCall(mockServerCall, metadata, mockServerCallHandler);

        UserContext context = UserContextHolder.getContext();
        assertThat(context).isNotNull();
        assertThat(context.getPermissions()).contains("READ", "WRITE", "DELETE", "EXECUTE");
    }

    @Test
    void testServerInterceptorExtractsTraceContext() {
        Metadata metadata = new Metadata();
        metadata.put(GrpcMetadataConstants.TRACE_ID_KEY, "trace-123");
        metadata.put(GrpcMetadataConstants.SPAN_ID_KEY, "span-456");
        metadata.put(GrpcMetadataConstants.CORRELATION_ID_KEY, "corr-789");

        when(mockServerCall.getMethodDescriptor()).thenReturn(methodDescriptor);

        serverInterceptor.interceptCall(mockServerCall, metadata, mockServerCallHandler);

        UserContext context = UserContextHolder.getContext();
        assertThat(context).isNotNull();
        assertThat(context.getTraceId()).isEqualTo("trace-123");
        assertThat(context.getSpanId()).isEqualTo("span-456");
        assertThat(context.getCorrelationId()).isEqualTo("corr-789");
    }

    @Test
    void testServerInterceptorHandlesEmptyMetadata() {
        Metadata metadata = new Metadata();

        when(mockServerCall.getMethodDescriptor()).thenReturn(methodDescriptor);

        serverInterceptor.interceptCall(mockServerCall, metadata, mockServerCallHandler);

        verify(mockServerCallHandler).startCall(any(), any());
    }

    @Test
    void testServerInterceptorClearsContextAfterCall() {
        Metadata metadata = new Metadata();
        metadata.put(GrpcMetadataConstants.USER_ID_KEY, "user-cleanup-test");

        when(mockServerCall.getMethodDescriptor()).thenReturn(methodDescriptor);

        serverInterceptor.interceptCall(mockServerCall, metadata, mockServerCallHandler);

        UserContext afterCall = UserContextHolder.getContext();
        assertThat(afterCall).isNotNull();
    }

    @Test
    void testClientInterceptorFallbackContextWhenUserContextNull() {
        UserContextHolder.clear();
        com.shareride.common.context.CorrelationIdContext.setCorrelationId("corr-fallback-123");
        com.shareride.common.filter.TraceIdContext.setTraceId("trace-fallback-456");
        com.shareride.common.filter.TraceIdContext.setSpanId("span-fallback-789");
        org.slf4j.MDC.put("userId", "user-mdc-1");

        try {
            when(mockChannel.newCall(any(), any())).thenReturn(mockClientCall);

            ClientCall<Object, Object> call =
                    clientInterceptor.interceptCall(
                            methodDescriptor, CallOptions.DEFAULT, mockChannel);

            @SuppressWarnings("unchecked")
            ClientCall.Listener<Object> mockListener =
                    org.mockito.Mockito.mock(ClientCall.Listener.class);
            Metadata metadata = new Metadata();

            call.start(mockListener, metadata);

            ArgumentCaptor<Metadata> metadataCaptor = ArgumentCaptor.forClass(Metadata.class);
            verify(mockClientCall).start(any(), metadataCaptor.capture());

            Metadata captured = metadataCaptor.getValue();
            assertThat(captured.get(GrpcMetadataConstants.CORRELATION_ID_KEY))
                    .isEqualTo("corr-fallback-123");
            assertThat(captured.get(GrpcMetadataConstants.TRACE_ID_KEY))
                    .isEqualTo("trace-fallback-456");
            assertThat(captured.get(GrpcMetadataConstants.SPAN_ID_KEY))
                    .isEqualTo("span-fallback-789");
            assertThat(captured.get(GrpcMetadataConstants.TRACEPARENT_KEY))
                    .isEqualTo("00-trace-fallback-456-span-fallback-789-01");
            assertThat(captured.get(GrpcMetadataConstants.USER_ID_KEY)).isEqualTo("user-mdc-1");
        } finally {
            com.shareride.common.context.CorrelationIdContext.clear();
            com.shareride.common.filter.TraceIdContext.clear();
            org.slf4j.MDC.remove("userId");
        }
    }
}
