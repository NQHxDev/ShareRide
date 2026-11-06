package com.shareride.common.redis;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.Collections;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Enterprise Redis Distributed Lock Service running on Shared Redis.
 * <p>
 * Implements atomic acquire with expiration (SET key value NX PX)
 * and atomic release via Lua script to prevent releasing expired locks acquired
 * by other processes.
 */
@Slf4j
public class DistributedLockService {

   private static final String LOCK_KEY_PREFIX = "lock:";
   private static final String RELEASE_LOCK_LUA = "if redis.call('get', KEYS[1]) == ARGV[1] then " +
         "    return redis.call('del', KEYS[1]) " +
         "else " +
         "    return 0 " +
         "end";

   private final RedisTemplate<String, Object> redisTemplate;
   private final RedisScript<Long> releaseScript;

   public DistributedLockService(RedisTemplate<String, Object> redisTemplate) {
      this.redisTemplate = redisTemplate;
      DefaultRedisScript<Long> script = new DefaultRedisScript<>();
      script.setScriptText(RELEASE_LOCK_LUA);
      script.setResultType(Long.class);
      this.releaseScript = script;
   }

   /**
    * Attempts to acquire a distributed lock.
    *
    * @param lockKey    Unique name of the lock
    * @param lockValue  Owner token/UUID
    * @param expiration Time-to-live for the lock
    * @return true if lock acquired, false otherwise
    */
   public boolean acquireLock(String lockKey, String lockValue, Duration expiration) {
      String fullKey = LOCK_KEY_PREFIX + lockKey;
      try {
         Boolean acquired = redisTemplate.opsForValue().setIfAbsent(fullKey, lockValue, expiration);
         boolean success = Boolean.TRUE.equals(acquired);
         if (success) {
            log.debug("Acquired distributed lock [{}] with token [{}]", lockKey, lockValue);
         }
         return success;
      } catch (Exception e) {
         log.warn("Failed to acquire distributed lock [{}]: {}", lockKey, e.getMessage());
         return false;
      }
   }

   /**
    * Atomically releases the distributed lock only if the token matches.
    *
    * @param lockKey   Unique name of the lock
    * @param lockValue Owner token used during acquisition
    * @return true if released, false if lock not held or token mismatch
    */
   public boolean releaseLock(String lockKey, String lockValue) {
      String fullKey = LOCK_KEY_PREFIX + lockKey;
      try {
         Long result = redisTemplate.execute(releaseScript, Collections.singletonList(fullKey), lockValue);
         boolean released = Long.valueOf(1L).equals(result);
         if (released) {
            log.debug("Released distributed lock [{}]", lockKey);
         } else {
            log.warn("Could not release lock [{}] - lock expired or token mismatch", lockKey);
         }
         return released;
      } catch (Exception e) {
         log.error("Error releasing distributed lock [{}]: {}", lockKey, e.getMessage());
         return false;
      }
   }

   /**
    * Executes a task within a distributed lock boundary.
    *
    * @param lockKey    Unique lock key
    * @param expiration Max duration to hold the lock
    * @param task       Action to execute if lock acquired
    * @return Task result
    * @throws IllegalStateException if lock could not be acquired
    */
   public <T> T executeWithLock(String lockKey, Duration expiration, Supplier<T> task) {
      String lockToken = UUID.randomUUID().toString();
      boolean acquired = acquireLock(lockKey, lockToken, expiration);
      if (!acquired) {
         throw new IllegalStateException("Failed to acquire distributed lock for key: " + lockKey);
      }
      try {
         return task.get();
      } finally {
         releaseLock(lockKey, lockToken);
      }
   }

   /**
    * Executes a runnable task within a distributed lock boundary.
    */
   public void executeWithLock(String lockKey, Duration expiration, Runnable task) {
      executeWithLock(lockKey, expiration, () -> {
         task.run();
         return null;
      });
   }

}
