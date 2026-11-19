package com.shareride.gateway.service;

import com.shareride.common.exception.RateLimitExceededException;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Valkey-backed rate limiting service for Gateway endpoints. Implements sliding window rate
 * limiting per T024 (User Story 1).
 *
 * <p>Rate limits:
 *
 * <ul>
 *   <li>Login: 5 requests/minute per client IP or email
 *   <li>Register: 5 requests/minute per client IP or email
 *   <li>Refresh: 10 requests/minute per client IP
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GatewayRateLimitService {

    private final ReactiveStringRedisTemplate reactiveStringRedisTemplate;

    private static final String RATE_LIMIT_PREFIX = "gateway:rate-limit:";
    private static final int LOGIN_LIMIT = 5;
    private static final int REGISTER_LIMIT = 5;
    private static final int REFRESH_LIMIT = 10;
    private static final Duration WINDOW_DURATION = Duration.ofMinutes(1);

    /**
     * Check rate limit for login endpoint.
     *
     * @param identifier client IP or email
     * @return Mono completing successfully if allowed, error with RateLimitExceededException if
     *     exceeded
     */
    public Mono<Void> checkLoginRateLimit(String identifier) {
        return checkRateLimit("login", identifier, LOGIN_LIMIT);
    }

    /**
     * Check rate limit for register endpoint.
     *
     * @param identifier client IP or email
     * @return Mono completing successfully if allowed, error with RateLimitExceededException if
     *     exceeded
     */
    public Mono<Void> checkRegisterRateLimit(String identifier) {
        return checkRateLimit("register", identifier, REGISTER_LIMIT);
    }

    /**
     * Check rate limit for refresh token endpoint.
     *
     * @param identifier client IP
     * @return Mono completing successfully if allowed, error with RateLimitExceededException if
     *     exceeded
     */
    public Mono<Void> checkRefreshRateLimit(String identifier) {
        return checkRateLimit("refresh", identifier, REFRESH_LIMIT);
    }

    /**
     * Generic rate limit check using Valkey counter with sliding window.
     *
     * @param endpoint endpoint name
     * @param identifier unique identifier (IP or email)
     * @param limit maximum requests per window
     * @return Mono completing successfully if allowed, error if exceeded
     */
    private Mono<Void> checkRateLimit(String endpoint, String identifier, int limit) {
        String key = RATE_LIMIT_PREFIX + endpoint + ":" + identifier;

        return reactiveStringRedisTemplate
                .opsForValue()
                .increment(key)
                .flatMap(
                        count -> {
                            if (count == 1) {
                                return reactiveStringRedisTemplate
                                        .expire(key, WINDOW_DURATION)
                                        .then();
                            }

                            if (count > limit) {
                                log.warn(
                                        "Rate limit exceeded for {}: identifier={}, count={}/{}",
                                        endpoint,
                                        identifier,
                                        count,
                                        limit);
                                return Mono.error(
                                        new RateLimitExceededException(
                                                "Quá nhiều yêu cầu. Vui lòng thử lại sau."));
                            }

                            return Mono.empty();
                        })
                .onErrorResume(
                        throwable -> {
                            if (throwable instanceof RateLimitExceededException) {
                                return Mono.error(throwable);
                            }
                            log.error(
                                    "Rate limit check failed for {}: {}",
                                    endpoint,
                                    throwable.getMessage());
                            return Mono.empty();
                        });
    }
}
