package com.shareride.common.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shareride.common.metrics.ShareRideMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class DistributedLockServiceTest {

    @Mock private RedisTemplate<String, Object> redisTemplate;

    @Mock private ValueOperations<String, Object> valueOperations;

    private ShareRideMetrics metrics;
    private SimpleMeterRegistry meterRegistry;
    private ScheduledExecutorService scheduler;
    private DistributedLockService lockService;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        meterRegistry = new SimpleMeterRegistry();
        metrics = new ShareRideMetrics(meterRegistry);
        scheduler = Executors.newScheduledThreadPool(2);
        lockService = new DistributedLockService(redisTemplate, metrics, scheduler);
    }

    @AfterEach
    void tearDown() {
        if (lockService != null) {
            lockService.destroy();
        }
    }

    // 1. Standard Fail-Fast Lock

    @Test
    @DisplayName("Should successfully acquire lock when key is absent")
    void testAcquireLockSuccess() {
        when(valueOperations.setIfAbsent(
                        eq("lock:ride:order-1"), eq("token-abc"), eq(Duration.ofSeconds(10))))
                .thenReturn(Boolean.TRUE);

        boolean acquired =
                lockService.acquireLock("ride:order-1", "token-abc", Duration.ofSeconds(10));
        assertTrue(acquired);

        assertNotNull(
                meterRegistry
                        .find("shareride.lock.acquire.total")
                        .tag("name", "ride:order-1")
                        .tag("type", "STANDARD")
                        .tag("status", "SUCCESS")
                        .counter());
    }

    @Test
    @DisplayName("Should fail to acquire lock when key already exists")
    void testAcquireLockFailure() {
        when(valueOperations.setIfAbsent(
                        eq("lock:ride:order-1"), eq("token-abc"), eq(Duration.ofSeconds(10))))
                .thenReturn(Boolean.FALSE);

        boolean acquired =
                lockService.acquireLock("ride:order-1", "token-abc", Duration.ofSeconds(10));
        assertFalse(acquired);

        assertNotNull(
                meterRegistry
                        .find("shareride.lock.acquire.total")
                        .tag("name", "ride:order-1")
                        .tag("type", "STANDARD")
                        .tag("status", "FAILURE")
                        .counter());
    }

    // 2. Retry Mechanism

    @Test
    @DisplayName("Should successfully acquire lock on retry attempt")
    void testAcquireLockRetrySuccess() {
        when(valueOperations.setIfAbsent(
                        eq("lock:payment:tx-100"), eq("token-123"), eq(Duration.ofSeconds(5))))
                .thenReturn(Boolean.FALSE)
                .thenReturn(Boolean.TRUE);

        boolean acquired =
                lockService.acquireLock(
                        "payment:tx-100",
                        "token-123",
                        Duration.ofSeconds(5),
                        2,
                        Duration.ofMillis(10));
        assertTrue(acquired);

        verify(valueOperations, times(2))
                .setIfAbsent(anyString(), anyString(), any(Duration.class));
        assertNotNull(
                meterRegistry
                        .find("shareride.lock.retries.total")
                        .tag("name", "payment:tx-100")
                        .tag("type", "STANDARD")
                        .tag("attempt", "1")
                        .counter());
    }

    @Test
    @DisplayName("Should fail when all retries are exhausted")
    void testAcquireLockRetryExhausted() {
        when(valueOperations.setIfAbsent(
                        eq("lock:payment:tx-100"), eq("token-123"), eq(Duration.ofSeconds(5))))
                .thenReturn(Boolean.FALSE);

        boolean acquired =
                lockService.acquireLock(
                        "payment:tx-100",
                        "token-123",
                        Duration.ofSeconds(5),
                        2,
                        Duration.ofMillis(5));
        assertFalse(acquired);

        verify(valueOperations, times(3))
                .setIfAbsent(anyString(), anyString(), any(Duration.class));
        assertNotNull(
                meterRegistry
                        .find("shareride.lock.acquire.total")
                        .tag("name", "payment:tx-100")
                        .tag("type", "STANDARD")
                        .tag("status", "FAILURE")
                        .counter());
    }

    // 3. Wait/Block Option

    @Test
    @DisplayName("Should acquire lock with blocking wait when it becomes available")
    void testTryAcquireLockWithWaitSuccess() {
        when(valueOperations.setIfAbsent(
                        eq("lock:order:dispatch"), eq("token-w1"), eq(Duration.ofSeconds(10))))
                .thenReturn(Boolean.FALSE)
                .thenReturn(Boolean.TRUE);

        boolean acquired =
                lockService.tryAcquireLock(
                        "order:dispatch",
                        "token-w1",
                        Duration.ofMillis(200),
                        Duration.ofSeconds(10),
                        Duration.ofMillis(20));
        assertTrue(acquired);

        assertNotNull(
                meterRegistry
                        .find("shareride.lock.acquire.total")
                        .tag("name", "order:dispatch")
                        .tag("type", "STANDARD")
                        .tag("status", "SUCCESS")
                        .counter());
    }

    @Test
    @DisplayName("Should timeout when blocking wait exceeds waitTime")
    void testTryAcquireLockWithWaitTimeout() {
        when(valueOperations.setIfAbsent(
                        eq("lock:order:dispatch"), eq("token-w1"), eq(Duration.ofSeconds(10))))
                .thenReturn(Boolean.FALSE);

        boolean acquired =
                lockService.tryAcquireLock(
                        "order:dispatch",
                        "token-w1",
                        Duration.ofMillis(50),
                        Duration.ofSeconds(10),
                        Duration.ofMillis(15));
        assertFalse(acquired);

        assertNotNull(
                meterRegistry
                        .find("shareride.lock.acquire.total")
                        .tag("name", "order:dispatch")
                        .tag("type", "STANDARD")
                        .tag("status", "TIMEOUT")
                        .counter());
    }

    // 4. Lock Extension (TTL Refresh)

    @Test
    @DisplayName("Should renew standard lock when Lua script returns 1")
    void testRenewLockSuccess() {
        when(redisTemplate.execute(
                        any(),
                        eq(Collections.singletonList("lock:task:1")),
                        eq("token-r1"),
                        eq("15000")))
                .thenReturn(1L);

        boolean renewed = lockService.renewLock("task:1", "token-r1", Duration.ofSeconds(15));
        assertTrue(renewed);

        assertNotNull(
                meterRegistry
                        .find("shareride.lock.renew.total")
                        .tag("name", "task:1")
                        .tag("status", "SUCCESS")
                        .counter());
    }

    @Test
    @DisplayName("Should fail to renew lock when token mismatch or lock expired (Lua returns 0)")
    void testRenewLockFailure() {
        when(redisTemplate.execute(
                        any(),
                        eq(Collections.singletonList("lock:task:1")),
                        eq("wrong-token"),
                        eq("15000")))
                .thenReturn(0L);

        boolean renewed = lockService.renewLock("task:1", "wrong-token", Duration.ofSeconds(15));
        assertFalse(renewed);

        assertNotNull(
                meterRegistry
                        .find("shareride.lock.renew.total")
                        .tag("name", "task:1")
                        .tag("status", "FAILURE")
                        .counter());
    }

    @Test
    @DisplayName("Should renew fair lock when Lua script returns 1")
    void testRenewFairLockSuccess() {
        when(redisTemplate.execute(
                        any(),
                        eq(Collections.singletonList("lock:fair:queue-job")),
                        eq("token-fair"),
                        eq("20000")))
                .thenReturn(1L);

        boolean renewed =
                lockService.renewFairLock("queue-job", "token-fair", Duration.ofSeconds(20));
        assertTrue(renewed);
    }

    // 5. Fair Lock Support (FIFO Queue)

    @Test
    @DisplayName("Should successfully acquire fair lock when caller is next in FIFO queue")
    void testAcquireFairLockSuccess() {
        when(redisTemplate.execute(
                        any(),
                        eq(List.of("lock:fair:matching", "lock:fair:queue:matching")),
                        eq("token-f1"),
                        anyString(),
                        anyString(),
                        anyString()))
                .thenReturn(1L);

        boolean acquired =
                lockService.acquireFairLock("matching", "token-f1", Duration.ofSeconds(30));
        assertTrue(acquired);

        assertNotNull(
                meterRegistry
                        .find("shareride.lock.acquire.total")
                        .tag("name", "matching")
                        .tag("type", "FAIR")
                        .tag("status", "SUCCESS")
                        .counter());
    }

    @Test
    @DisplayName("Should fail and cancel queue entry when tryAcquireFairLock times out")
    void testTryAcquireFairLockTimeout() {
        when(redisTemplate.execute(
                        any(),
                        eq(List.of("lock:fair:matching", "lock:fair:queue:matching")),
                        eq("token-f1"),
                        anyString(),
                        anyString(),
                        anyString()))
                .thenReturn(0L);
        when(redisTemplate.execute(any(), eq(List.of("lock:fair:queue:matching")), eq("token-f1")))
                .thenReturn(1L);

        boolean acquired =
                lockService.tryAcquireFairLock(
                        "matching",
                        "token-f1",
                        Duration.ofMillis(40),
                        Duration.ofSeconds(30),
                        Duration.ofMillis(15));
        assertFalse(acquired);

        verify(redisTemplate, atLeastOnce())
                .execute(any(), eq(List.of("lock:fair:queue:matching")), eq("token-f1"));
        assertNotNull(
                meterRegistry
                        .find("shareride.lock.acquire.total")
                        .tag("name", "matching")
                        .tag("type", "FAIR")
                        .tag("status", "TIMEOUT")
                        .counter());
    }

    @Test
    @DisplayName("Should release fair lock atomically and update metrics")
    void testReleaseFairLockSuccess() {
        when(redisTemplate.execute(
                        any(),
                        eq(List.of("lock:fair:matching", "lock:fair:queue:matching")),
                        eq("token-f1")))
                .thenReturn(1L);

        boolean released = lockService.releaseFairLock("matching", "token-f1");
        assertTrue(released);

        assertNotNull(
                meterRegistry
                        .find("shareride.lock.release.total")
                        .tag("name", "matching")
                        .tag("type", "FAIR")
                        .tag("status", "SUCCESS")
                        .counter());
    }

    // 6. Release Lock

    @Test
    @DisplayName("Should release standard lock when Lua script returns 1")
    void testReleaseLockSuccess() {
        when(redisTemplate.execute(
                        any(), eq(Collections.singletonList("lock:ride:order-1")), eq("token-abc")))
                .thenReturn(1L);

        boolean released = lockService.releaseLock("ride:order-1", "token-abc");
        assertTrue(released);

        assertNotNull(
                meterRegistry
                        .find("shareride.lock.release.total")
                        .tag("name", "ride:order-1")
                        .tag("type", "STANDARD")
                        .tag("status", "SUCCESS")
                        .counter());
    }

    @Test
    @DisplayName("Should fail release lock when token mismatch or lock expired (Lua returns 0)")
    void testReleaseLockMismatch() {
        when(redisTemplate.execute(
                        any(),
                        eq(Collections.singletonList("lock:ride:order-1")),
                        eq("wrong-token")))
                .thenReturn(0L);

        boolean released = lockService.releaseLock("ride:order-1", "wrong-token");
        assertFalse(released);

        assertNotNull(
                meterRegistry
                        .find("shareride.lock.release.total")
                        .tag("name", "ride:order-1")
                        .tag("type", "STANDARD")
                        .tag("status", "MISMATCH")
                        .counter());
    }

    // 7. Execution Boundaries & LockHandle (Auto-Renewal Watchdog)

    @Test
    @DisplayName("executeWithLock should execute supplier and release lock afterwards")
    void testExecuteWithLockSuccess() {
        when(valueOperations.setIfAbsent(
                        startsWith("lock:driver:matching"), anyString(), any(Duration.class)))
                .thenReturn(Boolean.TRUE);
        when(redisTemplate.execute(any(), anyList(), anyString())).thenReturn(1L);

        String result =
                lockService.executeWithLock(
                        "driver:matching", Duration.ofSeconds(5), () -> "RIDE_MATCHED");
        assertEquals("RIDE_MATCHED", result);

        verify(redisTemplate, times(1)).execute(any(), anyList(), anyString());
        assertNotNull(
                meterRegistry
                        .find("shareride.lock.hold.duration")
                        .tag("name", "driver:matching")
                        .tag("type", "STANDARD")
                        .timer());
    }

    @Test
    @DisplayName(
            "executeWithLock should throw LockAcquisitionException when lock cannot be acquired")
    void testExecuteWithLockThrowsWhenAcquireFails() {
        when(valueOperations.setIfAbsent(
                        startsWith("lock:driver:matching"), anyString(), any(Duration.class)))
                .thenReturn(Boolean.FALSE);

        assertThrows(
                LockAcquisitionException.class,
                () ->
                        lockService.executeWithLock(
                                "driver:matching", Duration.ofSeconds(5), () -> "SHOULD_FAIL"));
    }

    @Test
    @DisplayName("executeWithLock with wait and lease parameters")
    void testExecuteWithLockWaitAndLease() {
        when(valueOperations.setIfAbsent(
                        startsWith("lock:driver:matching"), anyString(), any(Duration.class)))
                .thenReturn(Boolean.TRUE);
        when(redisTemplate.execute(any(), anyList(), anyString())).thenReturn(1L);

        AtomicBoolean executed = new AtomicBoolean(false);
        lockService.executeWithLock(
                "driver:matching",
                Duration.ofSeconds(1),
                Duration.ofSeconds(5),
                () -> {
                    executed.set(true);
                });

        assertTrue(executed.get());
    }

    @Test
    @DisplayName("executeWithFairLock should execute task and release fair lock")
    void testExecuteWithFairLockSuccess() {
        when(redisTemplate.execute(
                        any(),
                        eq(List.of("lock:fair:ticket:1", "lock:fair:queue:ticket:1")),
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString()))
                .thenReturn(1L);
        when(redisTemplate.execute(
                        any(),
                        eq(List.of("lock:fair:ticket:1", "lock:fair:queue:ticket:1")),
                        anyString()))
                .thenReturn(1L);

        String res =
                lockService.executeWithFairLock(
                        "ticket:1",
                        Duration.ofSeconds(1),
                        Duration.ofSeconds(10),
                        () -> "TICKET_BOOKED");
        assertEquals("TICKET_BOOKED", res);

        verify(redisTemplate, times(1))
                .execute(
                        any(),
                        eq(List.of("lock:fair:ticket:1", "lock:fair:queue:ticket:1")),
                        anyString());
    }

    @Test
    @DisplayName(
            "acquireLockHandle with autoRenew should trigger periodic renewal and stop upon close")
    void testLockHandleAutoRenewWatchdog() throws Exception {
        when(valueOperations.setIfAbsent(
                        startsWith("lock:long:job"), anyString(), any(Duration.class)))
                .thenReturn(Boolean.TRUE);
        when(redisTemplate.execute(any(), anyList(), anyString(), anyString())).thenReturn(1L);
        when(redisTemplate.execute(any(), anyList(), anyString())).thenReturn(1L);

        LockOptions options =
                LockOptions.builder()
                        .leaseTime(Duration.ofMillis(300))
                        .autoRenew(true)
                        .autoRenewInterval(Duration.ofMillis(50))
                        .build();

        try (LockHandle handle = lockService.acquireLockHandle("long:job", options)) {
            assertTrue(handle.isAcquired());
            assertFalse(handle.isFair());
            Thread.sleep(160); // Allow at least 2 renewals to execute
        }

        verify(redisTemplate, atLeast(2)).execute(any(), anyList(), anyString(), anyString());
        verify(redisTemplate, times(1)).execute(any(), anyList(), anyString()); // release
    }
}
