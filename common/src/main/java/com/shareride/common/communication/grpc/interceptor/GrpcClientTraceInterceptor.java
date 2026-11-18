package com.shareride.common.communication.grpc.interceptor;

import com.shareride.common.communication.grpc.GrpcMetadataConstants;
import com.shareride.common.context.CorrelationIdContext;
import com.shareride.common.context.UserContext;
import com.shareride.common.context.UserContextHolder;
import com.shareride.common.filter.TraceIdContext;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.ForwardingClientCallListener;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

/**
 * gRPC client interceptor that injects distributed tracing context and user identity from {@link
 * UserContextHolder} into outgoing gRPC request metadata headers.
 *
 * <p>Propagates: user ID, email, tenant ID, roles, permissions, session ID, trace ID, span ID,
 * correlation ID, and W3C Traceparent header for distributed tracing across service boundaries.
 *
 * <p>Falls back to {@link CorrelationIdContext}, {@link TraceIdContext}, and SLF4J MDC when {@link
 * UserContext} is null (e.g., unauthenticated Gateway requests).
 */
@Slf4j
public class GrpcClientTraceInterceptor implements ClientInterceptor {

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
            MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {

        return new ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT>(
                next.newCall(method, callOptions)) {

            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                UserContext context = UserContextHolder.getContext();

                if (context != null) {
                    // Inject user identity
                    if (context.getUserId() != null) {
                        headers.put(GrpcMetadataConstants.USER_ID_KEY, context.getUserId());
                    }
                    if (context.getEmail() != null) {
                        headers.put(GrpcMetadataConstants.EMAIL_KEY, context.getEmail());
                    }
                    if (context.getTenantId() != null) {
                        headers.put(GrpcMetadataConstants.TENANT_ID_KEY, context.getTenantId());
                    }
                    if (context.getSessionId() != null) {
                        headers.put(GrpcMetadataConstants.SESSION_ID_KEY, context.getSessionId());
                    }

                    // Inject roles as comma-separated string
                    if (context.getRoles() != null && !context.getRoles().isEmpty()) {
                        String roles = String.join(",", context.getRoles());
                        headers.put(GrpcMetadataConstants.ROLES_KEY, roles);
                    }

                    // Inject permissions as comma-separated string
                    if (context.getPermissions() != null && !context.getPermissions().isEmpty()) {
                        String permissions = String.join(",", context.getPermissions());
                        headers.put(GrpcMetadataConstants.PERMISSIONS_KEY, permissions);
                    }

                    // Inject distributed tracing context
                    if (context.getTraceId() != null) {
                        headers.put(GrpcMetadataConstants.TRACE_ID_KEY, context.getTraceId());
                    }
                    if (context.getSpanId() != null) {
                        headers.put(GrpcMetadataConstants.SPAN_ID_KEY, context.getSpanId());
                    }
                    if (context.getCorrelationId() != null) {
                        headers.put(
                                GrpcMetadataConstants.CORRELATION_ID_KEY,
                                context.getCorrelationId());
                    }

                    // Inject W3C Traceparent header if trace context exists
                    if (context.getTraceId() != null && context.getSpanId() != null) {
                        String traceparent =
                                String.format(
                                        "00-%s-%s-01", context.getTraceId(), context.getSpanId());
                        headers.put(GrpcMetadataConstants.TRACEPARENT_KEY, traceparent);
                    }

                    log.debug(
                            "Injected trace context into gRPC call: method={}, userId={},"
                                    + " traceId={}",
                            method.getFullMethodName(),
                            context.getUserId(),
                            context.getTraceId());
                } else {
                    // Fallback: UserContext is null (e.g., unauthenticated Gateway requests)
                    // Use CorrelationIdContext, TraceIdContext, and MDC
                    String correlationId = CorrelationIdContext.getCorrelationId();
                    if (correlationId != null && !correlationId.isEmpty()) {
                        headers.put(GrpcMetadataConstants.CORRELATION_ID_KEY, correlationId);
                    }

                    String traceId = TraceIdContext.getTraceId();
                    if (traceId != null && !traceId.isEmpty()) {
                        headers.put(GrpcMetadataConstants.TRACE_ID_KEY, traceId);
                    }

                    String spanId = TraceIdContext.getSpanId();
                    if (spanId != null && !spanId.isEmpty()) {
                        headers.put(GrpcMetadataConstants.SPAN_ID_KEY, spanId);
                    }

                    // Inject W3C Traceparent header if trace context exists
                    if (traceId != null
                            && !traceId.isEmpty()
                            && spanId != null
                            && !spanId.isEmpty()) {
                        String traceparent = String.format("00-%s-%s-01", traceId, spanId);
                        headers.put(GrpcMetadataConstants.TRACEPARENT_KEY, traceparent);
                    }

                    // Fallback to MDC if available
                    String mdcUserId = MDC.get("userId");
                    if (mdcUserId != null && !mdcUserId.isEmpty()) {
                        headers.put(GrpcMetadataConstants.USER_ID_KEY, mdcUserId);
                    }

                    log.debug(
                            "Injected fallback trace context into gRPC call: method={}, traceId={},"
                                    + " correlationId={}",
                            method.getFullMethodName(),
                            traceId,
                            correlationId);
                }

                super.start(
                        new ForwardingClientCallListener.SimpleForwardingClientCallListener<RespT>(
                                responseListener) {},
                        headers);
            }
        };
    }
}
