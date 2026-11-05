package com.shareride.common.probe.dependency;

/**
 * Circuit breaker states for dependency health checks.
 */
public enum CircuitBreakerState {
    CLOSED,
    HALF_OPEN,
    OPEN
}
