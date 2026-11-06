package com.shareride.common.redis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DistributedLockServiceTest {

   @Mock
   private RedisTemplate<String, Object> redisTemplate;

   @Mock
   private ValueOperations<String, Object> valueOperations;

   private DistributedLockService lockService;

   @BeforeEach
   void setUp() {
      lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
      lockService = new DistributedLockService(redisTemplate);
   }

   @Test
   @DisplayName("Should successfully acquire lock when key is absent")
   void testAcquireLockSuccess() {
      when(valueOperations.setIfAbsent(eq("lock:ride:order-1"), eq("token-abc"), eq(Duration.ofSeconds(10))))
            .thenReturn(Boolean.TRUE);

      boolean acquired = lockService.acquireLock("ride:order-1", "token-abc", Duration.ofSeconds(10));
      assertTrue(acquired);
   }

   @Test
   @DisplayName("Should fail to acquire lock when key already exists")
   void testAcquireLockFailure() {
      when(valueOperations.setIfAbsent(eq("lock:ride:order-1"), eq("token-abc"), eq(Duration.ofSeconds(10))))
            .thenReturn(Boolean.FALSE);

      boolean acquired = lockService.acquireLock("ride:order-1", "token-abc", Duration.ofSeconds(10));
      assertFalse(acquired);
   }

   @Test
   @DisplayName("Should release lock when Lua script returns 1")
   void testReleaseLockSuccess() {
      when(redisTemplate.execute(any(), eq(Collections.singletonList("lock:ride:order-1")), eq("token-abc")))
            .thenReturn(1L);

      boolean released = lockService.releaseLock("ride:order-1", "token-abc");
      assertTrue(released);
   }

   @Test
   @DisplayName("Should fail release lock when token mismatch or lock expired (Lua returns 0)")
   void testReleaseLockMismatch() {
      when(redisTemplate.execute(any(), eq(Collections.singletonList("lock:ride:order-1")), eq("wrong-token")))
            .thenReturn(0L);

      boolean released = lockService.releaseLock("ride:order-1", "wrong-token");
      assertFalse(released);
   }

   @Test
   @DisplayName("executeWithLock should execute supplier and release lock afterwards")
   void testExecuteWithLockSuccess() {
      when(valueOperations.setIfAbsent(startsWith("lock:driver:matching"), anyString(), any(Duration.class)))
            .thenReturn(Boolean.TRUE);
      when(redisTemplate.execute(any(), anyList(), anyString()))
            .thenReturn(1L);

      String result = lockService.executeWithLock("driver:matching", Duration.ofSeconds(5), () -> "RIDE_MATCHED");
      assertEquals("RIDE_MATCHED", result);

      verify(redisTemplate, times(1)).execute(any(), anyList(), anyString());
   }

   @Test
   @DisplayName("executeWithLock should throw IllegalStateException when lock cannot be acquired")
   void testExecuteWithLockThrowsWhenAcquireFails() {
      when(valueOperations.setIfAbsent(startsWith("lock:driver:matching"), anyString(), any(Duration.class)))
            .thenReturn(Boolean.FALSE);

      assertThrows(IllegalStateException.class,
            () -> lockService.executeWithLock("driver:matching", Duration.ofSeconds(5), () -> "SHOULD_FAIL"));
   }

}
