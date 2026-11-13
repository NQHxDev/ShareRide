package com.shareride.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shareride.auth.service.impl.LoginRateLimiterServiceImpl;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class LoginRateLimiterServiceImplTest {

    private LoginRateLimiterService serviceWithInMemory;
    private LoginRateLimiterService serviceWithRedis;
    private RedisTemplate<String, Object> redisTemplate;
    private ValueOperations<String, Object> valueOperations;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        serviceWithInMemory = new LoginRateLimiterServiceImpl();

        redisTemplate = mock(RedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        serviceWithRedis = new LoginRateLimiterServiceImpl(redisTemplate);
    }

    @Test
    @DisplayName("In-Memory: Should not block IP with less than 5 failed attempts")
    void testIpNotBlockedUnderThreshold() {
        String ip = "192.168.1.1";
        assertFalse(serviceWithInMemory.isIpBlocked(ip));

        for (int i = 1; i <= 4; i++) {
            serviceWithInMemory.recordLoginFailure(ip, null);
        }

        assertEquals(4, serviceWithInMemory.getIpFailedAttempts(ip));
        assertFalse(serviceWithInMemory.isIpBlocked(ip));
    }

    @Test
    @DisplayName("In-Memory: Should block IP on 5 or more failed attempts")
    void testIpBlockedAtThreshold() {
        String ip = "192.168.1.2";

        for (int i = 1; i <= 5; i++) {
            serviceWithInMemory.recordLoginFailure(ip, null);
        }

        assertEquals(5, serviceWithInMemory.getIpFailedAttempts(ip));
        assertTrue(serviceWithInMemory.isIpBlocked(ip));

        // 6th attempt remains blocked
        serviceWithInMemory.recordLoginFailure(ip, null);
        assertEquals(6, serviceWithInMemory.getIpFailedAttempts(ip));
        assertTrue(serviceWithInMemory.isIpBlocked(ip));
    }

    @Test
    @DisplayName("In-Memory: Should not exceed account limit with less than 10 failed attempts")
    void testAccountNotExceededUnderThreshold() {
        String email = "rider@shareride.com";
        assertFalse(serviceWithInMemory.isAccountRateLimitExceeded(email));

        for (int i = 1; i <= 9; i++) {
            int count = serviceWithInMemory.recordLoginFailure(null, email);
            assertEquals(i, count);
        }

        assertEquals(9, serviceWithInMemory.getAccountFailedAttempts(email));
        assertFalse(serviceWithInMemory.isAccountRateLimitExceeded(email));
    }

    @Test
    @DisplayName("In-Memory: Should exceed account limit on 10 or more failed attempts")
    void testAccountExceededAtThreshold() {
        String email = "victim@shareride.com";

        for (int i = 1; i <= 10; i++) {
            int count = serviceWithInMemory.recordLoginFailure(null, email);
            assertEquals(i, count);
        }

        assertEquals(10, serviceWithInMemory.getAccountFailedAttempts(email));
        assertTrue(serviceWithInMemory.isAccountRateLimitExceeded(email));
    }

    @Test
    @DisplayName("In-Memory: Should reset failed attempts on successful login")
    void testResetLoginAttempts() {
        String ip = "10.0.0.1";
        String email = "user@shareride.com";

        serviceWithInMemory.recordLoginFailure(ip, email);
        serviceWithInMemory.recordLoginFailure(ip, email);

        assertEquals(2, serviceWithInMemory.getIpFailedAttempts(ip));
        assertEquals(2, serviceWithInMemory.getAccountFailedAttempts(email));

        serviceWithInMemory.resetLoginAttempts(ip, email);

        assertEquals(0, serviceWithInMemory.getIpFailedAttempts(ip));
        assertEquals(0, serviceWithInMemory.getAccountFailedAttempts(email));
        assertFalse(serviceWithInMemory.isIpBlocked(ip));
        assertFalse(serviceWithInMemory.isAccountRateLimitExceeded(email));
    }

    @Test
    @DisplayName("In-Memory: Should handle null and empty strings gracefully")
    void testNullAndEmptyInputs() {
        assertFalse(serviceWithInMemory.isIpBlocked(null));
        assertFalse(serviceWithInMemory.isIpBlocked(""));
        assertFalse(serviceWithInMemory.isAccountRateLimitExceeded(null));
        assertFalse(serviceWithInMemory.isAccountRateLimitExceeded(""));

        assertEquals(0, serviceWithInMemory.getIpFailedAttempts(null));
        assertEquals(0, serviceWithInMemory.getAccountFailedAttempts(null));

        int result = serviceWithInMemory.recordLoginFailure(null, null);
        assertEquals(0, result);

        serviceWithInMemory.resetLoginAttempts(null, null);
    }

    @Test
    @DisplayName("Redis: Should record failure and set TTL on first attempt")
    void testRedisRecordFailureFirstAttempt() {
        String ip = "172.16.0.5";
        String email = "redis.user@shareride.com";

        when(valueOperations.increment(anyString())).thenReturn(1L);

        int count = serviceWithRedis.recordLoginFailure(ip, email);

        assertEquals(1, count);
        verify(redisTemplate, org.mockito.Mockito.times(2))
                .expire(anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("Redis: Should fall back to in-memory store when Redis throws exception")
    void testRedisFailoverToInMemory() {
        String ip = "172.16.0.9";
        String email = "failover@shareride.com";

        doThrow(new RuntimeException("Redis connection refused"))
                .when(valueOperations)
                .increment(anyString());
        doThrow(new RuntimeException("Redis connection refused"))
                .when(valueOperations)
                .get(anyString());

        int count = serviceWithRedis.recordLoginFailure(ip, email);

        assertEquals(1, count);
        assertEquals(1, serviceWithRedis.getIpFailedAttempts(ip));
        assertEquals(1, serviceWithRedis.getAccountFailedAttempts(email));
    }
}
