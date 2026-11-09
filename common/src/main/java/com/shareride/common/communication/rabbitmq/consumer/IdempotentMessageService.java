package com.shareride.common.communication.rabbitmq.consumer;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Enterprise Idempotency Service preventing duplicate message execution across microservices.
 * Records processed event IDs in Redis with configurable TTL (default 7 days).
 */
public class IdempotentMessageService {

    private static final Logger log = LoggerFactory.getLogger(IdempotentMessageService.class);
    private static final String REDIS_KEY_PREFIX = "shareride:event:idempotency:";
    private static final Duration DEFAULT_TTL = Duration.ofDays(7);

    private final StringRedisTemplate redisTemplate;

    // In-memory fallback if Redis is unavailable or unconfigured
    private final ConcurrentMap<String, Long> inMemoryCache = new ConcurrentHashMap<>();

    public IdempotentMessageService() {
        this(null);
    }

    public IdempotentMessageService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /** Checks if the event has already been processed. */
    public boolean isProcessed(String eventId) {
        if (eventId == null || eventId.isBlank()) {
            return false;
        }

        if (redisTemplate != null) {
            try {
                Boolean exists = redisTemplate.hasKey(REDIS_KEY_PREFIX + eventId);
                return Boolean.TRUE.equals(exists);
            } catch (Exception e) {
                log.warn(
                        "Redis idempotency check failed for event [{}], checking in-memory cache:"
                                + " {}",
                        eventId,
                        e.getMessage());
            }
        }

        Long expiry = inMemoryCache.get(eventId);
        if (expiry == null) {
            return false;
        }
        if (System.currentTimeMillis() > expiry) {
            inMemoryCache.remove(eventId);
            return false;
        }
        return true;
    }

    /**
     * Atomically marks the event as processed.
     *
     * @return true if marked successfully (first time processed); false if already processed
     *     (duplicate).
     */
    public boolean markAsProcessed(String eventId) {
        return markAsProcessed(eventId, DEFAULT_TTL);
    }

    public boolean markAsProcessed(String eventId, Duration ttl) {
        if (eventId == null || eventId.isBlank()) {
            return true;
        }

        Duration effectiveTtl = (ttl != null) ? ttl : DEFAULT_TTL;

        if (redisTemplate != null) {
            try {
                Boolean success =
                        redisTemplate
                                .opsForValue()
                                .setIfAbsent(REDIS_KEY_PREFIX + eventId, "PROCESSED", effectiveTtl);
                return Boolean.TRUE.equals(success);
            } catch (Exception e) {
                log.warn(
                        "Redis idempotency set failed for event [{}], using in-memory cache: {}",
                        eventId,
                        e.getMessage());
            }
        }

        long expiryTime = System.currentTimeMillis() + effectiveTtl.toMillis();
        Long previous = inMemoryCache.putIfAbsent(eventId, expiryTime);
        return previous == null;
    }

    /**
     * Executes the task only if the event has not yet been processed (Idempotent Guard).
     *
     * @param eventId Unique event identifier
     * @param action The business consumer action to execute
     * @return true if executed; false if skipped due to deduplication
     */
    public boolean executeIfNew(String eventId, Runnable action) {
        if (!markAsProcessed(eventId)) {
            log.warn(
                    "Duplicate event [{}] detected! Skipping execution to guarantee idempotency.",
                    eventId);
            return false;
        }

        action.run();
        return true;
    }
}
