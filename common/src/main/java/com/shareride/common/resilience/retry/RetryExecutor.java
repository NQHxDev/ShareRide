package com.shareride.common.resilience.retry;

import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Executes operations under exponential backoff with jitter retry strategy. */
public class RetryExecutor {

    private static final Logger log = LoggerFactory.getLogger(RetryExecutor.class);

    private final RetryPolicy defaultPolicy;

    public RetryExecutor() {
        this(RetryPolicy.ofDefaults());
    }

    public RetryExecutor(RetryPolicy defaultPolicy) {
        this.defaultPolicy = defaultPolicy != null ? defaultPolicy : RetryPolicy.ofDefaults();
    }

    public <T> T execute(Supplier<T> supplier) {
        return execute("anonymous-op", supplier, defaultPolicy);
    }

    public <T> T execute(String operationName, Supplier<T> supplier) {
        return execute(operationName, supplier, defaultPolicy);
    }

    public <T> T execute(String operationName, Supplier<T> supplier, RetryPolicy policy) {
        RetryPolicy effectivePolicy = policy != null ? policy : defaultPolicy;
        int maxAttempts = effectivePolicy.getMaxAttempts();
        int attempt = 0;
        Throwable lastError = null;

        while (attempt < maxAttempts) {
            attempt++;
            try {
                return supplier.get();
            } catch (Throwable t) {
                lastError = t;
                boolean canRetry =
                        (attempt < maxAttempts) && effectivePolicy.getRetryablePredicate().test(t);

                if (!canRetry) {
                    if (attempt > 1) {
                        log.warn(
                                "Operation [{}] failed permanently after {} attempts: {}",
                                operationName,
                                attempt,
                                t.getMessage());
                    }
                    if (t instanceof RuntimeException re) {
                        throw re;
                    }
                    throw new RuntimeException(t);
                }

                long backoffMs = effectivePolicy.calculateDelayMs(attempt);
                log.warn(
                        "Operation [{}] failed attempt {}/{}: {}. Retrying in {}ms...",
                        operationName,
                        attempt,
                        maxAttempts,
                        t.getMessage(),
                        backoffMs);

                try {
                    Thread.sleep(backoffMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(
                            "Retry interrupted for operation [" + operationName + "]", ie);
                }
            }
        }

        if (lastError instanceof RuntimeException re) {
            throw re;
        }
        throw new RuntimeException(
                "Operation [" + operationName + "] exceeded " + maxAttempts + " attempts",
                lastError);
    }
}
