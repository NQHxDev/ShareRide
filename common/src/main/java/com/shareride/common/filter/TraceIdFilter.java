package com.shareride.common.filter;

import com.shareride.common.logging.EnvironmentContext;
import com.shareride.common.logging.MdcConstants;
import com.shareride.common.logging.dynamic.DynamicLogLevelContext;
import com.shareride.common.telemetry.AdaptiveTraceSampler;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filter that establishes distributed traceId, spanId, and unique requestId for each request.
 * Populates MDC with infrastructure & environment metadata, HTTP response headers,
 * and handles dynamic runtime debug activation and adaptive sampling.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String MDC_TRACE_ID = MdcConstants.TRACE_ID;
    public static final String MDC_SPAN_ID = MdcConstants.SPAN_ID;

    private final ObjectProvider<Tracer> tracerProvider;
    private final ObjectProvider<AdaptiveTraceSampler> samplerProvider;

    public TraceIdFilter() {
        this(null, null);
    }

    public TraceIdFilter(ObjectProvider<Tracer> tracerProvider) {
        this(tracerProvider, null);
    }

    public TraceIdFilter(ObjectProvider<Tracer> tracerProvider,
                         ObjectProvider<AdaptiveTraceSampler> samplerProvider) {
        this.tracerProvider = tracerProvider;
        this.samplerProvider = samplerProvider;
        if (tracerProvider != null) {
            TraceIdContext.setTracerSupplier(tracerProvider::getIfAvailable);
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        long startTime = System.currentTimeMillis();
        String traceId = null;
        String spanId = null;
        String parentId = null;
        Boolean sampled = null;

        // 1. Try to read from active Micrometer Tracer if present
        if (tracerProvider != null) {
            Tracer tracer = tracerProvider.getIfAvailable();
            if (tracer != null) {
                Span currentSpan = tracer.currentSpan();
                if (currentSpan != null && currentSpan.context() != null) {
                    traceId = currentSpan.context().traceId();
                    spanId = currentSpan.context().spanId();
                    parentId = currentSpan.context().parentId();
                    sampled = currentSpan.context().sampled();
                }
            }
        }

        // 2. Try W3C traceparent header: 00-traceid-spanid-flags
        if (traceId == null || traceId.isBlank()) {
            String traceParent = request.getHeader(TraceIdContext.TRACEPARENT_HEADER);
            TraceIdContext.TraceContextData parsed = TraceIdContext.parseTraceparent(traceParent);
            if (parsed != null) {
                traceId = parsed.traceId();
                parentId = parsed.parentSpanId();
                spanId = TraceIdContext.generateSpanId();
                sampled = parsed.sampled();
            }
        }

        // 3. Try X-Trace-Id header
        if (traceId == null || traceId.isBlank()) {
            traceId = request.getHeader(TraceIdContext.TRACE_HEADER);
        }

        // 4. Try X-Request-Id header for traceId fallback
        String requestId = request.getHeader(TraceIdContext.REQUEST_ID_HEADER);
        if (requestId != null && !requestId.isBlank()) {
            TraceIdContext.setRequestId(requestId.trim());
            if (traceId == null || traceId.isBlank()) {
                traceId = requestId.trim();
            }
        } else {
            requestId = TraceIdContext.generateTraceId();
            TraceIdContext.setRequestId(requestId);
        }

        // 5. Generate traceId if still absent
        if (traceId == null || traceId.isBlank()) {
            traceId = TraceIdContext.generateTraceId();
        }

        if (spanId == null || spanId.isBlank()) {
            spanId = request.getHeader(TraceIdContext.SPAN_HEADER);
            if (spanId == null || spanId.isBlank()) {
                spanId = TraceIdContext.generateSpanId();
            }
        }

        // 6. Resolve sampling decision if not explicitly dictated by parent
        if (sampled == null) {
            String priority = request.getHeader(UserContextFilter.PRIORITY_HEADER);
            boolean highPriority = "HIGH".equalsIgnoreCase(priority) || "URGENT".equalsIgnoreCase(priority);
            AdaptiveTraceSampler sampler = (samplerProvider != null) ? samplerProvider.getIfAvailable() : null;
            if (sampler != null) {
                sampled = sampler.shouldSample(request.getRequestURI(), highPriority, traceId);
            } else {
                sampled = true;
            }
        }

        // 7. Dynamic Log Level evaluation
        String debugRequest = request.getHeader(DynamicLogLevelContext.DEBUG_REQUEST_HEADER);
        String debugUser = request.getHeader(DynamicLogLevelContext.DEBUG_USER_HEADER);
        if ("true".equalsIgnoreCase(debugRequest)
                || DynamicLogLevelContext.isDebugEndpoint(request.getRequestURI())
                || (debugUser != null && DynamicLogLevelContext.isDebugUser(debugUser))) {
            DynamicLogLevelContext.setDebugOverride(true);
        }

        TraceIdContext.setTraceId(traceId);
        TraceIdContext.setSpanId(spanId);
        if (parentId != null) {
            TraceIdContext.setParentId(parentId);
        }
        TraceIdContext.setSampled(sampled);
        TraceIdContext.setStartTime(startTime);

        // Populate MDC
        MDC.put(MdcConstants.TRACE_ID, traceId);
        MDC.put(MdcConstants.SPAN_ID, spanId);
        MDC.put(MdcConstants.REQUEST_ID, requestId);
        EnvironmentContext.populateMdc();

        response.setHeader(TraceIdContext.TRACE_HEADER, traceId);
        response.setHeader(TraceIdContext.SPAN_HEADER, spanId);
        response.setHeader(TraceIdContext.REQUEST_ID_HEADER, requestId);
        response.setHeader(TraceIdContext.TRACEPARENT_HEADER, TraceIdContext.formatTraceparent(traceId, spanId, sampled));

        try {
            filterChain.doFilter(request, response);
        } finally {
            int status = response.getStatus();
            long duration = System.currentTimeMillis() - startTime;
            if (!TraceIdContext.isSampled() && (status >= 400 || duration > 1000)) {
                TraceIdContext.setSampled(true);
            }

            MDC.remove(MdcConstants.TRACE_ID);
            MDC.remove(MdcConstants.SPAN_ID);
            MDC.remove(MdcConstants.REQUEST_ID);
            DynamicLogLevelContext.clear();
            TraceIdContext.clear();
        }
    }
}
