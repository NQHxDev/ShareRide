package com.shareride.common.resilience.executor;

import com.shareride.common.resilience.bulkhead.BulkheadFullException;
import com.shareride.common.resilience.bulkhead.BulkheadLimiter;
import com.shareride.common.resilience.circuitbreaker.CircuitBreakerOpenException;
import com.shareride.common.resilience.circuitbreaker.CircuitBreakerRegistry;
import com.shareride.common.resilience.circuitbreaker.ResilienceCircuitBreaker;
import com.shareride.common.resilience.retry.RetryExecutor;
import com.shareride.common.resilience.timeout.TimeoutExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Enterprise Resilience Orchestrator unifying: 1. Concurrency Bulkhead Isolation 2. Circuit Breaker
 * Fast-Failure & Healing 3. Exponential Backoff with Jitter Retry 4. Hard Timeout & Deadline
 * Enforcement 5. Graceful Fallback Handling
 */
public class ResilientCallExecutor {

    private static final Logger log = LoggerFactory.getLogger(ResilientCallExecutor.class);

    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RetryExecutor retryExecutor;
    private final TimeoutExecutor timeoutExecutor;
    private final int defaultBulkheadCapacity;
    private final long defaultBulkheadWaitMs;
    private final long defaultTimeoutMs;

    public ResilientCallExecutor() {
        this(
                new CircuitBreakerRegistry(),
                new RetryExecutor(),
                new TimeoutExecutor(3000L),
                50,
                250L,
                3000L);
    }

    public ResilientCallExecutor(
            CircuitBreakerRegistry circuitBreakerRegistry,
            RetryExecutor retryExecutor,
            TimeoutExecutor timeoutExecutor,
            int defaultBulkheadCapacity,
            long defaultBulkheadWaitMs,
            long defaultTimeoutMs) {
        this.circuitBreakerRegistry =
                circuitBreakerRegistry != null
                        ? circuitBreakerRegistry
                        : new CircuitBreakerRegistry();
        this.retryExecutor = retryExecutor != null ? retryExecutor : new RetryExecutor();
        this.timeoutExecutor =
                timeoutExecutor != null ? timeoutExecutor : new TimeoutExecutor(defaultTimeoutMs);
        this.defaultBulkheadCapacity = defaultBulkheadCapacity > 0 ? defaultBulkheadCapacity : 50;
        this.defaultBulkheadWaitMs = defaultBulkheadWaitMs >= 0 ? defaultBulkheadWaitMs : 250L;
        this.defaultTimeoutMs = defaultTimeoutMs > 0 ? defaultTimeoutMs : 3000L;
    }

    public CircuitBreakerRegistry getCircuitBreakerRegistry() {
        return circuitBreakerRegistry;
    }

    /**
     * Executes downstream synchronous call with full resilience guards (Bulkhead + CircuitBreaker +
     * Retry + Timeout).
     */
    public <T> T execute(String serviceName, Supplier<T> supplier) {
        return execute(serviceName, supplier, null);
    }

    /**
     * Executes downstream synchronous call with full resilience guards and a fallback. If the call
     * fails or Circuit Breaker is OPEN, the fallback is safely executed.
     */
    public <T> T execute(String serviceName, Supplier<T> supplier, Supplier<T> fallback) {
        ResilienceCircuitBreaker circuitBreaker =
                circuitBreakerRegistry.circuitBreaker(serviceName);
        BulkheadLimiter bulkhead =
                BulkheadLimiter.getOrCreate(
                        serviceName, defaultBulkheadCapacity, defaultBulkheadWaitMs);

        // 1. Check Circuit Breaker
        if (!circuitBreaker.allowExecution()) {
            log.warn(
                    "Circuit Breaker is OPEN for service [{}]. Rejecting call immediately.",
                    serviceName);
            if (fallback != null) {
                return fallback.get();
            }
            throw new CircuitBreakerOpenException(serviceName);
        }

        // 2. Acquire Bulkhead Permit
        if (!bulkhead.acquirePermit()) {
            log.warn(
                    "Bulkhead concurrency limit saturated for service [{}]. Rejecting call.",
                    serviceName);
            if (fallback != null) {
                return fallback.get();
            }
            throw new BulkheadFullException(serviceName, bulkhead.getMaxConcurrentCalls());
        }

        long start = System.currentTimeMillis();
        try {
            // 3. Execute with Retry & Timeout
            T result =
                    retryExecutor.execute(
                            serviceName,
                            () ->
                                    timeoutExecutor.execute(
                                            serviceName,
                                            supplier,
                                            defaultTimeoutMs,
                                            TimeUnit.MILLISECONDS));
            long latency = System.currentTimeMillis() - start;
            circuitBreaker.recordSuccess(latency);
            return result;
        } catch (Throwable t) {
            long latency = System.currentTimeMillis() - start;
            circuitBreaker.recordFailure(latency, t);

            if (fallback != null) {
                log.warn(
                        "Downstream service [{}] failed: {}. Executing fallback response.",
                        serviceName,
                        t.getMessage());
                return fallback.get();
            }

            if (t instanceof RuntimeException re) {
                throw re;
            }
            throw new RuntimeException(
                    "Resilient execution failed for service [" + serviceName + "]", t);
        } finally {
            bulkhead.releasePermit();
        }
    }
}
