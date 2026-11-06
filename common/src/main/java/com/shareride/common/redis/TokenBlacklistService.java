package com.shareride.common.redis;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Duration;

/**
 * Enterprise Token Blacklist and Revocation Service running on Shared Redis.
 * <p>
 * Allows immediate revocation of JWTs/Refresh Tokens across all microservices
 * (e.g., during user logout, password reset, or compromised session).
 */
@Slf4j
public class TokenBlacklistService {

   private static final String BLACKLIST_KEY_PREFIX = "blacklist:token:";
   private static final String REVOKED_MARKER = "REVOKED";

   private final RedisTemplate<String, Object> redisTemplate;

   public TokenBlacklistService(RedisTemplate<String, Object> redisTemplate) {
      this.redisTemplate = redisTemplate;
   }

   /**
    * Adds a token ID (jti) or token hash to the distributed blacklist.
    *
    * @param tokenId    Unique token identifier (jti)
    * @param expiration Remaining validity duration of the token
    */
   public void blacklistToken(String tokenId, Duration expiration) {
      if (tokenId == null || tokenId.isBlank()) {
         return;
      }
      String key = BLACKLIST_KEY_PREFIX + tokenId;
      try {
         redisTemplate.opsForValue().set(key, REVOKED_MARKER, expiration);
         log.info("Token [{}] added to blacklist with TTL {}s", tokenId, expiration.toSeconds());
      } catch (Exception e) {
         log.error("Failed to blacklist token [{}]: {}", tokenId, e.getMessage(), e);
      }
   }

   /**
    * Checks if a token ID (jti) has been revoked.
    *
    * @param tokenId Unique token identifier (jti)
    * @return true if token is revoked, false otherwise
    */
   public boolean isBlacklisted(String tokenId) {
      if (tokenId == null || tokenId.isBlank()) {
         return false;
      }
      String key = BLACKLIST_KEY_PREFIX + tokenId;
      try {
         Boolean hasKey = redisTemplate.hasKey(key);
         return Boolean.TRUE.equals(hasKey);
      } catch (Exception e) {
         log.warn("Error checking blacklist for token [{}]: {}", tokenId, e.getMessage());
         // Fallback: Fail closed or open based on security policy (fail open to prevent
         // total outage if redis drops)
         return false;
      }
   }

}
