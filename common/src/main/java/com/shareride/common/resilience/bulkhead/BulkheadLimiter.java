package com.shareride.common.resilience.bulkhead;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Concurrency bulkhead limiter isolating thread pool exhaustion per target service.
 */
public class BulkheadLimiter {

    private static final Logger log = LoggerFactory.getLogger(BulkheadLimiter.class);

    private final String serviceName;
    private final int maxConcurrentCalls;
    private final long maxWaitMs;
    private final Semaphore semaphore;

    private static final ConcurrentMap<String, BulkheadLimiter> BULKHEADS = new ConcurrentHashMap<>();

    public BulkheadLimiter(String serviceName, int maxConcurrentCalls, long maxWaitMs) {
        this.serviceName = serviceName;
        this.maxConcurrentCalls = maxConcurrentCalls > 0 ? maxConcurrentCalls : 50;
        this.maxWaitMs = maxWaitMs >= 0 ? maxWaitMs : 250L;
        this.semaphore = new Semaphore(this.maxConcurrentCalls, true);
    }

    public static BulkheadLimiter getOrCreate(String serviceName, int maxConcurrentCalls, long maxWaitMs) {
        return BULKHEADS.computeIfAbsent(serviceName, key -> new BulkheadLimiter(key, maxConcurrentCalls, maxWaitMs));
    }

    public String getServiceName() {
        return serviceName;
    }

    public int getMaxConcurrentCalls() {
        return maxConcurrentCalls;
    }

    public int getAvailablePermits() {
        return semaphore.availablePermits();
    }

    /**
     * Attempts to acquire a concurrency permit within allowed wait time.
     */
    public boolean acquirePermit() {
        try {
            return semaphore.tryAcquire(maxWaitMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while waiting for bulkhead permit for [{}]", serviceName);
            return false;
        }
    }

    /**
     * Releases the acquired permit back to the bulkhead pool.
     */
    public void releasePermit() {
        semaphore.release();
    }
}
