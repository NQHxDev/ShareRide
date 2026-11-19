package com.shareride.gateway.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shareride.common.exception.RateLimitExceededException;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@DisplayName("GatewayRateLimitService Unit Tests")
class GatewayRateLimitServiceTest {

    @Mock private ReactiveStringRedisTemplate reactiveStringRedisTemplate;
    @Mock private ReactiveValueOperations<String, String> valueOperations;

    private GatewayRateLimitService rateLimitService;

    @BeforeEach
    void setUp() {
        rateLimitService = new GatewayRateLimitService(reactiveStringRedisTemplate);
    }

    @Test
    @DisplayName("Should set expiration on first request (count == 1)")
    void testCheckLoginRateLimit_FirstRequest() {
        when(reactiveStringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenReturn(Mono.just(1L));
        when(reactiveStringRedisTemplate.expire(anyString(), any(Duration.class)))
                .thenReturn(Mono.just(true));

        StepVerifier.create(rateLimitService.checkLoginRateLimit("192.168.1.1")).verifyComplete();

        verify(reactiveStringRedisTemplate)
                .expire(eq("gateway:rate-limit:login:192.168.1.1"), any(Duration.class));
    }

    @Test
    @DisplayName("Should allow request when count is within limit")
    void testCheckLoginRateLimit_WithinLimit() {
        when(reactiveStringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenReturn(Mono.just(3L));

        StepVerifier.create(rateLimitService.checkLoginRateLimit("192.168.1.1")).verifyComplete();
    }

    @Test
    @DisplayName("Should throw RateLimitExceededException when count exceeds limit")
    void testCheckLoginRateLimit_ExceedsLimit() {
        when(reactiveStringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenReturn(Mono.just(6L)); // Limit is 5

        StepVerifier.create(rateLimitService.checkLoginRateLimit("192.168.1.1"))
                .expectError(RateLimitExceededException.class)
                .verify();
    }

    @Test
    @DisplayName("Should test checkRegisterRateLimit within limit")
    void testCheckRegisterRateLimit() {
        when(reactiveStringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenReturn(Mono.just(2L));

        StepVerifier.create(rateLimitService.checkRegisterRateLimit("192.168.1.1"))
                .verifyComplete();
    }

    @Test
    @DisplayName("Should test checkRefreshRateLimit within limit")
    void testCheckRefreshRateLimit() {
        when(reactiveStringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenReturn(Mono.just(2L));

        StepVerifier.create(rateLimitService.checkRefreshRateLimit("192.168.1.1")).verifyComplete();
    }

    @Test
    @DisplayName("Should fallback gracefully to empty on Redis failure (fail-open)")
    void testCheckRateLimit_RedisFailureFailOpen() {
        when(reactiveStringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString()))
                .thenReturn(Mono.error(new RuntimeException("Redis connection refused")));

        StepVerifier.create(rateLimitService.checkLoginRateLimit("192.168.1.1")).verifyComplete();
    }
}
