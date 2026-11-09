package com.shareride.common.resilience.ratelimit;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

/**
 * Enterprise Distributed Rate Limiter powered by Redis sliding-window Lua script. Features an
 * automatic in-memory fallback to preserve service uptime if Redis is temporarily disconnected.
 */
public class DistributedRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(DistributedRateLimiter.class);

    // Redis Token-Bucket Lua Script: KEYS[1] = rate_limit_key, ARGV[1] = limit, ARGV[2] =
    // windowSeconds
    // Returns "<allowed>:<remaining>:<ttl>"
    private static final String LUA_RATE_LIMIT =
            """
            local key = KEYS[1]
            local limit = tonumber(ARGV[1])
            local window = tonumber(ARGV[2])
            local current = redis.call('INCR', key)
            if current == 1 then
                redis.call('EXPIRE', key, window)
            end
            local ttl = redis.call('TTL', key)
            if current > limit then
                return '0:0:' .. ttl
            else
                return '1:' .. (limit - current) .. ':' .. ttl
            end
            """;

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<String> redisScript;

    // In-memory fallback token buckets (keyed by rate limit key)
    private final ConcurrentMap<String, InMemoryBucket> inMemoryBuckets = new ConcurrentHashMap<>();

    public DistributedRateLimiter() {
        this(null);
    }

    public DistributedRateLimiter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.redisScript = new DefaultRedisScript<>(LUA_RATE_LIMIT, String.class);
    }

    /**
     * Evaluates rate limit for a client identifier (IP, userId, apiKey).
     *
     * @param key Unique rate limit key (e.g. "rate:ip:192.168.1.1" or "rate:user:u-123")
     * @param limitPerPeriod Maximum number of allowed requests in the time window
     * @param windowSeconds Window length in seconds (e.g. 1s or 60s)
     * @return {@link RateLimitResult}
     */
    public RateLimitResult tryAcquire(String key, int limitPerPeriod, int windowSeconds) {
        if (redisTemplate != null) {
            try {
                String result =
                        redisTemplate.execute(
                                redisScript,
                                List.of("shareride:ratelimit:" + key),
                                String.valueOf(limitPerPeriod),
                                String.valueOf(windowSeconds));

                if (result != null) {
                    return parseRedisResult(result, limitPerPeriod);
                }
            } catch (Exception e) {
                log.warn(
                        "Redis rate limit evaluation failed for key [{}], failing over to in-memory"
                                + " limiter: {}",
                        key,
                        e.getMessage());
            }
        }

        // In-memory fallback
        return evaluateInMemory(key, limitPerPeriod, windowSeconds);
    }

    private RateLimitResult parseRedisResult(String result, int limitPerPeriod) {
        String[] parts = result.split(":");
        if (parts.length < 3) {
            throw new IllegalStateException(
                    "Unexpected Redis rate limit script response: " + result);
        }
        long allowed = Long.parseLong(parts[0]);
        long remaining = Long.parseLong(parts[1]);
        long ttl = Long.parseLong(parts[2]);

        if (allowed == 1L) {
            return RateLimitResult.permitted(remaining, limitPerPeriod, ttl * 1000L);
        }
        return RateLimitResult.rejected(limitPerPeriod, Math.max(1L, ttl));
    }

    private RateLimitResult evaluateInMemory(String key, int limitPerPeriod, int windowSeconds) {
        long now = System.currentTimeMillis();
        long windowMs = windowSeconds * 1000L;

        InMemoryBucket bucket =
                inMemoryBuckets.compute(
                        key,
                        (k, existing) -> {
                            if (existing == null || now - existing.windowStart >= windowMs) {
                                return new InMemoryBucket(now, new AtomicInteger(1));
                            }
                            existing.count.incrementAndGet();
                            return existing;
                        });

        int current = bucket.count.get();
        long elapsed = now - bucket.windowStart;
        long remainingMs = Math.max(0, windowMs - elapsed);
        long retryAfterSec = Math.max(1, (remainingMs + 999) / 1000);

        if (current <= limitPerPeriod) {
            return RateLimitResult.permitted(limitPerPeriod - current, limitPerPeriod, remainingMs);
        } else {
            return RateLimitResult.rejected(limitPerPeriod, retryAfterSec);
        }
    }

    private record InMemoryBucket(long windowStart, AtomicInteger count) {}
}
