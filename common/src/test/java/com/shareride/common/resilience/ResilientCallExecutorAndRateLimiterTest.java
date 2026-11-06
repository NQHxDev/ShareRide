package com.shareride.common.resilience;

import com.shareride.common.resilience.circuitbreaker.CircuitBreakerConfig;
import com.shareride.common.resilience.circuitbreaker.CircuitBreakerRegistry;
import com.shareride.common.resilience.executor.ResilientCallExecutor;
import com.shareride.common.resilience.ratelimit.DistributedRateLimiter;
import com.shareride.common.resilience.ratelimit.RateLimitResult;
import com.shareride.common.resilience.retry.RetryExecutor;
import com.shareride.common.resilience.timeout.TimeoutExecutor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ResilientCallExecutor and DistributedRateLimiter Tests")
class ResilientCallExecutorAndRateLimiterTest {

    @Test
    @DisplayName("ResilientCallExecutor should execute downstream call normally")
    void testExecuteNormally() {
        ResilientCallExecutor executor = new ResilientCallExecutor();
        String result = executor.execute("order-service", () -> "ORDER_CREATED");
        assertEquals("ORDER_CREATED", result);
    }

    @Test
    @DisplayName("ResilientCallExecutor should execute fallback when downstream call fails")
    void testExecuteFallbackOnFailure() {
        ResilientCallExecutor executor = new ResilientCallExecutor();
        String result = executor.execute(
                "failing-service",
                () -> { throw new RuntimeException("DB Connection Down"); },
                () -> "FALLBACK_CACHED_VALUE"
        );
        assertEquals("FALLBACK_CACHED_VALUE", result);
    }

    @Test
    @DisplayName("ResilientCallExecutor should fail-fast and invoke fallback when Circuit Breaker is OPEN")
    void testExecuteFallbackWhenCircuitOpen() {
        CircuitBreakerConfig config = CircuitBreakerConfig.builder()
                .failureRateThreshold(50.0f)
                .slidingWindowSize(5)
                .minimumNumberOfCalls(2)
                .waitDurationInOpenStateMs(10000L)
                .build();
        CircuitBreakerRegistry registry = new CircuitBreakerRegistry(config);
        registry.circuitBreaker("payment-service").transitionToOpen();

        ResilientCallExecutor executor = new ResilientCallExecutor(
                registry, new RetryExecutor(), new TimeoutExecutor(1000L), 20, 100L, 1000L);

        String result = executor.execute(
                "payment-service",
                () -> "SHOULD_NOT_BE_CALLED",
                () -> "PAYMENT_SERVICE_OFFLINE_FALLBACK"
        );
        assertEquals("PAYMENT_SERVICE_OFFLINE_FALLBACK", result);
    }

    @Test
    @DisplayName("DistributedRateLimiter should permit under limit and reject over limit in memory")
    void testRateLimiterPermitAndReject() {
        DistributedRateLimiter rateLimiter = new DistributedRateLimiter(); // in-memory fallback
        String clientIp = "192.168.1.100";

        // Limit: 3 requests per 10 seconds
        RateLimitResult r1 = rateLimiter.tryAcquire(clientIp, 3, 10);
        assertTrue(r1.isAllowed());
        assertEquals(2, r1.getRemainingTokens());

        RateLimitResult r2 = rateLimiter.tryAcquire(clientIp, 3, 10);
        assertTrue(r2.isAllowed());
        assertEquals(1, r2.getRemainingTokens());

        RateLimitResult r3 = rateLimiter.tryAcquire(clientIp, 3, 10);
        assertTrue(r3.isAllowed());
        assertEquals(0, r3.getRemainingTokens());

        // 4th request exceeds limit -> REJECTED
        RateLimitResult r4 = rateLimiter.tryAcquire(clientIp, 3, 10);
        assertFalse(r4.isAllowed());
        assertTrue(r4.getRetryAfterSeconds() > 0);
    }
}
