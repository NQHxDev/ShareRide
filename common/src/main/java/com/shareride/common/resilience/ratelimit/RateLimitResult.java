package com.shareride.common.resilience.ratelimit;

import lombok.Builder;
import lombok.Getter;

/** Result of a rate limiting evaluation. */
@Getter
@Builder
public class RateLimitResult {

    private final boolean allowed;
    private final long remainingTokens;
    private final long limitPerPeriod;
    private final long resetMs;
    private final long retryAfterSeconds;

    public static RateLimitResult permitted(
            long remainingTokens, long limitPerPeriod, long resetMs) {
        return RateLimitResult.builder()
                .allowed(true)
                .remainingTokens(remainingTokens)
                .limitPerPeriod(limitPerPeriod)
                .resetMs(resetMs)
                .retryAfterSeconds(0)
                .build();
    }

    public static RateLimitResult rejected(long limitPerPeriod, long retryAfterSeconds) {
        return RateLimitResult.builder()
                .allowed(false)
                .remainingTokens(0)
                .limitPerPeriod(limitPerPeriod)
                .resetMs(retryAfterSeconds * 1000L)
                .retryAfterSeconds(retryAfterSeconds)
                .build();
    }
}
