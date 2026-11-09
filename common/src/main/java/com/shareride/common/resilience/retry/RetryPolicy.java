package com.shareride.common.resilience.retry;

import com.shareride.common.exception.BadRequestException;
import com.shareride.common.exception.ConflictException;
import com.shareride.common.exception.ForbiddenException;
import com.shareride.common.exception.ResourceNotFoundException;
import com.shareride.common.exception.UnauthorizedException;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;
import lombok.Builder;
import lombok.Getter;

/** Immutable configuration defining exponential backoff, jitter, and retryable conditions. */
@Getter
@Builder
public class RetryPolicy {

    @Builder.Default private final int maxAttempts = 3;

    @Builder.Default private final long initialIntervalMs = 200L;

    @Builder.Default private final double multiplier = 2.0d;

    @Builder.Default private final long maxIntervalMs = 3000L;

    @Builder.Default private final double jitterFactor = 0.25d; // +/- 25% random jitter

    @Builder.Default
    private final Predicate<Throwable> retryablePredicate = RetryPolicy::defaultIsRetryable;

    public static RetryPolicy ofDefaults() {
        return RetryPolicy.builder().build();
    }

    /**
     * Determines whether an error is transient and suitable for safe retry. Never retries 4xx
     * client errors (BadRequest, NotFound, Unauthorized, Forbidden, Conflict).
     */
    public static boolean defaultIsRetryable(Throwable throwable) {
        if (throwable == null) {
            return false;
        }

        // Do NOT retry client business errors
        if (throwable instanceof BadRequestException
                || throwable instanceof ResourceNotFoundException
                || throwable instanceof UnauthorizedException
                || throwable instanceof ForbiddenException
                || throwable instanceof ConflictException) {
            return false;
        }

        // Retry network timeouts & IO drops
        if (throwable instanceof IOException
                || throwable instanceof TimeoutException
                || throwable instanceof SocketTimeoutException) {
            return true;
        }

        // Inspect gRPC StatusRuntimeException if present on classpath
        String className = throwable.getClass().getName();
        if (className.contains("StatusRuntimeException") || className.contains("StatusException")) {
            String msg = throwable.getMessage();
            if (msg != null
                    && (msg.contains("UNAVAILABLE")
                            || msg.contains("DEADLINE_EXCEEDED")
                            || msg.contains("RESOURCE_EXHAUSTED"))) {
                return true;
            }
            if (msg != null
                    && (msg.contains("NOT_FOUND")
                            || msg.contains("INVALID_ARGUMENT")
                            || msg.contains("UNAUTHENTICATED")
                            || msg.contains("PERMISSION_DENIED")
                            || msg.contains("ALREADY_EXISTS"))) {
                return false;
            }
        }

        return true;
    }

    /** Computes exponential backoff interval with full jitter to avoid thundering herd. */
    public long calculateDelayMs(int attempt) {
        if (attempt <= 0) {
            return 0;
        }
        double baseDelay = initialIntervalMs * Math.pow(multiplier, attempt - 1);
        long cappedDelay = Math.min((long) baseDelay, maxIntervalMs);

        if (jitterFactor > 0.0) {
            double jitterRange = cappedDelay * jitterFactor;
            double delta = ThreadLocalRandom.current().nextDouble(-jitterRange, jitterRange);
            return Math.max(0, (long) (cappedDelay + delta));
        }
        return cappedDelay;
    }
}
