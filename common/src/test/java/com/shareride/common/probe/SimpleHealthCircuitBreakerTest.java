package com.shareride.common.probe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.shareride.common.probe.dependency.CircuitBreakerState;
import com.shareride.common.probe.dependency.ResilientHealthExecutor;
import com.shareride.common.probe.dependency.SimpleHealthCircuitBreaker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

class SimpleHealthCircuitBreakerTest {

    @Test
    @DisplayName("Should trip circuit breaker from CLOSED to OPEN after failure threshold")
    void testCircuitBreakerTripping() {
        SimpleHealthCircuitBreaker cb = new SimpleHealthCircuitBreaker("test-cb", 3, 500L);
        assertEquals(CircuitBreakerState.CLOSED, cb.getState());
        assertTrue(cb.allowExecution());

        cb.recordFailure();
        assertEquals(CircuitBreakerState.CLOSED, cb.getState());
        cb.recordFailure();
        assertEquals(CircuitBreakerState.CLOSED, cb.getState());

        cb.recordFailure(); // 3rd failure trips the circuit
        assertEquals(CircuitBreakerState.OPEN, cb.getState());
        assertFalse(cb.allowExecution());
    }

    @Test
    @DisplayName(
            "Should transition from OPEN to HALF_OPEN after reset timeout and reset on success")
    void testCircuitBreakerRecovery() throws InterruptedException {
        SimpleHealthCircuitBreaker cb = new SimpleHealthCircuitBreaker("test-cb-recovery", 2, 50L);
        cb.recordFailure();
        cb.recordFailure();
        assertEquals(CircuitBreakerState.OPEN, cb.getState());

        Thread.sleep(60L);
        // Reset timeout elapsed -> should be HALF_OPEN and allow trial execution
        assertEquals(CircuitBreakerState.HALF_OPEN, cb.getState());
        assertTrue(cb.allowExecution());

        cb.recordSuccess();
        assertEquals(CircuitBreakerState.CLOSED, cb.getState());
        assertTrue(cb.allowExecution());
    }

    @Test
    @DisplayName("ResilientHealthExecutor should return fast failure when circuit breaker is OPEN")
    void testResilientExecutorCircuitBreaking() {
        SimpleHealthCircuitBreaker cb = new SimpleHealthCircuitBreaker("exec-cb", 1, 1000L);
        ResilientHealthExecutor executor = new ResilientHealthExecutor("exec-service", 500L, cb);

        // First call fails
        Health h1 =
                executor.execute(
                        () -> {
                            throw new RuntimeException("DB down");
                        });
        assertEquals(Status.DOWN, h1.getStatus());

        // Second call is short-circuited
        Health h2 = executor.execute(() -> Health.up().build());
        assertEquals(Status.DOWN, h2.getStatus());
        assertEquals("CIRCUIT_BREAKER_OPEN", h2.getDetails().get("status"));
    }

    @Test
    @DisplayName("ResilientHealthExecutor should timeout slow dependency calls")
    void testResilientExecutorTimeout() {
        ResilientHealthExecutor executor = new ResilientHealthExecutor("timeout-service", 50L);

        Health health =
                executor.execute(
                        () -> {
                            try {
                                Thread.sleep(200L);
                            } catch (InterruptedException ignored) {
                            }
                            return Health.up().build();
                        });

        assertEquals(Status.DOWN, health.getStatus());
        assertEquals("TIMEOUT", health.getDetails().get("status"));
    }
}
