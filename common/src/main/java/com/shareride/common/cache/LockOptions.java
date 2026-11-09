package com.shareride.common.cache;

import java.time.Duration;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

/**
 * Configuration options for distributed lock acquisition and lifecycle management. Supports
 * retries, blocking wait, fair FIFO ordering, and automatic background renewal (watchdog).
 */
@Getter
@Builder(toBuilder = true)
@ToString
public class LockOptions {

    public static final Duration DEFAULT_LEASE_TIME = Duration.ofSeconds(30);
    public static final Duration DEFAULT_WAIT_TIME = Duration.ZERO;
    public static final Duration DEFAULT_RETRY_DELAY = Duration.ofMillis(100);
    public static final Duration DEFAULT_POLL_INTERVAL = Duration.ofMillis(50);
    public static final Duration DEFAULT_QUEUE_TIMEOUT = Duration.ofMinutes(5);

    /** Maximum time to wait/block to acquire the lock. Default is Duration.ZERO (fail-fast). */
    @Builder.Default private Duration waitTime = DEFAULT_WAIT_TIME;

    /** Time-to-live for the lock once acquired. Default is 30 seconds. */
    @Builder.Default private Duration leaseTime = DEFAULT_LEASE_TIME;

    /** Number of retries when attempting non-blocking acquisition. Default is 0 (no retry). */
    @Builder.Default private int maxRetries = 0;

    /** Delay between retry attempts. Default is 100 milliseconds. */
    @Builder.Default private Duration retryDelay = DEFAULT_RETRY_DELAY;

    /**
     * Polling interval while blocking/waiting for lock availability. Default is 50 milliseconds.
     */
    @Builder.Default private Duration pollInterval = DEFAULT_POLL_INTERVAL;

    /** Whether this lock enforces strict FIFO fair ordering via Redis Sorted Set queue. */
    @Builder.Default private boolean fair = false;

    /** Whether to automatically renew the lock TTL in a background watchdog thread while held. */
    @Builder.Default private boolean autoRenew = false;

    /** Interval for automatic renewal heartbeat. If ZERO, defaults to leaseTime / 3. */
    @Builder.Default private Duration autoRenewInterval = Duration.ZERO;

    /** Stale waiter eviction timeout for fair lock queues. Default is 5 minutes. */
    @Builder.Default private Duration queueTimeout = DEFAULT_QUEUE_TIMEOUT;

    public static LockOptions defaults() {
        return LockOptions.builder().build();
    }

    public static LockOptions withLease(Duration leaseTime) {
        return LockOptions.builder().leaseTime(leaseTime).build();
    }

    public static LockOptions withWait(Duration waitTime, Duration leaseTime) {
        return LockOptions.builder().waitTime(waitTime).leaseTime(leaseTime).build();
    }

    public static LockOptions withRetry(int maxRetries, Duration retryDelay, Duration leaseTime) {
        return LockOptions.builder()
                .maxRetries(maxRetries)
                .retryDelay(retryDelay)
                .leaseTime(leaseTime)
                .build();
    }

    public static LockOptions fair(Duration waitTime, Duration leaseTime) {
        return LockOptions.builder().fair(true).waitTime(waitTime).leaseTime(leaseTime).build();
    }

    public static LockOptions autoRenew(Duration waitTime, Duration leaseTime) {
        return LockOptions.builder()
                .waitTime(waitTime)
                .leaseTime(leaseTime)
                .autoRenew(true)
                .build();
    }
}
