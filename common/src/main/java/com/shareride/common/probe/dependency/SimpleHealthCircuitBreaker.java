package com.shareride.common.probe.dependency;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Thread-safe circuit breaker preventing cascading failures and thread pool starvation when
 * checking third-party dependencies (databases, redis, external APIs).
 */
public class SimpleHealthCircuitBreaker {

    private final String name;
    private final int failureThreshold;
    private final long resetTimeoutMs;

    private final AtomicReference<CircuitBreakerState> state =
            new AtomicReference<>(CircuitBreakerState.CLOSED);
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final AtomicLong lastFailureTimestamp = new AtomicLong(0L);

    public SimpleHealthCircuitBreaker(String name) {
        this(name, 3, 15000L);
    }

    public SimpleHealthCircuitBreaker(String name, int failureThreshold, long resetTimeoutMs) {
        this.name = name;
        this.failureThreshold = failureThreshold;
        this.resetTimeoutMs = resetTimeoutMs;
    }

    public String getName() {
        return name;
    }

    /** Checks if a health probe is permitted through the circuit. */
    public boolean allowExecution() {
        CircuitBreakerState currentState = getState();
        return currentState == CircuitBreakerState.CLOSED
                || currentState == CircuitBreakerState.HALF_OPEN;
    }

    /** Records a successful execution. Resets consecutive failures and transitions to CLOSED. */
    public void recordSuccess() {
        consecutiveFailures.set(0);
        state.set(CircuitBreakerState.CLOSED);
    }

    /** Records a failed execution. Transitions to OPEN if failure threshold is reached. */
    public void recordFailure() {
        lastFailureTimestamp.set(System.currentTimeMillis());
        int failures = consecutiveFailures.incrementAndGet();
        if (failures >= failureThreshold) {
            state.set(CircuitBreakerState.OPEN);
        }
    }

    /** Evaluates current circuit breaker state, handling OPEN -> HALF_OPEN timeout transition. */
    public CircuitBreakerState getState() {
        CircuitBreakerState current = state.get();
        if (current == CircuitBreakerState.OPEN) {
            long now = System.currentTimeMillis();
            if (now - lastFailureTimestamp.get() >= resetTimeoutMs) {
                if (state.compareAndSet(CircuitBreakerState.OPEN, CircuitBreakerState.HALF_OPEN)) {
                    return CircuitBreakerState.HALF_OPEN;
                }
            }
        }
        return state.get();
    }

    public int getConsecutiveFailures() {
        return consecutiveFailures.get();
    }

    public void reset() {
        consecutiveFailures.set(0);
        state.set(CircuitBreakerState.CLOSED);
    }
}
