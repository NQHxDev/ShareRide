package com.shareride.gateway.ratelimit;

import java.util.List;
import lombok.Value;

/**
 * Type-safe result from Redis token bucket rate limiting Lua script.
 *
 * <p>Represents the atomic token bucket operation result with all necessary information for HTTP
 * rate limit headers and retry logic.
 */
@Value
public class TokenBucketResult {

    /** Whether the request is allowed (true) or rate limited (false). */
    boolean allowed;

    /** Number of tokens remaining in the bucket after this request. */
    long remainingTokens;

    /** Unix epoch timestamp (seconds) when the bucket will be fully replenished. */
    long resetTimestamp;

    /** Seconds to wait before retrying (0 if allowed, >0 if rate limited). */
    long retryAfterSeconds;

    /**
     * Parses the raw Redis Lua script response into a type-safe result object.
     *
     * <p>The Lua script returns a list: {@code [allowed, remaining_tokens, reset_timestamp,
     * retry_after]}
     *
     * @param rawResponse the list returned by Redis Lua script execution
     * @return parsed token bucket result
     * @throws IllegalArgumentException if the response format is invalid
     */
    public static TokenBucketResult fromRedisResponse(List<?> rawResponse) {
        if (rawResponse == null || rawResponse.size() != 4) {
            throw new IllegalArgumentException(
                    "Invalid Redis token bucket response: expected 4 elements, got "
                            + (rawResponse == null ? "null" : rawResponse.size()));
        }

        long allowed = toLong(rawResponse.get(0));
        long remainingTokens = toLong(rawResponse.get(1));
        long resetTimestamp = toLong(rawResponse.get(2));
        long retryAfterSeconds = toLong(rawResponse.get(3));

        return new TokenBucketResult(
                allowed == 1, remainingTokens, resetTimestamp, retryAfterSeconds);
    }

    private static long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        throw new IllegalArgumentException(
                "Expected numeric value, got: " + (value == null ? "null" : value.getClass()));
    }
}
