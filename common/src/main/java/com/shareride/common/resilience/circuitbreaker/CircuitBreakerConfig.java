package com.shareride.common.resilience.circuitbreaker;

import lombok.Builder;
import lombok.Getter;

/** Immutable configuration for {@link ResilienceCircuitBreaker}. */
@Getter
@Builder
public class CircuitBreakerConfig {

    @Builder.Default private final float failureRateThreshold = 50.0f; // percentage 0 - 100

    @Builder.Default private final float slowCallRateThreshold = 50.0f; // percentage 0 - 100

    @Builder.Default private final long slowCallDurationThresholdMs = 2000L;

    @Builder.Default private final int slidingWindowSize = 20;

    @Builder.Default private final int minimumNumberOfCalls = 5;

    @Builder.Default private final long waitDurationInOpenStateMs = 10000L;

    @Builder.Default private final int permittedNumberOfCallsInHalfOpenState = 5;

    public static CircuitBreakerConfig ofDefaults() {
        return CircuitBreakerConfig.builder().build();
    }
}
