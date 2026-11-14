package com.shareride.auth.service.impl;

import com.shareride.auth.constant.AuthConstants;
import com.shareride.auth.dto.LoginAttemptRecord;
import com.shareride.auth.service.LoginRateLimiterService;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Triển khai chuẩn của {@link LoginRateLimiterService} quản lý tần suất đăng nhập và chống
 * brute-force thông qua Valkey/Redis, tích hợp cơ chế dự phòng bộ nhớ nội bộ (in-memory fallback)
 * nếu Redis tạm thời không khả dụng.
 */
@Slf4j
@Service
public class LoginRateLimiterServiceImpl implements LoginRateLimiterService {

    private final RedisTemplate<String, Object> redisTemplate;

    private final ConcurrentMap<String, LoginAttemptRecord> inMemoryIpAttempts =
            new ConcurrentHashMap<>();
    private final ConcurrentMap<String, LoginAttemptRecord> inMemoryAccountAttempts =
            new ConcurrentHashMap<>();

    @Autowired
    public LoginRateLimiterServiceImpl(
            @Autowired(required = false) @Qualifier("sharedRedisTemplate")
                    RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public LoginRateLimiterServiceImpl() {
        this(null);
    }

    @Override
    public boolean isIpBlocked(String ipAddress) {
        if (ipAddress == null || ipAddress.isBlank()) {
            return false;
        }
        int attempts = getIpFailedAttempts(ipAddress);
        return attempts >= AuthConstants.RateLimit.MAX_IP_FAILED_ATTEMPTS;
    }

    @Override
    public boolean isAccountRateLimitExceeded(String email) {
        if (email == null || email.isBlank()) {
            return false;
        }
        int attempts = getAccountFailedAttempts(normalizeEmail(email));
        return attempts >= AuthConstants.RateLimit.MAX_ACCOUNT_FAILED_ATTEMPTS;
    }

    @Override
    public int recordLoginFailure(String ipAddress, String email) {
        int ipCount = 0;
        if (ipAddress != null && !ipAddress.isBlank()) {
            String ipKey = AuthConstants.CacheKey.LOGIN_RATE_LIMIT_IP_PREFIX + ipAddress;
            ipCount =
                    incrementCounter(
                            ipKey,
                            AuthConstants.RateLimit.IP_RATE_LIMIT_TTL,
                            inMemoryIpAttempts,
                            ipAddress);
        }

        int accountCount = 0;
        if (email != null && !email.isBlank()) {
            String normalizedEmail = normalizeEmail(email);
            String accountKey =
                    AuthConstants.CacheKey.LOGIN_RATE_LIMIT_ACCOUNT_PREFIX + normalizedEmail;
            accountCount =
                    incrementCounter(
                            accountKey,
                            AuthConstants.RateLimit.ACCOUNT_RATE_LIMIT_TTL,
                            inMemoryAccountAttempts,
                            normalizedEmail);
        }

        log.debug(
                "Recorded login failure for IP [{}] (failures: {}) and account [{}] (failures: {})",
                ipAddress,
                ipCount,
                email,
                accountCount);

        return accountCount;
    }

    @Override
    public void resetLoginAttempts(String ipAddress, String email) {
        if (ipAddress != null && !ipAddress.isBlank()) {
            String ipKey = AuthConstants.CacheKey.LOGIN_RATE_LIMIT_IP_PREFIX + ipAddress;
            deleteCounter(ipKey, inMemoryIpAttempts, ipAddress);
        }
        if (email != null && !email.isBlank()) {
            String normalizedEmail = normalizeEmail(email);
            String accountKey =
                    AuthConstants.CacheKey.LOGIN_RATE_LIMIT_ACCOUNT_PREFIX + normalizedEmail;
            deleteCounter(accountKey, inMemoryAccountAttempts, normalizedEmail);
        }
    }

    @Override
    public int getIpFailedAttempts(String ipAddress) {
        if (ipAddress == null || ipAddress.isBlank()) {
            return 0;
        }
        String ipKey = AuthConstants.CacheKey.LOGIN_RATE_LIMIT_IP_PREFIX + ipAddress;
        return getCounter(ipKey, inMemoryIpAttempts, ipAddress);
    }

    @Override
    public int getAccountFailedAttempts(String email) {
        if (email == null || email.isBlank()) {
            return 0;
        }
        String normalizedEmail = normalizeEmail(email);
        String accountKey =
                AuthConstants.CacheKey.LOGIN_RATE_LIMIT_ACCOUNT_PREFIX + normalizedEmail;
        return getCounter(accountKey, inMemoryAccountAttempts, normalizedEmail);
    }

    private int incrementCounter(
            String redisKey,
            Duration ttl,
            ConcurrentMap<String, LoginAttemptRecord> inMemoryMap,
            String mapKey) {
        if (redisTemplate != null) {
            try {
                Long val = redisTemplate.opsForValue().increment(redisKey);
                if (val != null) {
                    if (val == 1L) {
                        redisTemplate.expire(redisKey, ttl);
                    }
                    return val.intValue();
                }
            } catch (Exception e) {
                log.warn(
                        "Redis increment failed for key [{}], using in-memory store: {}",
                        redisKey,
                        e.getMessage());
            }
        }
        return incrementInMemory(inMemoryMap, mapKey, ttl);
    }

    private int getCounter(
            String redisKey, ConcurrentMap<String, LoginAttemptRecord> inMemoryMap, String mapKey) {
        if (redisTemplate != null) {
            try {
                Object val = redisTemplate.opsForValue().get(redisKey);
                if (val instanceof Number n) {
                    return n.intValue();
                } else if (val instanceof String s) {
                    return Integer.parseInt(s);
                } else if (val != null) {
                    return Integer.parseInt(val.toString());
                }
                return 0;
            } catch (Exception e) {
                log.warn(
                        "Redis get failed for key [{}], using in-memory store: {}",
                        redisKey,
                        e.getMessage());
            }
        }
        LoginAttemptRecord record = inMemoryMap.get(mapKey);
        if (record == null || System.currentTimeMillis() > record.getExpiresAt()) {
            inMemoryMap.remove(mapKey);
            return 0;
        }
        return record.getCount().get();
    }

    private void deleteCounter(
            String redisKey, ConcurrentMap<String, LoginAttemptRecord> inMemoryMap, String mapKey) {
        if (redisTemplate != null) {
            try {
                redisTemplate.delete(redisKey);
            } catch (Exception e) {
                log.warn(
                        "Redis delete failed for key [{}], removing from in-memory store: {}",
                        redisKey,
                        e.getMessage());
            }
        }
        inMemoryMap.remove(mapKey);
    }

    private int incrementInMemory(
            ConcurrentMap<String, LoginAttemptRecord> inMemoryMap, String mapKey, Duration ttl) {
        long now = System.currentTimeMillis();
        long expiresAt = now + ttl.toMillis();
        LoginAttemptRecord record =
                inMemoryMap.compute(
                        mapKey,
                        (k, existing) -> {
                            if (existing == null || now > existing.getExpiresAt()) {
                                return new LoginAttemptRecord(expiresAt, new AtomicInteger(1));
                            }
                            existing.getCount().incrementAndGet();
                            return existing;
                        });
        return record.getCount().get();
    }

    private String normalizeEmail(String email) {
        return email != null ? email.toLowerCase().trim() : "";
    }
}
