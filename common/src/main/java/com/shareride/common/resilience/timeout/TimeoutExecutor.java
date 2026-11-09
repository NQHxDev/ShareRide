package com.shareride.common.resilience.timeout;

import com.shareride.common.exception.RequestTimeoutException;
import java.util.concurrent.*;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Strict timeout executor ensuring downstream sync calls do not exceed configured execution
 * deadlines.
 */
public class TimeoutExecutor {

    private static final Logger log = LoggerFactory.getLogger(TimeoutExecutor.class);

    private static final ExecutorService WORKER_POOL =
            Executors.newCachedThreadPool(
                    r -> {
                        Thread t = new Thread(r, "timeout-guard-worker");
                        t.setDaemon(true);
                        return t;
                    });

    private final long defaultTimeoutMs;

    public TimeoutExecutor() {
        this(3000L);
    }

    public TimeoutExecutor(long defaultTimeoutMs) {
        this.defaultTimeoutMs = defaultTimeoutMs > 0 ? defaultTimeoutMs : 3000L;
    }

    public <T> T execute(String operationName, Supplier<T> supplier) {
        return execute(operationName, supplier, defaultTimeoutMs, TimeUnit.MILLISECONDS);
    }

    public <T> T execute(String operationName, Supplier<T> supplier, long timeout, TimeUnit unit) {
        long timeoutMs = unit.toMillis(timeout);
        CompletableFuture<T> future = CompletableFuture.supplyAsync(supplier, WORKER_POOL);

        try {
            return future.get(timeout, unit);
        } catch (TimeoutException te) {
            future.cancel(true);
            log.warn("Operation [{}] timed out after {}ms", operationName, timeoutMs);
            throw new RequestTimeoutException(
                    "Operation [" + operationName + "] timed out after " + timeoutMs + "ms");
        } catch (InterruptedException ie) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Operation [" + operationName + "] interrupted", ie);
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause() != null ? ee.getCause() : ee;
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            throw new RuntimeException(
                    "Operation [" + operationName + "] execution failed: " + cause.getMessage(),
                    cause);
        }
    }
}
