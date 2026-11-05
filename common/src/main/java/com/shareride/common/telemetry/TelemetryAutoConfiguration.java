package com.shareride.common.telemetry;

import com.shareride.common.filter.TraceIdFilter;
import io.micrometer.tracing.Tracer;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for OpenTelemetry tracing, distributed context propagation,
 * W3C baggage, and adaptive/tail-based trace sampling.
 */
@AutoConfiguration
public class TelemetryAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public AdaptiveTraceSampler adaptiveTraceSampler(
            @Value("${management.tracing.sampling.probability:0.05}") double samplingProbability) {
        return new AdaptiveTraceSampler(samplingProbability);
    }

    /**
     * Expose OpenTelemetry Sampler bean to hook into OpenTelemetry SDK.
     */
    @Bean
    @ConditionalOnMissingBean(type = "io.opentelemetry.sdk.trace.samplers.Sampler")
    public Sampler openTelemetrySampler(AdaptiveTraceSampler adaptiveSampler) {
        return adaptiveSampler;
    }

    @Bean
    @ConditionalOnMissingBean
    public TraceIdFilter traceIdFilter(ObjectProvider<Tracer> tracerProvider,
                                       ObjectProvider<AdaptiveTraceSampler> samplerProvider) {
        return new TraceIdFilter(tracerProvider, samplerProvider);
    }

    @Bean
    @ConditionalOnMissingBean
    public TracePropagationInterceptor tracePropagationInterceptor(ObjectProvider<Tracer> tracerProvider) {
        return new TracePropagationInterceptor(tracerProvider);
    }
}
