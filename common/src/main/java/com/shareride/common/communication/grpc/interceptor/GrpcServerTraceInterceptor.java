package com.shareride.common.communication.grpc.interceptor;

import com.shareride.common.communication.grpc.GrpcMetadataConstants;
import com.shareride.common.context.UserContext;
import com.shareride.common.context.UserContextHolder;
import com.shareride.common.filter.TraceIdContext;
import com.shareride.common.logging.MdcConstants;
import io.grpc.*;
import org.slf4j.MDC;

import java.util.HashSet;
import java.util.Set;

/**
 * Server-side gRPC interceptor that extracts trace IDs, tenant isolation metadata,
 * and user identity from inbound call Metadata, initializing thread-local and SLF4J MDC contexts.
 */
public class GrpcServerTraceInterceptor implements ServerInterceptor {

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {

        String rawTraceparent = headers.get(GrpcMetadataConstants.TRACEPARENT_KEY);
        String traceId = headers.get(GrpcMetadataConstants.TRACE_ID_KEY);
        String spanId = headers.get(GrpcMetadataConstants.SPAN_ID_KEY);
        boolean sampled = true;

        if (rawTraceparent != null) {
            TraceIdContext.TraceContextData parsed = TraceIdContext.parseTraceparent(rawTraceparent);
            if (parsed != null) {
                traceId = parsed.traceId();
                spanId = TraceIdContext.generateSpanId();
                TraceIdContext.setParentId(parsed.parentSpanId());
                sampled = parsed.sampled();
            }
        }

        if (traceId == null || traceId.isBlank()) {
            traceId = TraceIdContext.generateTraceId();
        }
        if (spanId == null || spanId.isBlank()) {
            spanId = TraceIdContext.generateSpanId();
        }

        TraceIdContext.setTraceId(traceId);
        TraceIdContext.setSpanId(spanId);
        TraceIdContext.setSampled(sampled);

        MDC.put(MdcConstants.TRACE_ID, traceId);
        MDC.put(MdcConstants.SPAN_ID, spanId);

        String userId = headers.get(GrpcMetadataConstants.USER_ID_KEY);
        String tenantId = headers.get(GrpcMetadataConstants.TENANT_ID_KEY);
        String correlationId = headers.get(GrpcMetadataConstants.CORRELATION_ID_KEY);
        String priority = headers.get(GrpcMetadataConstants.PRIORITY_KEY);
        String rawFeatureFlags = headers.get(GrpcMetadataConstants.FEATURE_FLAGS_KEY);

        Set<String> featureFlags = new HashSet<>();
        if (rawFeatureFlags != null && !rawFeatureFlags.isBlank()) {
            for (String flag : rawFeatureFlags.split(",")) {
                String trimmed = flag.trim();
                if (!trimmed.isEmpty()) {
                    featureFlags.add(trimmed);
                }
            }
        }

        if (correlationId == null || correlationId.isBlank()) {
            correlationId = traceId;
        }

        UserContext userContext = UserContext.builder()
                .userId(userId)
                .tenantId(tenantId)
                .correlationId(correlationId)
                .priority(priority)
                .featureFlags(featureFlags)
                .traceId(traceId)
                .spanId(spanId)
                .build();

        UserContextHolder.setContext(userContext);

        if (userId != null && !userId.isBlank()) {
            MDC.put(MdcConstants.USER_ID, userId);
        }
        if (tenantId != null && !tenantId.isBlank()) {
            MDC.put(MdcConstants.TENANT_ID, tenantId);
        }
        if (correlationId != null && !correlationId.isBlank()) {
            MDC.put(MdcConstants.CORRELATION_ID, correlationId);
        }

        ServerCall.Listener<ReqT> delegate = next.startCall(call, headers);

        return new ForwardingServerCallListener.SimpleForwardingServerCallListener<>(delegate) {
            @Override
            public void onComplete() {
                try {
                    super.onComplete();
                } finally {
                    cleanContext();
                }
            }

            @Override
            public void onCancel() {
                try {
                    super.onCancel();
                } finally {
                    cleanContext();
                }
            }
        };
    }

    private void cleanContext() {
        TraceIdContext.clear();
        UserContextHolder.clear();
        MDC.remove(MdcConstants.TRACE_ID);
        MDC.remove(MdcConstants.SPAN_ID);
        MDC.remove(MdcConstants.USER_ID);
        MDC.remove(MdcConstants.TENANT_ID);
        MDC.remove(MdcConstants.CORRELATION_ID);
    }
}
