package com.shareride.common.cache;

import com.shareride.common.metrics.ShareRideMetrics;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

/**
 * Enterprise Redis Distributed Lock Service running on Shared Redis.
 *
 * <p>Advanced features:
 *
 * <ul>
 *   <li>Atomic acquire with expiration (SET key value NX PX)
 *   <li>Configurable retry mechanism with jittered backoff
 *   <li>Blocking wait with deadline timeout and customizable polling interval
 *   <li>Fair lock support ensuring strict FIFO request ordering via Redis Sorted Set queue
 *   <li>Lock extension (manual TTL refresh and automatic Watchdog heartbeat renewal)
 *   <li>Comprehensive Prometheus metrics tracking via {@link ShareRideMetrics}
 * </ul>
 */
@Slf4j
public class DistributedLockService implements DisposableBean {

    private static final String LOCK_KEY_PREFIX = "lock:";
    private static final String FAIR_LOCK_KEY_PREFIX = "lock:fair:";
    private static final String FAIR_QUEUE_KEY_PREFIX = "lock:fair:queue:";

    private static final String RELEASE_LOCK_LUA =
            "if redis.call('get', KEYS[1]) == ARGV[1] then "
                    + "    return redis.call('del', KEYS[1]) "
                    + "else "
                    + "    return 0 "
                    + "end";

    private static final String RENEW_LOCK_LUA =
            "if redis.call('get', KEYS[1]) == ARGV[1] then "
                    + "    return redis.call('pexpire', KEYS[1], ARGV[2]) "
                    + "else "
                    + "    return 0 "
                    + "end";

    private static final String FAIR_ACQUIRE_LUA =
            "local expireBefore = tonumber(ARGV[3]) - tonumber(ARGV[4]) "
                    + "redis.call('zremrangebyscore', KEYS[2], '-inf', expireBefore) "
                    + "local current = redis.call('get', KEYS[1]) "
                    + "if current == ARGV[1] then "
                    + "    redis.call('pexpire', KEYS[1], ARGV[2]) "
                    + "    redis.call('zrem', KEYS[2], ARGV[1]) "
                    + "    return 1 "
                    + "end "
                    + "local head = redis.call('zrange', KEYS[2], 0, 0) "
                    + "if not current then "
                    + "    if #head == 0 or head[1] == ARGV[1] then "
                    + "        redis.call('set', KEYS[1], ARGV[1], 'PX', ARGV[2]) "
                    + "        redis.call('zrem', KEYS[2], ARGV[1]) "
                    + "        return 1 "
                    + "    end "
                    + "end "
                    + "redis.call('zadd', KEYS[2], 'NX', ARGV[3], ARGV[1]) "
                    + "local queueTtl = math.max(60, math.ceil(tonumber(ARGV[4]) / 1000) * 2) "
                    + "redis.call('expire', KEYS[2], queueTtl) "
                    + "return 0";

    private static final String FAIR_RELEASE_LUA =
            "if redis.call('get', KEYS[1]) == ARGV[1] then "
                    + "    redis.call('del', KEYS[1]) "
                    + "    redis.call('zrem', KEYS[2], ARGV[1]) "
                    + "    return 1 "
                    + "else "
                    + "    redis.call('zrem', KEYS[2], ARGV[1]) "
                    + "    return 0 "
                    + "end";

    private static final String FAIR_CANCEL_LUA = "return redis.call('zrem', KEYS[1], ARGV[1])";

    private final RedisTemplate<String, Object> redisTemplate;
    private final ShareRideMetrics metrics;
    private final ScheduledExecutorService autoRenewScheduler;

    private final RedisScript<Long> releaseScript;
    private final RedisScript<Long> renewScript;
    private final RedisScript<Long> fairAcquireScript;
    private final RedisScript<Long> fairReleaseScript;
    private final RedisScript<Long> fairCancelScript;

    public DistributedLockService(RedisTemplate<String, Object> redisTemplate) {
        this(redisTemplate, null);
    }

    public DistributedLockService(
            RedisTemplate<String, Object> redisTemplate, ShareRideMetrics metrics) {
        this(
                redisTemplate,
                metrics,
                Executors.newScheduledThreadPool(
                        1,
                        r -> {
                            Thread thread = new Thread(r, "shareride-lock-renew");
                            thread.setDaemon(true);
                            return thread;
                        }));
    }

    DistributedLockService(
            RedisTemplate<String, Object> redisTemplate,
            ShareRideMetrics metrics,
            ScheduledExecutorService autoRenewScheduler) {
        this.redisTemplate = redisTemplate;
        this.metrics = metrics;
        this.autoRenewScheduler = autoRenewScheduler;

        this.releaseScript = createScript(RELEASE_LOCK_LUA);
        this.renewScript = createScript(RENEW_LOCK_LUA);
        this.fairAcquireScript = createScript(FAIR_ACQUIRE_LUA);
        this.fairReleaseScript = createScript(FAIR_RELEASE_LUA);
        this.fairCancelScript = createScript(FAIR_CANCEL_LUA);
    }

    // Standard Distributed Lock (Fail-Fast, Retries & Blocking Wait)

    /**
     * Attempts to acquire a distributed lock without retrying (fail-fast).
     *
     * @param lockKey Unique name of the lock
     * @param lockValue Owner token/UUID
     * @param expiration Time-to-live for the lock
     * @return true if lock acquired, false otherwise
     */
    public boolean acquireLock(String lockKey, String lockValue, Duration expiration) {
        return acquireLock(lockKey, lockValue, expiration, 0, Duration.ZERO);
    }

    /**
     * Attempts to acquire a distributed lock with retry mechanism and backoff delay.
     *
     * @param lockKey Unique name of the lock
     * @param lockValue Owner token/UUID
     * @param expiration Time-to-live for the lock
     * @param maxRetries Maximum number of retry attempts
     * @param retryDelay Delay between retries
     * @return true if acquired, false otherwise
     */
    public boolean acquireLock(
            String lockKey,
            String lockValue,
            Duration expiration,
            int maxRetries,
            Duration retryDelay) {
        LockOptions options =
                LockOptions.builder()
                        .leaseTime(expiration)
                        .maxRetries(maxRetries)
                        .retryDelay(retryDelay)
                        .build();
        return acquireLockInternal(lockKey, lockValue, options);
    }

    /**
     * Attempts to acquire a distributed lock with blocking wait until timeout.
     *
     * @param lockKey Unique name of the lock
     * @param lockValue Owner token/UUID
     * @param waitTime Maximum duration to block/wait for lock availability
     * @param leaseTime Time-to-live once acquired
     * @return true if acquired within waitTime, false otherwise
     */
    public boolean tryAcquireLock(
            String lockKey, String lockValue, Duration waitTime, Duration leaseTime) {
        return tryAcquireLock(
                lockKey, lockValue, waitTime, leaseTime, LockOptions.DEFAULT_POLL_INTERVAL);
    }

    /**
     * Attempts to acquire a distributed lock with blocking wait and customizable polling interval.
     */
    public boolean tryAcquireLock(
            String lockKey,
            String lockValue,
            Duration waitTime,
            Duration leaseTime,
            Duration pollInterval) {
        LockOptions options =
                LockOptions.builder()
                        .waitTime(waitTime)
                        .leaseTime(leaseTime)
                        .pollInterval(pollInterval)
                        .build();
        return tryAcquireLock(lockKey, lockValue, options);
    }

    /** Attempts to acquire a distributed lock based on comprehensive {@link LockOptions}. */
    public boolean tryAcquireLock(String lockKey, String lockValue, LockOptions options) {
        if (options.isFair()) {
            return tryAcquireFairLock(
                    lockKey,
                    lockValue,
                    options.getWaitTime(),
                    options.getLeaseTime(),
                    options.getPollInterval());
        }
        if (options.getWaitTime() == null
                || options.getWaitTime().isZero()
                || options.getWaitTime().isNegative()) {
            return acquireLockInternal(lockKey, lockValue, options);
        }

        String fullKey = LOCK_KEY_PREFIX + lockKey;
        long startNanos = System.nanoTime();
        long deadlineNanos = startNanos + options.getWaitTime().toNanos();
        Duration pollInterval =
                options.getPollInterval() != null
                        ? options.getPollInterval()
                        : LockOptions.DEFAULT_POLL_INTERVAL;

        while (true) {
            try {
                Boolean acquired =
                        redisTemplate
                                .opsForValue()
                                .setIfAbsent(fullKey, lockValue, options.getLeaseTime());
                if (Boolean.TRUE.equals(acquired)) {
                    Duration duration = Duration.ofNanos(System.nanoTime() - startNanos);
                    recordAcquireMetric(lockKey, "STANDARD", "SUCCESS", duration);
                    log.debug(
                            "Acquired distributed lock [{}] with token [{}] after wait",
                            lockKey,
                            lockValue);
                    return true;
                }
            } catch (Exception e) {
                log.warn(
                        "Error trying to acquire distributed lock [{}]: {}",
                        lockKey,
                        e.getMessage());
            }

            long remainingNanos = deadlineNanos - System.nanoTime();
            if (remainingNanos <= 0) {
                Duration duration = Duration.ofNanos(System.nanoTime() - startNanos);
                recordAcquireMetric(lockKey, "STANDARD", "TIMEOUT", duration);
                log.warn(
                        "Timed out waiting to acquire distributed lock [{}] after {}",
                        lockKey,
                        options.getWaitTime());
                return false;
            }

            long sleepNanos = Math.min(pollInterval.toNanos(), remainingNanos);
            try {
                TimeUnit.NANOSECONDS.sleep(sleepNanos);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Duration duration = Duration.ofNanos(System.nanoTime() - startNanos);
                recordAcquireMetric(lockKey, "STANDARD", "INTERRUPTED", duration);
                log.warn("Interrupted waiting to acquire distributed lock [{}]", lockKey);
                return false;
            }
        }
    }

    private boolean acquireLockInternal(String lockKey, String lockValue, LockOptions options) {
        String fullKey = LOCK_KEY_PREFIX + lockKey;
        long startNanos = System.nanoTime();
        int attempt = 0;
        int maxRetries = Math.max(0, options.getMaxRetries());
        Duration retryDelay =
                options.getRetryDelay() != null
                        ? options.getRetryDelay()
                        : LockOptions.DEFAULT_RETRY_DELAY;

        while (true) {
            try {
                Boolean acquired =
                        redisTemplate
                                .opsForValue()
                                .setIfAbsent(fullKey, lockValue, options.getLeaseTime());
                boolean success = Boolean.TRUE.equals(acquired);
                if (success) {
                    Duration duration = Duration.ofNanos(System.nanoTime() - startNanos);
                    recordAcquireMetric(lockKey, "STANDARD", "SUCCESS", duration);
                    log.debug(
                            "Acquired distributed lock [{}] with token [{}] (attempt {})",
                            lockKey,
                            lockValue,
                            attempt);
                    return true;
                }
            } catch (Exception e) {
                log.warn(
                        "Failed to acquire distributed lock [{}] on attempt {}: {}",
                        lockKey,
                        attempt,
                        e.getMessage());
            }

            if (attempt >= maxRetries) {
                Duration duration = Duration.ofNanos(System.nanoTime() - startNanos);
                recordAcquireMetric(lockKey, "STANDARD", "FAILURE", duration);
                return false;
            }

            attempt++;
            recordRetryMetric(lockKey, "STANDARD", attempt);
            if (!sleepWithJitter(retryDelay)) {
                return false;
            }
        }
    }

    // Fair Lock Support (FIFO Queue Ordering)

    /** Attempts to acquire a fair distributed lock without waiting. */
    public boolean acquireFairLock(String lockKey, String lockValue, Duration expiration) {
        return tryAcquireFairLock(
                lockKey, lockValue, Duration.ZERO, expiration, LockOptions.DEFAULT_POLL_INTERVAL);
    }

    /** Attempts to acquire a fair distributed lock with blocking wait. */
    public boolean tryAcquireFairLock(
            String lockKey, String lockValue, Duration waitTime, Duration leaseTime) {
        return tryAcquireFairLock(
                lockKey, lockValue, waitTime, leaseTime, LockOptions.DEFAULT_POLL_INTERVAL);
    }

    /**
     * Attempts to acquire a fair distributed lock enforcing FIFO request ordering via Redis Sorted
     * Set.
     */
    public boolean tryAcquireFairLock(
            String lockKey,
            String lockValue,
            Duration waitTime,
            Duration leaseTime,
            Duration pollInterval) {
        String lockRedisKey = FAIR_LOCK_KEY_PREFIX + lockKey;
        String queueRedisKey = FAIR_QUEUE_KEY_PREFIX + lockKey;
        List<String> keys = List.of(lockRedisKey, queueRedisKey);

        long startNanos = System.nanoTime();
        long waitTimeNanos = (waitTime != null && !waitTime.isNegative()) ? waitTime.toNanos() : 0L;
        long deadlineNanos = startNanos + waitTimeNanos;
        Duration effectivePoll =
                (pollInterval != null && !pollInterval.isNegative() && !pollInterval.isZero())
                        ? pollInterval
                        : LockOptions.DEFAULT_POLL_INTERVAL;
        long queueTimeoutMs = LockOptions.DEFAULT_QUEUE_TIMEOUT.toMillis();

        while (true) {
            long nowMs = System.currentTimeMillis();
            try {
                Long res =
                        redisTemplate.execute(
                                fairAcquireScript,
                                keys,
                                lockValue,
                                String.valueOf(leaseTime.toMillis()),
                                String.valueOf(nowMs),
                                String.valueOf(queueTimeoutMs));
                if (Long.valueOf(1L).equals(res)) {
                    Duration duration = Duration.ofNanos(System.nanoTime() - startNanos);
                    recordAcquireMetric(lockKey, "FAIR", "SUCCESS", duration);
                    log.debug(
                            "Acquired distributed FAIR lock [{}] with token [{}]",
                            lockKey,
                            lockValue);
                    return true;
                }
            } catch (Exception e) {
                log.warn("Error acquiring fair lock [{}]: {}", lockKey, e.getMessage());
            }

            long remainingNanos = deadlineNanos - System.nanoTime();
            if (remainingNanos <= 0) {
                cancelFairWait(lockKey, lockValue);
                Duration duration = Duration.ofNanos(System.nanoTime() - startNanos);
                String status = waitTimeNanos > 0 ? "TIMEOUT" : "FAILURE";
                recordAcquireMetric(lockKey, "FAIR", status, duration);
                return false;
            }

            long sleepNanos = Math.min(effectivePoll.toNanos(), remainingNanos);
            try {
                TimeUnit.NANOSECONDS.sleep(sleepNanos);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                cancelFairWait(lockKey, lockValue);
                Duration duration = Duration.ofNanos(System.nanoTime() - startNanos);
                recordAcquireMetric(lockKey, "FAIR", "INTERRUPTED", duration);
                return false;
            }
        }
    }

    private void cancelFairWait(String lockKey, String lockValue) {
        try {
            String queueRedisKey = FAIR_QUEUE_KEY_PREFIX + lockKey;
            redisTemplate.execute(fairCancelScript, List.of(queueRedisKey), lockValue);
        } catch (Exception e) {
            log.warn(
                    "Failed to cancel fair lock wait queue for key [{}]: {}",
                    lockKey,
                    e.getMessage());
        }
    }

    /** Atomically releases the fair distributed lock only if the token matches. */
    public boolean releaseFairLock(String lockKey, String lockValue) {
        String lockRedisKey = FAIR_LOCK_KEY_PREFIX + lockKey;
        String queueRedisKey = FAIR_QUEUE_KEY_PREFIX + lockKey;
        try {
            Long result =
                    redisTemplate.execute(
                            fairReleaseScript, List.of(lockRedisKey, queueRedisKey), lockValue);
            boolean released = Long.valueOf(1L).equals(result);
            recordReleaseMetric(lockKey, "FAIR", released ? "SUCCESS" : "MISMATCH");
            if (released) {
                log.debug("Released distributed FAIR lock [{}]", lockKey);
            } else {
                log.warn(
                        "Could not release fair lock [{}] - lock expired or token mismatch",
                        lockKey);
            }
            return released;
        } catch (Exception e) {
            recordReleaseMetric(lockKey, "FAIR", "ERROR");
            log.error("Error releasing distributed fair lock [{}]: {}", lockKey, e.getMessage());
            return false;
        }
    }

    // Lock Release & Lock Extension (TTL Refresh)

    /**
     * Atomically releases the distributed lock only if the token matches.
     *
     * @param lockKey Unique name of the lock
     * @param lockValue Owner token used during acquisition
     * @return true if released, false if lock not held or token mismatch
     */
    public boolean releaseLock(String lockKey, String lockValue) {
        String fullKey = LOCK_KEY_PREFIX + lockKey;
        try {
            Long result =
                    redisTemplate.execute(
                            releaseScript, Collections.singletonList(fullKey), lockValue);
            boolean released = Long.valueOf(1L).equals(result);
            recordReleaseMetric(lockKey, "STANDARD", released ? "SUCCESS" : "MISMATCH");
            if (released) {
                log.debug("Released distributed lock [{}]", lockKey);
            } else {
                log.warn("Could not release lock [{}] - lock expired or token mismatch", lockKey);
            }
            return released;
        } catch (Exception e) {
            recordReleaseMetric(lockKey, "STANDARD", "ERROR");
            log.error("Error releasing distributed lock [{}]: {}", lockKey, e.getMessage());
            return false;
        }
    }

    /**
     * Atomically extends the expiration time of an existing standard lock.
     *
     * @param lockKey Unique name of the lock
     * @param lockValue Owner token used during acquisition
     * @param extension Duration to extend the lock TTL
     * @return true if extended, false if lock not held or token mismatch
     */
    public boolean renewLock(String lockKey, String lockValue, Duration extension) {
        String fullKey = LOCK_KEY_PREFIX + lockKey;
        try {
            Long result =
                    redisTemplate.execute(
                            renewScript,
                            Collections.singletonList(fullKey),
                            lockValue,
                            String.valueOf(extension.toMillis()));
            boolean renewed = Long.valueOf(1L).equals(result);
            recordRenewMetric(lockKey, renewed);
            if (renewed) {
                log.debug(
                        "Renewed lock [{}] for token [{}] with TTL {} ms",
                        lockKey,
                        lockValue,
                        extension.toMillis());
            } else {
                log.warn("Failed to renew lock [{}] - lock expired or token mismatch", lockKey);
            }
            return renewed;
        } catch (Exception e) {
            recordRenewMetric(lockKey, false);
            log.error("Error renewing lock [{}]: {}", lockKey, e.getMessage());
            return false;
        }
    }

    /** Atomically extends the expiration time of an existing fair lock. */
    public boolean renewFairLock(String lockKey, String lockValue, Duration extension) {
        String lockRedisKey = FAIR_LOCK_KEY_PREFIX + lockKey;
        try {
            Long result =
                    redisTemplate.execute(
                            renewScript,
                            Collections.singletonList(lockRedisKey),
                            lockValue,
                            String.valueOf(extension.toMillis()));
            boolean renewed = Long.valueOf(1L).equals(result);
            recordRenewMetric(lockKey, renewed);
            if (renewed) {
                log.debug(
                        "Renewed fair lock [{}] for token [{}] with TTL {} ms",
                        lockKey,
                        lockValue,
                        extension.toMillis());
            } else {
                log.warn(
                        "Failed to renew fair lock [{}] - lock expired or token mismatch", lockKey);
            }
            return renewed;
        } catch (Exception e) {
            recordRenewMetric(lockKey, false);
            log.error("Error renewing fair lock [{}]: {}", lockKey, e.getMessage());
            return false;
        }
    }

    // LockHandle & Managed Boundaries (Try-With-Resources & Auto-Renewal)

    /**
     * Acquires a managed {@link LockHandle}. Supports try-with-resources and automatic background
     * watchdog heartbeat renewal when {@link LockOptions#isAutoRenew()} is enabled.
     */
    public LockHandle acquireLockHandle(String lockKey, LockOptions options) {
        String lockToken = UUID.randomUUID().toString();
        boolean acquired =
                options.isFair()
                        ? tryAcquireFairLock(
                                lockKey,
                                lockToken,
                                options.getWaitTime(),
                                options.getLeaseTime(),
                                options.getPollInterval())
                        : tryAcquireLock(lockKey, lockToken, options);

        if (!acquired) {
            return new FailedLockHandle(
                    lockKey, lockToken, options.isFair(), options.getLeaseTime());
        }

        ScheduledFuture<?> autoRenewFuture = null;
        if (options.isAutoRenew()) {
            Duration interval =
                    (options.getAutoRenewInterval() != null
                                    && !options.getAutoRenewInterval().isZero())
                            ? options.getAutoRenewInterval()
                            : Duration.ofMillis(
                                    Math.max(100, options.getLeaseTime().toMillis() / 3));

            autoRenewFuture =
                    autoRenewScheduler.scheduleAtFixedRate(
                            () -> {
                                try {
                                    boolean ok =
                                            options.isFair()
                                                    ? renewFairLock(
                                                            lockKey,
                                                            lockToken,
                                                            options.getLeaseTime())
                                                    : renewLock(
                                                            lockKey,
                                                            lockToken,
                                                            options.getLeaseTime());
                                    if (!ok) {
                                        log.warn(
                                                "Auto-renewal failed for lock [{}]: token mismatch"
                                                        + " or expired",
                                                lockKey);
                                    }
                                } catch (Exception e) {
                                    log.warn(
                                            "Auto-renewal exception for lock [{}]: {}",
                                            lockKey,
                                            e.getMessage());
                                }
                            },
                            interval.toMillis(),
                            interval.toMillis(),
                            TimeUnit.MILLISECONDS);
        }

        return new ActiveLockHandle(
                lockKey, lockToken, options, autoRenewFuture, System.nanoTime());
    }

    // Execution Boundaries

    /**
     * Executes a task within a distributed lock boundary.
     *
     * @param lockKey Unique lock key
     * @param expiration Max duration to hold the lock
     * @param task Action to execute if lock acquired
     * @return Task result
     * @throws LockAcquisitionException if lock could not be acquired
     */
    public <T> T executeWithLock(String lockKey, Duration expiration, Supplier<T> task) {
        return executeWithLock(lockKey, LockOptions.withLease(expiration), task);
    }

    /** Executes a runnable task within a distributed lock boundary. */
    public void executeWithLock(String lockKey, Duration expiration, Runnable task) {
        executeWithLock(
                lockKey,
                expiration,
                () -> {
                    task.run();
                    return null;
                });
    }

    /** Executes a task with blocking wait and lease time. */
    public <T> T executeWithLock(
            String lockKey, Duration waitTime, Duration leaseTime, Supplier<T> task) {
        return executeWithLock(lockKey, LockOptions.withWait(waitTime, leaseTime), task);
    }

    /** Executes a runnable task with blocking wait and lease time. */
    public void executeWithLock(
            String lockKey, Duration waitTime, Duration leaseTime, Runnable task) {
        executeWithLock(
                lockKey,
                waitTime,
                leaseTime,
                () -> {
                    task.run();
                    return null;
                });
    }

    /** Executes a task with comprehensive {@link LockOptions}. */
    public <T> T executeWithLock(String lockKey, LockOptions options, Supplier<T> task) {
        try (LockHandle handle = acquireLockHandle(lockKey, options)) {
            if (!handle.isAcquired()) {
                throw new LockAcquisitionException(
                        "Failed to acquire distributed lock for key: " + lockKey);
            }
            return task.get();
        }
    }

    /** Executes a runnable task with comprehensive {@link LockOptions}. */
    public void executeWithLock(String lockKey, LockOptions options, Runnable task) {
        executeWithLock(
                lockKey,
                options,
                () -> {
                    task.run();
                    return null;
                });
    }

    /** Executes a task within a fair FIFO distributed lock boundary. */
    public <T> T executeWithFairLock(String lockKey, Duration expiration, Supplier<T> task) {
        return executeWithLock(lockKey, LockOptions.fair(Duration.ZERO, expiration), task);
    }

    /** Executes a task within a fair FIFO distributed lock boundary with wait time. */
    public <T> T executeWithFairLock(
            String lockKey, Duration waitTime, Duration leaseTime, Supplier<T> task) {
        return executeWithLock(lockKey, LockOptions.fair(waitTime, leaseTime), task);
    }

    /** Executes a runnable task within a fair FIFO distributed lock boundary with wait time. */
    public void executeWithFairLock(
            String lockKey, Duration waitTime, Duration leaseTime, Runnable task) {
        executeWithFairLock(
                lockKey,
                waitTime,
                leaseTime,
                () -> {
                    task.run();
                    return null;
                });
    }

    @Override
    public void destroy() {
        if (autoRenewScheduler != null && !autoRenewScheduler.isShutdown()) {
            autoRenewScheduler.shutdown();
            try {
                if (!autoRenewScheduler.awaitTermination(2, TimeUnit.SECONDS)) {
                    autoRenewScheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                autoRenewScheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    // Internal Helpers & LockHandle Implementations

    private boolean sleepWithJitter(Duration delay) {
        if (delay == null || delay.isZero() || delay.isNegative()) {
            return true;
        }
        long millis = delay.toMillis();
        long jitter = (long) (millis * 0.2 * (ThreadLocalRandom.current().nextDouble() * 2 - 1));
        long sleepTime = Math.max(1, millis + jitter);
        try {
            Thread.sleep(sleepTime);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Lock acquire sleep interrupted");
            return false;
        }
    }

    private static RedisScript<Long> createScript(String lua) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(lua);
        script.setResultType(Long.class);
        return script;
    }

    private void recordAcquireMetric(
            String lockKey, String lockType, String status, Duration duration) {
        if (metrics != null) {
            metrics.recordLockAcquire(lockKey, lockType, status, duration);
        }
    }

    private void recordHoldMetric(String lockKey, String lockType, Duration duration) {
        if (metrics != null) {
            metrics.recordLockHold(lockKey, lockType, duration);
        }
    }

    private void recordReleaseMetric(String lockKey, String lockType, String status) {
        if (metrics != null) {
            metrics.recordLockRelease(lockKey, lockType, status);
        }
    }

    private void recordRenewMetric(String lockKey, boolean success) {
        if (metrics != null) {
            metrics.recordLockRenewal(lockKey, success);
        }
    }

    private void recordRetryMetric(String lockKey, String lockType, int attempt) {
        if (metrics != null) {
            metrics.recordLockRetry(lockKey, lockType, attempt);
        }
    }

    private class ActiveLockHandle implements LockHandle {
        private final String lockKey;
        private final String lockToken;
        private final LockOptions options;
        private final ScheduledFuture<?> autoRenewFuture;
        private final long acquiredAtNanos;
        private final AtomicBoolean closed = new AtomicBoolean(false);

        ActiveLockHandle(
                String lockKey,
                String lockToken,
                LockOptions options,
                ScheduledFuture<?> autoRenewFuture,
                long acquiredAtNanos) {
            this.lockKey = lockKey;
            this.lockToken = lockToken;
            this.options = options;
            this.autoRenewFuture = autoRenewFuture;
            this.acquiredAtNanos = acquiredAtNanos;
        }

        @Override
        public String getLockKey() {
            return lockKey;
        }

        @Override
        public String getLockToken() {
            return lockToken;
        }

        @Override
        public boolean isAcquired() {
            return !closed.get();
        }

        @Override
        public boolean isFair() {
            return options.isFair();
        }

        @Override
        public Duration getLeaseTime() {
            return options.getLeaseTime();
        }

        @Override
        public boolean renew(Duration extension) {
            if (closed.get()) {
                return false;
            }
            return options.isFair()
                    ? renewFairLock(lockKey, lockToken, extension)
                    : renewLock(lockKey, lockToken, extension);
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                if (autoRenewFuture != null) {
                    autoRenewFuture.cancel(false);
                }
                long holdNanos = System.nanoTime() - acquiredAtNanos;
                recordHoldMetric(
                        lockKey,
                        options.isFair() ? "FAIR" : "STANDARD",
                        Duration.ofNanos(holdNanos));
                if (options.isFair()) {
                    releaseFairLock(lockKey, lockToken);
                } else {
                    releaseLock(lockKey, lockToken);
                }
            }
        }
    }

    private static class FailedLockHandle implements LockHandle {
        private final String lockKey;
        private final String lockToken;
        private final boolean fair;
        private final Duration leaseTime;

        FailedLockHandle(String lockKey, String lockToken, boolean fair, Duration leaseTime) {
            this.lockKey = lockKey;
            this.lockToken = lockToken;
            this.fair = fair;
            this.leaseTime = leaseTime;
        }

        @Override
        public String getLockKey() {
            return lockKey;
        }

        @Override
        public String getLockToken() {
            return lockToken;
        }

        @Override
        public boolean isAcquired() {
            return false;
        }

        @Override
        public boolean isFair() {
            return fair;
        }

        @Override
        public Duration getLeaseTime() {
            return leaseTime;
        }

        @Override
        public boolean renew(Duration extension) {
            return false;
        }

        @Override
        public void close() {
            // No-op
        }
    }
}
