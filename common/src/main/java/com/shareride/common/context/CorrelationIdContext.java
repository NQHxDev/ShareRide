package com.shareride.common.context;

import com.shareride.common.logging.MdcConstants;
import com.shareride.common.util.UuidUtils;
import org.slf4j.MDC;

/**
 * Thread-local context for managing the Request Correlation ID across microservices.
 *
 * <p>Correlation ID tracks end-to-end business flows (transactions, sagas, multi-request
 * operations) across multiple services, asynchronous message queues, and background jobs.
 *
 * <p>It is distinct and strictly decoupled from technical distributed Trace IDs (W3C traceparent /
 * X-Trace-Id).
 */
public final class CorrelationIdContext {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";

    private static final ThreadLocal<String> CORRELATION_ID_HOLDER = new ThreadLocal<>();

    private CorrelationIdContext() {}

    /**
     * Sets the Correlation ID for the current thread and synchronizes with SLF4J MDC.
     *
     * @param correlationId the business flow correlation ID
     */
    public static void setCorrelationId(String correlationId) {
        if (correlationId != null && !correlationId.isBlank()) {
            String trimmed = correlationId.trim();
            CORRELATION_ID_HOLDER.set(trimmed);
            MDC.put(MdcConstants.CORRELATION_ID, trimmed);
        } else {
            clear();
        }
    }

    /** Returns the current Correlation ID, or null if none is bound to this thread. */
    public static String getCorrelationId() {
        return CORRELATION_ID_HOLDER.get();
    }

    /** Checks if a Correlation ID is actively present on the current thread. */
    public static boolean hasCorrelationId() {
        String cid = CORRELATION_ID_HOLDER.get();
        return cid != null && !cid.isBlank();
    }

    /**
     * Retrieves the current Correlation ID or generates a new unique business flow identifier if
     * absent, binding it to the current thread and SLF4J MDC.
     */
    public static String getOrGenerate() {
        String cid = CORRELATION_ID_HOLDER.get();
        if (cid == null || cid.isBlank()) {
            cid = generateCorrelationId();
            setCorrelationId(cid);
        }
        return cid;
    }

    /** Generates a new unique Correlation ID distinct from a 32-hex traceId. */
    public static String generateCorrelationId() {
        return UuidUtils.v7AsString();
    }

    /** Cleans up the thread-local state and removes the correlation key from MDC. */
    public static void clear() {
        CORRELATION_ID_HOLDER.remove();
        MDC.remove(MdcConstants.CORRELATION_ID);
    }
}
