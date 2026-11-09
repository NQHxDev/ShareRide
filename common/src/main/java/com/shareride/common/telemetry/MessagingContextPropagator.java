package com.shareride.common.telemetry;

import com.shareride.common.context.CorrelationIdContext;
import com.shareride.common.context.UserContext;
import com.shareride.common.context.UserContextHolder;
import com.shareride.common.filter.TraceIdContext;
import com.shareride.common.filter.UserContextFilter;
import com.shareride.common.logging.MdcConstants;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.slf4j.MDC;

/**
 * Distributed context propagator for Asynchronous Message Queues (Kafka, RabbitMQ, SQS, JMS).
 * Injects and extracts distributed trace context and user/tenant isolation metadata into message
 * headers.
 */
public final class MessagingContextPropagator {

    public static final String TRACEPARENT_HEADER = TraceIdContext.TRACEPARENT_HEADER;
    public static final String TRACE_ID_HEADER = TraceIdContext.TRACE_HEADER;
    public static final String SPAN_ID_HEADER = TraceIdContext.SPAN_HEADER;
    public static final String REQUEST_ID_HEADER = TraceIdContext.REQUEST_ID_HEADER;
    public static final String CORRELATION_ID_HEADER = CorrelationIdContext.CORRELATION_ID_HEADER;
    public static final String USER_ID_HEADER = UserContextFilter.USER_ID_HEADER;
    public static final String TENANT_ID_HEADER = UserContextFilter.TENANT_ID_HEADER;
    public static final String PRIORITY_HEADER = UserContextFilter.PRIORITY_HEADER;
    public static final String FEATURE_FLAGS_HEADER = UserContextFilter.FEATURE_FLAGS_HEADER;

    private MessagingContextPropagator() {}

    /** Injects tracing, user identity, and tenant baggage into outgoing message headers. */
    public static void inject(Map<String, Object> headers) {
        if (headers == null) {
            return;
        }

        String traceId = TraceIdContext.getTraceId();
        String spanId = TraceIdContext.getSpanId();
        boolean sampled = TraceIdContext.isSampled();

        headers.put(TRACE_ID_HEADER, traceId);
        headers.put(SPAN_ID_HEADER, spanId);
        headers.put(REQUEST_ID_HEADER, traceId);
        headers.put(TRACEPARENT_HEADER, TraceIdContext.formatTraceparent(traceId, spanId, sampled));

        UserContext userContext = UserContextHolder.getContext();
        String correlationId = (userContext != null) ? userContext.getCorrelationId() : null;
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = CorrelationIdContext.getCorrelationId();
        }
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = CorrelationIdContext.generateCorrelationId();
        }
        headers.put(CORRELATION_ID_HEADER, correlationId);

        if (userContext != null) {
            if (userContext.getUserId() != null && !userContext.getUserId().isBlank()) {
                headers.put(USER_ID_HEADER, userContext.getUserId());
            }
            if (userContext.getTenantId() != null && !userContext.getTenantId().isBlank()) {
                headers.put(TENANT_ID_HEADER, userContext.getTenantId());
            }
            if (userContext.getPriority() != null && !userContext.getPriority().isBlank()) {
                headers.put(PRIORITY_HEADER, userContext.getPriority());
            }
            if (userContext.getFeatureFlags() != null && !userContext.getFeatureFlags().isEmpty()) {
                headers.put(FEATURE_FLAGS_HEADER, String.join(",", userContext.getFeatureFlags()));
            }
        }
    }

    /**
     * Extracts tracing, user context, and tenant isolation metadata from incoming message headers,
     * restoring {@link TraceIdContext}, {@link CorrelationIdContext}, {@link UserContextHolder},
     * and SLF4J MDC.
     */
    public static void extract(Map<String, ?> headers) {
        if (headers == null || headers.isEmpty()) {
            return;
        }

        String rawTraceparent = readHeader(headers, TRACEPARENT_HEADER);
        String traceId = readHeader(headers, TRACE_ID_HEADER);
        String spanId = readHeader(headers, SPAN_ID_HEADER);
        boolean sampled = true;

        if (rawTraceparent != null) {
            TraceIdContext.TraceContextData parsed =
                    TraceIdContext.parseTraceparent(rawTraceparent);
            if (parsed != null) {
                traceId = parsed.traceId();
                spanId = TraceIdContext.generateSpanId();
                TraceIdContext.setParentId(parsed.parentSpanId());
                sampled = parsed.sampled();
            }
        }

        if (traceId == null || traceId.isBlank()) {
            traceId = readHeader(headers, REQUEST_ID_HEADER);
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

        String userId = readHeader(headers, USER_ID_HEADER);
        String tenantId = readHeader(headers, TENANT_ID_HEADER);
        String correlationId = readHeader(headers, CORRELATION_ID_HEADER);
        String priority = readHeader(headers, PRIORITY_HEADER);
        String rawFeatureFlags = readHeader(headers, FEATURE_FLAGS_HEADER);

        Set<String> featureFlags = new HashSet<>();
        if (rawFeatureFlags != null && !rawFeatureFlags.isBlank()) {
            for (String flag : rawFeatureFlags.split(",")) {
                String trimmed = flag.trim();
                if (!trimmed.isEmpty()) {
                    featureFlags.add(trimmed);
                }
            }
        }

        // Correlation ID is distinct from technical trace ID
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = CorrelationIdContext.generateCorrelationId();
        } else {
            correlationId = correlationId.trim();
        }

        CorrelationIdContext.setCorrelationId(correlationId);

        UserContext userContext =
                UserContext.builder()
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
        if (priority != null && !priority.isBlank()) {
            MDC.put(MdcConstants.PRIORITY, priority);
        }
    }

    /** Cleans up thread-local context after message processing finishes. */
    public static void clear() {
        TraceIdContext.clear();
        CorrelationIdContext.clear();
        UserContextHolder.clear();
        MDC.remove(MdcConstants.TRACE_ID);
        MDC.remove(MdcConstants.SPAN_ID);
        MDC.remove(MdcConstants.USER_ID);
        MDC.remove(MdcConstants.TENANT_ID);
        MDC.remove(MdcConstants.CORRELATION_ID);
        MDC.remove(MdcConstants.PRIORITY);
    }

    private static String readHeader(Map<String, ?> headers, String key) {
        Object val = headers.get(key);
        if (val == null) {
            // Case-insensitive lookup
            for (Map.Entry<String, ?> entry : headers.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(key)) {
                    val = entry.getValue();
                    break;
                }
            }
        }

        if (val == null) {
            return null;
        }
        if (val instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8).trim();
        }
        return val.toString().trim();
    }
}
