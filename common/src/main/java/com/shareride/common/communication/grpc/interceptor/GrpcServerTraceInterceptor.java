package com.shareride.common.communication.grpc.interceptor;

import com.shareride.common.communication.grpc.GrpcMetadataConstants;
import com.shareride.common.context.CorrelationIdContext;
import com.shareride.common.context.UserContext;
import com.shareride.common.context.UserContextHolder;
import com.shareride.common.filter.TraceIdContext;
import com.shareride.common.logging.MdcConstants;
import io.grpc.ForwardingServerCallListener;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

/**
 * gRPC server interceptor that extracts distributed tracing context and user identity from incoming
 * gRPC request metadata headers and populates {@link UserContextHolder}, {@link
 * CorrelationIdContext}, {@link TraceIdContext}, and SLF4J MDC for downstream processing.
 *
 * <p>Extracts: user ID, email, tenant ID, roles, permissions, session ID, trace ID, span ID,
 * correlation ID from gRPC Metadata and makes them available via thread-local contexts and SLF4J
 * MDC for logging.
 *
 * <p>Ensures proper cleanup of all contexts and MDC in finally block after request processing.
 */
@Slf4j
public class GrpcServerTraceInterceptor implements ServerInterceptor {

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {

        UserContext.UserContextBuilder contextBuilder = UserContext.builder();

        // Extract user identity
        String userId = headers.get(GrpcMetadataConstants.USER_ID_KEY);
        if (userId != null) {
            contextBuilder.userId(userId);
        }

        String email = headers.get(GrpcMetadataConstants.EMAIL_KEY);
        if (email != null) {
            contextBuilder.email(email);
        }

        String tenantId = headers.get(GrpcMetadataConstants.TENANT_ID_KEY);
        if (tenantId != null) {
            contextBuilder.tenantId(tenantId);
        }

        String sessionId = headers.get(GrpcMetadataConstants.SESSION_ID_KEY);
        if (sessionId != null) {
            contextBuilder.sessionId(sessionId);
        }

        // Extract roles from comma-separated string
        String rolesStr = headers.get(GrpcMetadataConstants.ROLES_KEY);
        if (rolesStr != null && !rolesStr.isEmpty()) {
            Set<String> roles =
                    Arrays.stream(rolesStr.split(","))
                            .map(s -> s.trim())
                            .filter(s -> !s.isEmpty())
                            .collect(Collectors.toSet());
            contextBuilder.roles(roles);
        }

        // Extract permissions from comma-separated string
        String permissionsStr = headers.get(GrpcMetadataConstants.PERMISSIONS_KEY);
        if (permissionsStr != null && !permissionsStr.isEmpty()) {
            Set<String> permissions =
                    Arrays.stream(permissionsStr.split(","))
                            .map(s -> s.trim())
                            .filter(s -> !s.isEmpty())
                            .collect(Collectors.toSet());
            contextBuilder.permissions(permissions);
        }

        // Extract distributed tracing context
        String traceId = headers.get(GrpcMetadataConstants.TRACE_ID_KEY);
        if (traceId != null) {
            contextBuilder.traceId(traceId);
            TraceIdContext.setTraceId(traceId);
        }

        String spanId = headers.get(GrpcMetadataConstants.SPAN_ID_KEY);
        if (spanId != null) {
            contextBuilder.spanId(spanId);
            TraceIdContext.setSpanId(spanId);
        }

        String correlationId = headers.get(GrpcMetadataConstants.CORRELATION_ID_KEY);
        if (correlationId != null) {
            contextBuilder.correlationId(correlationId);
            CorrelationIdContext.setCorrelationId(correlationId);
        }

        UserContext userContext = contextBuilder.build();
        UserContextHolder.setContext(userContext);

        // Populate SLF4J MDC for logging (from both UserContext and ThreadLocal contexts)
        populateMDC();

        log.debug(
                "Extracted trace context from gRPC call: method={}, userId={}, traceId={},"
                        + " correlationId={}",
                call.getMethodDescriptor().getFullMethodName(),
                userId,
                traceId,
                correlationId);

        try {
            ServerCall.Listener<ReqT> listener = next.startCall(call, headers);
            return new ContextCleanupListener<>(listener);
        } catch (Exception e) {
            clearContext();
            throw e;
        }
    }

    /**
     * Populates SLF4J MDC with tracing and user context for structured logging. Reads from both
     * UserContext and ThreadLocal contexts (TraceIdContext, CorrelationIdContext).
     */
    private void populateMDC() {
        // Populate from ThreadLocal contexts first (always available)
        String traceId = TraceIdContext.getTraceId();
        if (traceId != null) {
            MDC.put(MdcConstants.TRACE_ID, traceId);
        }

        String spanId = TraceIdContext.getSpanId();
        if (spanId != null) {
            MDC.put(MdcConstants.SPAN_ID, spanId);
        }

        String correlationId = CorrelationIdContext.getCorrelationId();
        if (correlationId != null) {
            MDC.put(MdcConstants.CORRELATION_ID, correlationId);
        }

        // Populate from UserContext if available (adds user-specific fields)
        UserContext context = UserContextHolder.getContext();
        if (context != null) {
            if (context.getUserId() != null) {
                MDC.put(MdcConstants.USER_ID, context.getUserId());
            }
            if (context.getTenantId() != null) {
                MDC.put(MdcConstants.TENANT_ID, context.getTenantId());
            }
            if (context.getSessionId() != null) {
                MDC.put(MdcConstants.SESSION_ID, context.getSessionId());
            }
        }
    }

    /**
     * Clears UserContext, CorrelationIdContext, TraceIdContext and request-scoped MDC keys to
     * prevent context leakage across requests in thread pools.
     */
    private void clearContext() {
        UserContextHolder.clear();
        CorrelationIdContext.clear();
        TraceIdContext.clear();

        // Remove only request-scoped MDC keys (preserve service-level keys like serviceName,
        // environment)
        MDC.remove(MdcConstants.TRACE_ID);
        MDC.remove(MdcConstants.SPAN_ID);
        MDC.remove(MdcConstants.USER_ID);
        MDC.remove(MdcConstants.CORRELATION_ID);
        MDC.remove(MdcConstants.TENANT_ID);
        MDC.remove(MdcConstants.SESSION_ID);
        MDC.remove(MdcConstants.REQUEST_ID);
        MDC.remove(MdcConstants.CLIENT_IP);
    }

    /**
     * Listener wrapper that ensures context cleanup after request processing completes.
     *
     * @param <ReqT> the request type
     */
    private class ContextCleanupListener<ReqT>
            extends ForwardingServerCallListener.SimpleForwardingServerCallListener<ReqT> {

        ContextCleanupListener(ServerCall.Listener<ReqT> delegate) {
            super(delegate);
        }

        @Override
        public void onComplete() {
            try {
                super.onComplete();
            } finally {
                clearContext();
            }
        }

        @Override
        public void onCancel() {
            try {
                super.onCancel();
            } finally {
                clearContext();
            }
        }
    }
}
