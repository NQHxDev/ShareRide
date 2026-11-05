package com.shareride.common.metrics;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.tracing.Tracer;
import io.prometheus.metrics.tracer.common.SpanContext;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.micrometer.metrics.autoconfigure.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import java.net.InetAddress;

/**
 * Auto-configuration for Micrometer metrics, Prometheus scrape registry,
 * SLO histogram buckets, latency percentiles, Exemplar correlation, and common tags.
 */
@AutoConfiguration
@ConditionalOnClass(MeterRegistry.class)
public class MetricsAutoConfiguration {

    private static final double[] SLO_BUCKETS = {
            0.010, // 10ms
            0.025, // 25ms
            0.050, // 50ms
            0.100, // 100ms
            0.250, // 250ms
            0.500, // 500ms
            1.000, // 1000ms (1s)
            2.500, // 2500ms (2.5s)
            5.000  // 5000ms (5s)
    };

    private static final double[] PERCENTILES = {0.50, 0.90, 0.95, 0.99};

    @Bean
    @ConditionalOnMissingBean
    public ShareRideMetrics shareRideMetrics(MeterRegistry meterRegistry) {
        return new ShareRideMetrics(meterRegistry);
    }

    /**
     * Bridges tracing context to Prometheus client 1.x registry, enabling Prometheus Exemplars.
     */
    @Bean
    @ConditionalOnMissingBean
    public SpanContext prometheusSpanContext(ObjectProvider<Tracer> tracerProvider) {
        return new OpenTelemetryPrometheusSpanContext(tracerProvider);
    }

    /**
     * Enables SLO histogram buckets and latency percentiles (p50, p90, p95, p99)
     * across HTTP server requests and all ShareRide timer metrics.
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public MeterFilter sloHistogramAndPercentilesMeterFilter() {
        return new MeterFilter() {
            @Override
            public DistributionStatisticConfig configure(Meter.Id id, DistributionStatisticConfig config) {
                String name = id.getName();
                if (name.startsWith("http.") || name.startsWith("shareride.") || name.equals("http.server.requests")) {
                    return DistributionStatisticConfig.builder()
                            .percentilesHistogram(true)
                            .percentiles(PERCENTILES)
                            .serviceLevelObjectives(SLO_BUCKETS)
                            .build()
                            .merge(config);
                }
                return config;
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean
    public MeterRegistryCustomizer<MeterRegistry> commonMeterRegistryCustomizer(
            @Value("${spring.application.name:shareride-service}") String applicationName,
            @Value("${spring.profiles.active:default}") String environment,
            @Value("${info.app.version:1.0.0}") String version) {
        String instanceId = resolveInstanceId();
        return registry -> registry.config().commonTags(
                "application", applicationName,
                "environment", environment,
                "instance", instanceId,
                "version", version
        );
    }

    private String resolveInstanceId() {
        String host = System.getenv("HOSTNAME");
        if (host == null || host.isBlank()) {
            host = System.getenv("HOST");
        }
        if (host == null || host.isBlank()) {
            try {
                host = InetAddress.getLocalHost().getHostName();
            } catch (Exception ignored) {
                host = "unknown-instance";
            }
        }
        return host;
    }
}
