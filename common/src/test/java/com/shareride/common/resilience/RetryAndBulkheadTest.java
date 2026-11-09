package com.shareride.common.resilience;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.shareride.common.exception.BadRequestException;
import com.shareride.common.resilience.bulkhead.BulkheadLimiter;
import com.shareride.common.resilience.retry.RetryExecutor;
import com.shareride.common.resilience.retry.RetryPolicy;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Retry and Bulkhead Resilience Tests")
class RetryAndBulkheadTest {

    @Test
    @DisplayName("Should succeed on first attempt without retrying")
    void testRetrySuccessFirstAttempt() {
        RetryExecutor executor = new RetryExecutor();
        String result = executor.execute("quick-op", () -> "SUCCESS");
        assertEquals("SUCCESS", result);
    }

    @Test
    @DisplayName("Should retry transient error and succeed on second attempt")
    void testRetryTransientErrorSuccess() {
        RetryPolicy policy =
                RetryPolicy.builder()
                        .maxAttempts(3)
                        .initialIntervalMs(10L)
                        .multiplier(1.0)
                        .jitterFactor(0.0)
                        .build();
        RetryExecutor executor = new RetryExecutor(policy);

        AtomicInteger attempts = new AtomicInteger(0);
        String result =
                executor.execute(
                        "retry-op",
                        () -> {
                            if (attempts.incrementAndGet() < 2) {
                                throw new RuntimeException(
                                        new IOException("Transient socket drop"));
                            }
                            return "RECOVERED";
                        },
                        policy);

        assertEquals("RECOVERED", result);
        assertEquals(2, attempts.get());
    }

    @Test
    @DisplayName("Should NOT retry non-retryable client errors like BadRequestException")
    void testDoNotRetryBadRequest() {
        RetryPolicy policy = RetryPolicy.builder().maxAttempts(3).initialIntervalMs(10L).build();
        RetryExecutor executor = new RetryExecutor(policy);

        AtomicInteger attempts = new AtomicInteger(0);
        assertThrows(
                BadRequestException.class,
                () ->
                        executor.execute(
                                "bad-request-op",
                                () -> {
                                    attempts.incrementAndGet();
                                    throw new BadRequestException("Invalid parameter format");
                                },
                                policy));

        assertEquals(1, attempts.get(), "Must not retry BadRequestException");
    }

    @Test
    @DisplayName("Bulkhead should limit concurrency and release permits")
    void testBulkheadPermits() {
        BulkheadLimiter bulkhead = new BulkheadLimiter("isolated-service", 2, 50L);

        assertTrue(bulkhead.acquirePermit());
        assertTrue(bulkhead.acquirePermit());
        assertEquals(0, bulkhead.getAvailablePermits());

        // 3rd attempt should be rejected after 50ms wait
        assertFalse(bulkhead.acquirePermit());

        // Release 1 permit
        bulkhead.releasePermit();
        assertEquals(1, bulkhead.getAvailablePermits());
        assertTrue(bulkhead.acquirePermit());
    }
}
