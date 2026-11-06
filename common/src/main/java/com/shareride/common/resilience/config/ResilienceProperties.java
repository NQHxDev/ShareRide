package com.shareride.common.resilience.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for ShareRide Service Resilience.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "shareride.resilience")
public class ResilienceProperties {

    private CircuitBreakerProps circuitBreaker = new CircuitBreakerProps();
    private RetryProps retry = new RetryProps();
    private BulkheadProps bulkhead = new BulkheadProps();
    private TimeoutProps timeout = new TimeoutProps();
    private RateLimiterProps rateLimiter = new RateLimiterProps();

    @Getter
    @Setter
    public static class CircuitBreakerProps {
        private boolean enabled = true;
        private float failureRateThreshold = 50.0f;
        private float slowCallRateThreshold = 50.0f;
        private long slowCallDurationThresholdMs = 2000L;
        private int slidingWindowSize = 20;
        private int minimumNumberOfCalls = 5;
        private long waitDurationInOpenStateMs = 10000L;
        private int permittedNumberOfCallsInHalfOpenState = 5;
    }

    @Getter
    @Setter
    public static class RetryProps {
        private boolean enabled = true;
        private int maxAttempts = 3;
        private long initialIntervalMs = 200L;
        private double multiplier = 2.0d;
        private long maxIntervalMs = 3000L;
        private double jitterFactor = 0.25d;
    }

    @Getter
    @Setter
    public static class BulkheadProps {
        private boolean enabled = true;
        private int defaultMaxConcurrentCalls = 50;
        private long defaultMaxWaitMs = 250L;
    }

    @Getter
    @Setter
    public static class TimeoutProps {
        private boolean enabled = true;
        private long defaultTimeoutMs = 3000L;
    }

    @Getter
    @Setter
    public static class RateLimiterProps {
        private boolean enabled = true;
        private int defaultLimitPerSecond = 50;
        private int defaultWindowSeconds = 1;
    }
}
