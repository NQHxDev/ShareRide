package com.shareride.common.redis;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class TokenBlacklistServiceTest {

    @Mock private RedisTemplate<String, Object> redisTemplate;

    @Mock private ValueOperations<String, Object> valueOperations;

    private TokenBlacklistService blacklistService;

    @BeforeEach
    void setUp() {
        blacklistService = new TokenBlacklistService(redisTemplate);
    }

    @Test
    @DisplayName("blacklistToken should set token in Redis with TTL")
    void testBlacklistToken() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        blacklistService.blacklistToken("jti-12345", Duration.ofMinutes(15));
        verify(valueOperations, times(1))
                .set("blacklist:token:jti-12345", "REVOKED", Duration.ofMinutes(15));
    }

    @Test
    @DisplayName("isBlacklisted should return true if key exists in Redis")
    void testIsBlacklistedTrue() {
        when(redisTemplate.hasKey("blacklist:token:jti-revoked")).thenReturn(Boolean.TRUE);

        assertTrue(blacklistService.isBlacklisted("jti-revoked"));
    }

    @Test
    @DisplayName("isBlacklisted should return false if key does not exist")
    void testIsBlacklistedFalse() {
        when(redisTemplate.hasKey("blacklist:token:jti-active")).thenReturn(Boolean.FALSE);

        assertFalse(blacklistService.isBlacklisted("jti-active"));
    }

    @Test
    @DisplayName("Null or empty tokenId should be handled safely")
    void testNullOrBlankHandling() {
        blacklistService.blacklistToken("", Duration.ofMinutes(5));
        blacklistService.blacklistToken(null, Duration.ofMinutes(5));

        assertFalse(blacklistService.isBlacklisted(""));
        assertFalse(blacklistService.isBlacklisted(null));

        verifyNoInteractions(valueOperations);
    }
}
