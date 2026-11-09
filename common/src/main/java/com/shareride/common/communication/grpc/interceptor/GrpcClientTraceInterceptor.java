package com.shareride.common.communication.grpc.interceptor;

import com.shareride.common.communication.grpc.GrpcMetadataConstants;
import com.shareride.common.context.UserContext;
import com.shareride.common.context.UserContextHolder;
import com.shareride.common.filter.TraceIdContext;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;

/**
 * Client-side gRPC interceptor that propagates distributed trace identifiers, tenant context, and
 * user authentication tokens into outgoing call Metadata headers.
 */
public class GrpcClientTraceInterceptor implements ClientInterceptor {

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
            MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {

        return new ForwardingClientCall.SimpleForwardingClientCall<>(
                next.newCall(method, callOptions)) {
            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                // Trace Context Propagation
                String traceId = TraceIdContext.getTraceId();
                String spanId = TraceIdContext.getSpanId();
                boolean sampled = TraceIdContext.isSampled();

                if (traceId != null && !traceId.isBlank()) {
                    headers.put(GrpcMetadataConstants.TRACE_ID_KEY, traceId);
                    headers.put(
                            GrpcMetadataConstants.TRACEPARENT_KEY,
                            TraceIdContext.formatTraceparent(traceId, spanId, sampled));
                }
                if (spanId != null && !spanId.isBlank()) {
                    headers.put(GrpcMetadataConstants.SPAN_ID_KEY, spanId);
                }

                // User & Tenant Context Propagation
                UserContext userContext = UserContextHolder.getContext();
                if (userContext != null) {
                    if (userContext.getUserId() != null && !userContext.getUserId().isBlank()) {
                        headers.put(GrpcMetadataConstants.USER_ID_KEY, userContext.getUserId());
                    }
                    if (userContext.getTenantId() != null && !userContext.getTenantId().isBlank()) {
                        headers.put(GrpcMetadataConstants.TENANT_ID_KEY, userContext.getTenantId());
                    }
                    if (userContext.getCorrelationId() != null
                            && !userContext.getCorrelationId().isBlank()) {
                        headers.put(
                                GrpcMetadataConstants.CORRELATION_ID_KEY,
                                userContext.getCorrelationId());
                    } else if (traceId != null) {
                        headers.put(GrpcMetadataConstants.CORRELATION_ID_KEY, traceId);
                    }
                    if (userContext.getPriority() != null && !userContext.getPriority().isBlank()) {
                        headers.put(GrpcMetadataConstants.PRIORITY_KEY, userContext.getPriority());
                    }
                    if (userContext.getFeatureFlags() != null
                            && !userContext.getFeatureFlags().isEmpty()) {
                        headers.put(
                                GrpcMetadataConstants.FEATURE_FLAGS_KEY,
                                String.join(",", userContext.getFeatureFlags()));
                    }
                }

                super.start(responseListener, headers);
            }
        };
    }
}
