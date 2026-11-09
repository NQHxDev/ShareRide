package com.shareride.common.resilience.circuitbreaker;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * High-performance, thread-safe Circuit Breaker implementation with sliding window metrics and
 * fail-fast resilience protection for synchronous service calls.
 */
public class ResilienceCircuitBreaker {

    private static final Logger log = LoggerFactory.getLogger(ResilienceCircuitBreaker.class);

    private final String name;
    private final CircuitBreakerConfig config;
    private final AtomicReference<CircuitBreakerState> state;
    private final AtomicLong lastStateChangeTimestamp;

    // Sliding window ring buffer
    private final byte[] ringBuffer; // 0 = SUCCESS, 1 = FAILURE, 2 = SLOW
    private final AtomicInteger bufferIndex;
    private final AtomicInteger totalRecordedCalls;

    // Half-open counters
    private final AtomicInteger halfOpenAttempts;
    private final AtomicInteger halfOpenSuccesses;

    public ResilienceCircuitBreaker(String name) {
        this(name, CircuitBreakerConfig.ofDefaults());
    }

    public ResilienceCircuitBreaker(String name, CircuitBreakerConfig config) {
        this.name = name;
        this.config = config != null ? config : CircuitBreakerConfig.ofDefaults();
        this.state = new AtomicReference<>(CircuitBreakerState.CLOSED);
        this.lastStateChangeTimestamp = new AtomicLong(System.currentTimeMillis());
        this.ringBuffer = new byte[this.config.getSlidingWindowSize()];
        this.bufferIndex = new AtomicInteger(0);
        this.totalRecordedCalls = new AtomicInteger(0);
        this.halfOpenAttempts = new AtomicInteger(0);
        this.halfOpenSuccesses = new AtomicInteger(0);
    }

    public String getName() {
        return name;
    }

    public CircuitBreakerConfig getConfig() {
        return config;
    }

    public CircuitBreakerState getState() {
        checkAndRefreshState();
        return state.get();
    }

    /**
     * Checks if a request is permitted through the circuit breaker.
     *
     * @return true if call is allowed; false if rejected (fast-fail)
     */
    public boolean allowExecution() {
        checkAndRefreshState();
        CircuitBreakerState currentState = state.get();

        if (currentState == CircuitBreakerState.CLOSED) {
            return true;
        }

        if (currentState == CircuitBreakerState.HALF_OPEN) {
            int currentAttempts = halfOpenAttempts.incrementAndGet();
            return currentAttempts <= config.getPermittedNumberOfCallsInHalfOpenState();
        }

        return false;
    }

    /** Records a successful downstream call. */
    public void recordSuccess(long durationMs) {
        checkAndRefreshState();
        CircuitBreakerState currentState = state.get();

        if (currentState == CircuitBreakerState.HALF_OPEN) {
            int successes = halfOpenSuccesses.incrementAndGet();
            if (successes >= config.getPermittedNumberOfCallsInHalfOpenState()) {
                transitionToClosed();
            }
            return;
        }

        if (currentState == CircuitBreakerState.CLOSED) {
            byte callType =
                    (durationMs >= config.getSlowCallDurationThresholdMs()) ? (byte) 2 : (byte) 0;
            recordCall(callType);
            evaluateClosedMetrics();
        }
    }

    /** Records a failed downstream call. */
    public void recordFailure(long durationMs, Throwable throwable) {
        checkAndRefreshState();
        CircuitBreakerState currentState = state.get();

        if (currentState == CircuitBreakerState.HALF_OPEN) {
            log.warn(
                    "Downstream service [{}] failed in HALF_OPEN probe state. Reopening circuit"
                            + " breaker.",
                    name);
            transitionToOpen();
            return;
        }

        if (currentState == CircuitBreakerState.CLOSED) {
            recordCall((byte) 1);
            evaluateClosedMetrics();
        }
    }

    private void recordCall(byte status) {
        int idx = Math.abs(bufferIndex.getAndIncrement() % ringBuffer.length);
        synchronized (ringBuffer) {
            ringBuffer[idx] = status;
        }
        totalRecordedCalls.updateAndGet(count -> Math.min(count + 1, ringBuffer.length));
    }

    private void evaluateClosedMetrics() {
        int recorded = totalRecordedCalls.get();
        if (recorded < config.getMinimumNumberOfCalls()) {
            return;
        }

        int failures = 0;
        int slowCalls = 0;
        synchronized (ringBuffer) {
            for (int i = 0; i < recorded; i++) {
                if (ringBuffer[i] == 1) {
                    failures++;
                } else if (ringBuffer[i] == 2) {
                    slowCalls++;
                }
            }
        }

        float failureRate = ((float) failures / recorded) * 100.0f;
        float slowRate = ((float) slowCalls / recorded) * 100.0f;

        if (failureRate >= config.getFailureRateThreshold()
                || slowRate >= config.getSlowCallRateThreshold()) {
            log.error(
                    "Circuit breaker for [{}] tripped to OPEN: failureRate={}% (threshold {}%),"
                            + " slowRate={}% (threshold {}%)",
                    name,
                    failureRate,
                    config.getFailureRateThreshold(),
                    slowRate,
                    config.getSlowCallRateThreshold());
            transitionToOpen();
        }
    }

    private void checkAndRefreshState() {
        if (state.get() == CircuitBreakerState.OPEN) {
            long elapsed = System.currentTimeMillis() - lastStateChangeTimestamp.get();
            if (elapsed >= config.getWaitDurationInOpenStateMs()) {
                if (state.compareAndSet(CircuitBreakerState.OPEN, CircuitBreakerState.HALF_OPEN)) {
                    lastStateChangeTimestamp.set(System.currentTimeMillis());
                    halfOpenAttempts.set(0);
                    halfOpenSuccesses.set(0);
                    log.info(
                            "Circuit breaker for [{}] transitioned from OPEN to HALF_OPEN after"
                                    + " {}ms cooldown",
                            name,
                            elapsed);
                }
            }
        }
    }

    public synchronized void transitionToOpen() {
        state.set(CircuitBreakerState.OPEN);
        lastStateChangeTimestamp.set(System.currentTimeMillis());
        resetWindow();
    }

    public synchronized void transitionToClosed() {
        state.set(CircuitBreakerState.CLOSED);
        lastStateChangeTimestamp.set(System.currentTimeMillis());
        resetWindow();
        log.info("Circuit breaker for [{}] recovered and transitioned to CLOSED", name);
    }

    public synchronized void transitionToHalfOpen() {
        state.set(CircuitBreakerState.HALF_OPEN);
        lastStateChangeTimestamp.set(System.currentTimeMillis());
        halfOpenAttempts.set(0);
        halfOpenSuccesses.set(0);
        resetWindow();
    }

    private void resetWindow() {
        synchronized (ringBuffer) {
            for (int i = 0; i < ringBuffer.length; i++) {
                ringBuffer[i] = 0;
            }
        }
        bufferIndex.set(0);
        totalRecordedCalls.set(0);
        halfOpenAttempts.set(0);
        halfOpenSuccesses.set(0);
    }
}
