package com.shareride.common.metrics;

import com.shareride.common.filter.TraceIdContext;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.prometheus.metrics.tracer.common.SpanContext;
import org.springframework.beans.factory.ObjectProvider;

/**
 * Bridges Micrometer Tracing / OpenTelemetry context to Prometheus client 1.x
 * to enable Prometheus Exemplars (traceId, spanId linked to metrics buckets in Grafana).
 */
public class OpenTelemetryPrometheusSpanContext implements SpanContext {

    private final ObjectProvider<Tracer> tracerProvider;

    public OpenTelemetryPrometheusSpanContext(ObjectProvider<Tracer> tracerProvider) {
        this.tracerProvider = tracerProvider;
    }

    @Override
    public String getCurrentTraceId() {
        if (tracerProvider != null) {
            Tracer tracer = tracerProvider.getIfAvailable();
            if (tracer != null) {
                Span span = tracer.currentSpan();
                if (span != null && span.context() != null) {
                    String tid = span.context().traceId();
                    if (tid != null && !tid.isBlank()) {
                        return tid;
                    }
                }
            }
        }
        return TraceIdContext.getTraceId();
    }

    @Override
    public String getCurrentSpanId() {
        if (tracerProvider != null) {
            Tracer tracer = tracerProvider.getIfAvailable();
            if (tracer != null) {
                Span span = tracer.currentSpan();
                if (span != null && span.context() != null) {
                    String sid = span.context().spanId();
                    if (sid != null && !sid.isBlank()) {
                        return sid;
                    }
                }
            }
        }
        return TraceIdContext.getSpanId();
    }

    @Override
    public boolean isCurrentSpanSampled() {
        if (tracerProvider != null) {
            Tracer tracer = tracerProvider.getIfAvailable();
            if (tracer != null) {
                Span span = tracer.currentSpan();
                if (span != null && span.context() != null) {
                    Boolean sampled = span.context().sampled();
                    if (sampled != null) {
                        return sampled;
                    }
                }
            }
        }
        return TraceIdContext.isSampled();
    }

    @Override
    public void markCurrentSpanAsExemplar() {
        // Can attach exemplar flag to tracer context if needed
    }
}
