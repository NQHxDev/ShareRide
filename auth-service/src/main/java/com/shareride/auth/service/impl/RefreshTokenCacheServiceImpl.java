package com.shareride.auth.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shareride.auth.constant.AuthConstants;
import com.shareride.auth.dto.RefreshTokenData;
import com.shareride.auth.service.RefreshTokenCacheService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Triển khai chuẩn của {@link RefreshTokenCacheService} lưu trữ và xử lý vòng đời của Refresh
 * Tokens trên Valkey/Redis.
 */
@Slf4j
@Service
public class RefreshTokenCacheServiceImpl implements RefreshTokenCacheService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    public RefreshTokenCacheServiceImpl(
            @Qualifier("sharedRedisTemplate") RedisTemplate<String, Object> redisTemplate,
            ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void saveRefreshToken(
            UUID sessionId, UUID accountId, String refreshToken, Duration ttl) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }

        String tokenHash = hashToken(refreshToken);
        String tokenKey = AuthConstants.CacheKey.REFRESH_TOKEN_PREFIX + tokenHash;
        String sessionKey = AuthConstants.CacheKey.SESSION_TOKEN_PREFIX + sessionId;
        String accountSessionsKey = AuthConstants.CacheKey.ACCOUNT_SESSIONS_PREFIX + accountId;

        RefreshTokenData data =
                RefreshTokenData.builder()
                        .sessionId(sessionId)
                        .accountId(accountId)
                        .tokenHash(tokenHash)
                        .createdAt(Instant.now())
                        .expiresAt(Instant.now().plus(ttl))
                        .build();

        try {
            redisTemplate.opsForValue().set(tokenKey, data, ttl);
            redisTemplate.opsForValue().set(sessionKey, tokenHash, ttl);
            redisTemplate.opsForSet().add(accountSessionsKey, sessionId.toString());
            log.info(
                    "Stored refresh token in Valkey for account [{}] session [{}] with TTL {}s",
                    accountId,
                    sessionId,
                    ttl.toSeconds());
        } catch (Exception e) {
            log.error(
                    "Failed to store refresh token in Valkey for account [{}] session [{}]: {}",
                    accountId,
                    sessionId,
                    e.getMessage(),
                    e);
        }
    }

    @Override
    public Optional<RefreshTokenData> getRefreshTokenData(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return Optional.empty();
        }

        String tokenHash = hashToken(refreshToken);
        String tokenKey = AuthConstants.CacheKey.REFRESH_TOKEN_PREFIX + tokenHash;

        try {
            Object raw = redisTemplate.opsForValue().get(tokenKey);
            if (raw == null) {
                return Optional.empty();
            }
            RefreshTokenData data = objectMapper.convertValue(raw, RefreshTokenData.class);
            return Optional.ofNullable(data);
        } catch (Exception e) {
            log.error("Failed to read refresh token from Valkey: {}", e.getMessage(), e);
            return Optional.empty();
        }
    }

    @Override
    public void revokeRefreshToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }

        String tokenHash = hashToken(refreshToken);
        String tokenKey = AuthConstants.CacheKey.REFRESH_TOKEN_PREFIX + tokenHash;

        try {
            Object raw = redisTemplate.opsForValue().get(tokenKey);
            if (raw != null) {
                RefreshTokenData data = objectMapper.convertValue(raw, RefreshTokenData.class);
                redisTemplate.delete(tokenKey);
                if (data.getSessionId() != null) {
                    redisTemplate.delete(
                            AuthConstants.CacheKey.SESSION_TOKEN_PREFIX + data.getSessionId());
                    if (data.getAccountId() != null) {
                        redisTemplate
                                .opsForSet()
                                .remove(
                                        AuthConstants.CacheKey.ACCOUNT_SESSIONS_PREFIX
                                                + data.getAccountId(),
                                        data.getSessionId().toString());
                    }
                }
                log.info("Revoked refresh token in Valkey for session [{}]", data.getSessionId());
            } else {
                redisTemplate.delete(tokenKey);
            }
        } catch (Exception e) {
            log.error("Failed to revoke refresh token in Valkey: {}", e.getMessage(), e);
        }
    }

    @Override
    public void revokeSession(UUID sessionId, UUID accountId) {
        if (sessionId == null) {
            return;
        }

        String sessionKey = AuthConstants.CacheKey.SESSION_TOKEN_PREFIX + sessionId;
        try {
            Object tokenHashRaw = redisTemplate.opsForValue().get(sessionKey);
            if (tokenHashRaw != null) {
                String tokenHash = tokenHashRaw.toString();
                redisTemplate.delete(AuthConstants.CacheKey.REFRESH_TOKEN_PREFIX + tokenHash);
            }
            redisTemplate.delete(sessionKey);
            if (accountId != null) {
                redisTemplate
                        .opsForSet()
                        .remove(
                                AuthConstants.CacheKey.ACCOUNT_SESSIONS_PREFIX + accountId,
                                sessionId.toString());
            }
            log.info("Revoked session [{}] from Valkey", sessionId);
        } catch (Exception e) {
            log.error("Failed to revoke session [{}] in Valkey: {}", sessionId, e.getMessage(), e);
        }
    }

    @Override
    public void revokeAllSessions(UUID accountId) {
        if (accountId == null) {
            return;
        }

        String accountSessionsKey = AuthConstants.CacheKey.ACCOUNT_SESSIONS_PREFIX + accountId;
        try {
            Set<Object> sessionIds = redisTemplate.opsForSet().members(accountSessionsKey);
            if (sessionIds != null) {
                for (Object sId : sessionIds) {
                    if (sId != null) {
                        UUID sessionId = UUID.fromString(sId.toString());
                        String sessionKey = AuthConstants.CacheKey.SESSION_TOKEN_PREFIX + sessionId;
                        Object tokenHashRaw = redisTemplate.opsForValue().get(sessionKey);
                        if (tokenHashRaw != null) {
                            redisTemplate.delete(
                                    AuthConstants.CacheKey.REFRESH_TOKEN_PREFIX
                                            + tokenHashRaw.toString());
                        }
                        redisTemplate.delete(sessionKey);
                    }
                }
            }
            redisTemplate.delete(accountSessionsKey);
            log.info("Revoked all sessions in Valkey for account [{}]", accountId);
        } catch (Exception e) {
            log.error(
                    "Failed to revoke all sessions for account [{}] in Valkey: {}",
                    accountId,
                    e.getMessage(),
                    e);
        }
    }

    private String hashToken(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance(AuthConstants.HASH_ALGORITHM_SHA256);
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(
                    AuthConstants.HASH_ALGORITHM_SHA256 + " algorithm not available", e);
        }
    }
}
