package com.shareride.common.resilience.circuitbreaker;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Central registry holding singleton {@link ResilienceCircuitBreaker} instances by service or
 * operation name.
 */
public class CircuitBreakerRegistry {

    private final ConcurrentMap<String, ResilienceCircuitBreaker> circuitBreakers =
            new ConcurrentHashMap<>();
    private final CircuitBreakerConfig defaultConfig;

    public CircuitBreakerRegistry() {
        this(CircuitBreakerConfig.ofDefaults());
    }

    public CircuitBreakerRegistry(CircuitBreakerConfig defaultConfig) {
        this.defaultConfig =
                defaultConfig != null ? defaultConfig : CircuitBreakerConfig.ofDefaults();
    }

    /** Gets or creates a Circuit Breaker with default configuration. */
    public ResilienceCircuitBreaker circuitBreaker(String name) {
        return circuitBreakers.computeIfAbsent(
                name, key -> new ResilienceCircuitBreaker(key, defaultConfig));
    }

    /** Gets or creates a Circuit Breaker with custom configuration. */
    public ResilienceCircuitBreaker circuitBreaker(String name, CircuitBreakerConfig config) {
        return circuitBreakers.computeIfAbsent(
                name, key -> new ResilienceCircuitBreaker(key, config));
    }

    public ConcurrentMap<String, ResilienceCircuitBreaker> getAll() {
        return circuitBreakers;
    }
}
