package com.shareride.gateway.ratelimit;

import lombok.Builder;

/**
 * Immutable decision result from the reactive token bucket evaluation.
 *
 * @param allowed whether the request is allowed to proceed
 * @param limit maximum burst capacity configured for the matching rule
 * @param replenishRate sustained replenish rate in tokens per second
 * @param remaining number of tokens remaining in the bucket
 * @param resetSeconds Unix epoch timestamp in seconds when the bucket completely resets
 * @param retryAfterSeconds seconds to wait before the next token becomes available (0 if allowed)
 * @param tier identified tier or rule identifier (e.g. premium, free, anonymous, endpoint:/login)
 * @param key unique cache key used in the token bucket state store
 */
@Builder
public record RateLimitResult(
        boolean allowed,
        long limit,
        long replenishRate,
        long remaining,
        long resetSeconds,
        long retryAfterSeconds,
        String tier,
        String key) {}
