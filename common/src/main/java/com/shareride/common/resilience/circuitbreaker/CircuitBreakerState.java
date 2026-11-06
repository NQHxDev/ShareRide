package com.shareride.common.resilience.circuitbreaker;

/**
 * State machine stages for the Resilience Circuit Breaker pattern.
 */
public enum CircuitBreakerState {
    CLOSED,
    OPEN,
    HALF_OPEN
}
