package com.shareride.common.filter;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import java.security.SecureRandom;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Thread-local context for distributed tracing (traceId, spanId, parentId, requestId, timing). W3C
 * Trace Context recommendation (traceparent) and Micrometer Tracing compliant.
 */
public final class TraceIdContext {

    public static final String TRACE_HEADER = "X-Trace-Id";
    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String SPAN_HEADER = "X-Span-Id";
    public static final String TRACEPARENT_HEADER = "traceparent";

    private static final String ALL_ZERO_TRACE_ID = "00000000000000000000000000000000";
    private static final String ALL_ZERO_SPAN_ID = "0000000000000000";

    private static final ThreadLocal<String> TRACE_ID_HOLDER = new ThreadLocal<>();
    private static final ThreadLocal<String> SPAN_ID_HOLDER = new ThreadLocal<>();
    private static final ThreadLocal<String> PARENT_ID_HOLDER = new ThreadLocal<>();
    private static final ThreadLocal<String> REQUEST_ID_HOLDER = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> SAMPLED_HOLDER = new ThreadLocal<>();
    private static final ThreadLocal<Long> START_TIME_HOLDER = new ThreadLocal<>();

    private static final SecureRandom RANDOM = new SecureRandom();
    private static volatile Supplier<Tracer> tracerSupplier = () -> null;

    private TraceIdContext() {}

    public record TraceContextData(String traceId, String parentSpanId, boolean sampled) {}

    public static void setTracerSupplier(Supplier<Tracer> supplier) {
        tracerSupplier = (supplier != null) ? supplier : () -> null;
    }

    public static void setTraceId(String traceId) {
        TRACE_ID_HOLDER.set(traceId);
    }

    public static void setSpanId(String spanId) {
        SPAN_ID_HOLDER.set(spanId);
    }

    public static void setParentId(String parentId) {
        PARENT_ID_HOLDER.set(parentId);
    }

    public static String getParentId() {
        return PARENT_ID_HOLDER.get();
    }

    public static void setRequestId(String requestId) {
        REQUEST_ID_HOLDER.set(requestId);
    }

    public static String getRequestId() {
        String reqId = REQUEST_ID_HOLDER.get();
        if (reqId != null && !reqId.isBlank()) {
            return reqId;
        }
        return getTraceId();
    }

    public static void setSampled(boolean sampled) {
        SAMPLED_HOLDER.set(sampled);
    }

    public static boolean isSampled() {
        Boolean s = SAMPLED_HOLDER.get();
        return s == null || s;
    }

    public static void setStartTime(long startTimeMillis) {
        START_TIME_HOLDER.set(startTimeMillis);
    }

    public static Long getStartTime() {
        return START_TIME_HOLDER.get();
    }

    public static Long getDurationMs() {
        Long start = START_TIME_HOLDER.get();
        if (start == null) {
            return null;
        }
        return Math.max(0, System.currentTimeMillis() - start);
    }

    public static boolean hasTraceId() {
        return TRACE_ID_HOLDER.get() != null;
    }

    public static String getRawTraceId() {
        return TRACE_ID_HOLDER.get();
    }

    public static String getTraceId() {
        String current = TRACE_ID_HOLDER.get();
        if (current != null && !current.isBlank()) {
            return current;
        }

        Tracer tracer = tracerSupplier.get();
        if (tracer != null) {
            Span currentSpan = tracer.currentSpan();
            if (currentSpan != null && currentSpan.context() != null) {
                String tid = currentSpan.context().traceId();
                if (tid != null && !tid.isBlank()) {
                    TRACE_ID_HOLDER.set(tid);
                    return tid;
                }
            }
        }

        current = generateTraceId();
        TRACE_ID_HOLDER.set(current);
        return current;
    }

    public static String getSpanId() {
        String current = SPAN_ID_HOLDER.get();
        if (current != null && !current.isBlank()) {
            return current;
        }

        Tracer tracer = tracerSupplier.get();
        if (tracer != null) {
            Span currentSpan = tracer.currentSpan();
            if (currentSpan != null && currentSpan.context() != null) {
                String sid = currentSpan.context().spanId();
                if (sid != null && !sid.isBlank()) {
                    SPAN_ID_HOLDER.set(sid);
                    return sid;
                }
            }
        }

        current = generateSpanId();
        SPAN_ID_HOLDER.set(current);
        return current;
    }

    public static void clear() {
        TRACE_ID_HOLDER.remove();
        SPAN_ID_HOLDER.remove();
        PARENT_ID_HOLDER.remove();
        REQUEST_ID_HOLDER.remove();
        SAMPLED_HOLDER.remove();
        START_TIME_HOLDER.remove();
    }

    public static String generateTraceId() {
        return UUID.randomUUID().toString().replace("-", "").toLowerCase();
    }

    public static String generateSpanId() {
        byte[] bytes = new byte[8];
        RANDOM.nextBytes(bytes);
        StringBuilder sb = new StringBuilder(16);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /** Formats W3C traceparent header: 00-{traceId}-{spanId}-{flags}. */
    public static String formatTraceparent(String traceId, String spanId, boolean sampled) {
        String flags = sampled ? "01" : "00";
        return String.format("00-%s-%s-%s", traceId, spanId, flags);
    }

    /** Parses W3C traceparent header. Returns null if invalid or all zeroes. */
    public static TraceContextData parseTraceparent(String traceparent) {
        if (traceparent == null || traceparent.isBlank()) {
            return null;
        }

        String[] parts = traceparent.trim().split("-");
        if (parts.length < 4) {
            return null;
        }

        String version = parts[0];
        if (!"00".equals(version)) {
            return null;
        }

        String traceId = parts[1];
        if (traceId.length() != 32
                || ALL_ZERO_TRACE_ID.equals(traceId)
                || !traceId.matches("^[0-9a-fA-F]{32}$")) {
            return null;
        }

        String spanId = parts[2];
        if (spanId.length() != 16
                || ALL_ZERO_SPAN_ID.equals(spanId)
                || !spanId.matches("^[0-9a-fA-F]{16}$")) {
            return null;
        }

        String flags = parts[3];
        boolean sampled = "01".equals(flags);

        return new TraceContextData(traceId.toLowerCase(), spanId.toLowerCase(), sampled);
    }
}
