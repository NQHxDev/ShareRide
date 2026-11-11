package com.shareride.common.resilience;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.shareride.common.resilience.circuitbreaker.CircuitBreakerConfig;
import com.shareride.common.resilience.circuitbreaker.CircuitBreakerRegistry;
import com.shareride.common.resilience.circuitbreaker.CircuitBreakerState;
import com.shareride.common.resilience.circuitbreaker.ResilienceCircuitBreaker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Resilience Circuit Breaker Tests")
class CircuitBreakerTest {

    @Test
    @DisplayName("Should start in CLOSED state and permit calls")
    void testInitialClosedState() {
        ResilienceCircuitBreaker cb = new ResilienceCircuitBreaker("test-service");
        assertEquals(CircuitBreakerState.CLOSED, cb.getState());
        assertTrue(cb.allowExecution());
    }

    @Test
    @DisplayName("Should trip to OPEN when failure rate exceeds threshold")
    void testTripToOpenOnFailureRate() {
        CircuitBreakerConfig config =
                CircuitBreakerConfig.builder()
                        .failureRateThreshold(50.0f)
                        .slidingWindowSize(10)
                        .minimumNumberOfCalls(5)
                        .waitDurationInOpenStateMs(500L)
                        .build();

        ResilienceCircuitBreaker cb = new ResilienceCircuitBreaker("test-service", config);

        // Record 1 success, 4 failures (failure rate = 4/5 = 80% >= 50%)
        cb.recordSuccess(20);
        cb.recordFailure(20, new RuntimeException("error 1"));
        cb.recordFailure(20, new RuntimeException("error 2"));
        cb.recordFailure(20, new RuntimeException("error 3"));
        cb.recordFailure(20, new RuntimeException("error 4"));

        assertEquals(CircuitBreakerState.OPEN, cb.getState());
        assertFalse(cb.allowExecution(), "Call should be rejected fast when OPEN");
    }

    @Test
    @DisplayName("Should transition from OPEN to HALF_OPEN after cooldown and recover to CLOSED")
    void testRecoveryFromOpenToClosed() throws InterruptedException {
        CircuitBreakerConfig config =
                CircuitBreakerConfig.builder()
                        .failureRateThreshold(50.0f)
                        .slidingWindowSize(5)
                        .minimumNumberOfCalls(2)
                        .waitDurationInOpenStateMs(100L)
                        .permittedNumberOfCallsInHalfOpenState(2)
                        .build();

        ResilienceCircuitBreaker cb = new ResilienceCircuitBreaker("recovery-service", config);

        // Force trip to OPEN
        cb.recordFailure(10, new RuntimeException("fail"));
        cb.recordFailure(10, new RuntimeException("fail"));
        assertEquals(CircuitBreakerState.OPEN, cb.getState());

        // Wait for cooldown
        Thread.sleep(120);

        // Next check should transition to HALF_OPEN
        assertEquals(CircuitBreakerState.HALF_OPEN, cb.getState());
        assertTrue(cb.allowExecution());

        // Probe calls succeed
        cb.recordSuccess(10);
        cb.recordSuccess(10);

        // Circuit breaker should recover to CLOSED
        assertEquals(CircuitBreakerState.CLOSED, cb.getState());
        assertTrue(cb.allowExecution());
    }

    @Test
    @DisplayName("Should manage multiple circuit breakers via CircuitBreakerRegistry")
    void testCircuitBreakerRegistry() {
        CircuitBreakerRegistry registry = new CircuitBreakerRegistry();
        ResilienceCircuitBreaker authCb = registry.circuitBreaker("auth-service");
        ResilienceCircuitBreaker rideCb = registry.circuitBreaker("ride-service");

        assertNotNull(authCb);
        assertNotNull(rideCb);
        assertSame(authCb, registry.circuitBreaker("auth-service"));
        assertNotSame(authCb, rideCb);
    }
}
