package com.shareride.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import com.shareride.auth.config.BloomFilterProperties;
import com.shareride.auth.repository.AccountRepository;
import com.shareride.auth.service.EmailBloomFilterService.BloomFilterInfo;
import com.shareride.auth.service.impl.EmailBloomFilterServiceImpl;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.rebloom.client.Client;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class EmailBloomFilterServiceImplTest {

    @Mock private LettuceConnectionFactory connectionFactory;
    @Mock private AccountRepository accountRepository;
    @Mock private RedisTemplate<String, String> redisTemplate;

    private MeterRegistry meterRegistry;
    private BloomFilterProperties properties;
    private EmailBloomFilterServiceImpl bloomFilterService;
    private TestBloomClient testBloomClient;

    static class TestBloomClient extends Client {
        boolean existsResult = true;
        boolean existsCalled = false;
        boolean addCalled = false;
        boolean deleteCalled = false;
        boolean createFilterCalled = false;
        String lastAddedEmail;
        RuntimeException throwOnExists;
        RuntimeException throwOnAdd;
        RuntimeException throwOnDelete;
        RuntimeException throwOnCreateFilter;

        public TestBloomClient() {
            super((redis.clients.jedis.Jedis) null);
        }

        @Override
        public boolean exists(String key, String value) {
            existsCalled = true;
            if (throwOnExists != null) {
                throw throwOnExists;
            }
            return existsResult;
        }

        @Override
        public boolean add(String key, String value) {
            addCalled = true;
            lastAddedEmail = value;
            if (throwOnAdd != null) {
                throw throwOnAdd;
            }
            return true;
        }

        @Override
        public void createFilter(String key, long initCapacity, double errorRate) {
            createFilterCalled = true;
            if (throwOnCreateFilter != null) {
                throw throwOnCreateFilter;
            }
        }

        @Override
        public boolean delete(String key) {
            deleteCalled = true;
            if (throwOnDelete != null) {
                throw throwOnDelete;
            }
            return true;
        }

        @Override
        public Map<String, Object> info(String key) {
            return Map.of(
                    "Capacity", 1000000L,
                    "Size", 1437608L,
                    "Number of filters", 1L,
                    "Number of items inserted", 0L,
                    "Expansion rate", 2L);
        }
    }

    @BeforeEach
    void setUp() {
        properties = new BloomFilterProperties();
        properties.setEnabled(true);
        properties.setKey("auth:email:bloom");
        properties.setErrorRate(0.01);
        properties.setInitialCapacity(1000000);

        meterRegistry = new SimpleMeterRegistry();

        bloomFilterService =
                new EmailBloomFilterServiceImpl(
                        properties,
                        connectionFactory,
                        accountRepository,
                        meterRegistry,
                        redisTemplate);

        testBloomClient = new TestBloomClient();
        ReflectionTestUtils.setField(bloomFilterService, "bloomClient", testBloomClient);
    }

    @Test
    @DisplayName("mightExist should throw IllegalArgumentException when email is null or blank")
    void testMightExist_nullOrBlank() {
        assertThrows(IllegalArgumentException.class, () -> bloomFilterService.mightExist(null));
        assertThrows(IllegalArgumentException.class, () -> bloomFilterService.mightExist("  "));
    }

    @Test
    @DisplayName("mightExist should return true when Bloom filter is disabled")
    void testMightExist_disabled() {
        properties.setEnabled(false);
        assertTrue(bloomFilterService.mightExist("user@example.com"));
        assertFalse(testBloomClient.existsCalled);
    }

    @Test
    @DisplayName("mightExist should return true and increment hit counter on hit")
    void testMightExist_hit() {
        testBloomClient.existsResult = true;

        boolean exists = bloomFilterService.mightExist(" USER@example.com ");

        assertTrue(exists);
        assertTrue(testBloomClient.existsCalled);
        assertEquals(1.0, meterRegistry.get("auth.bloom.check.total").counter().count());
        assertEquals(1.0, meterRegistry.get("auth.bloom.hit.total").counter().count());
    }

    @Test
    @DisplayName("mightExist should return false and increment miss counter on miss")
    void testMightExist_miss() {
        testBloomClient.existsResult = false;

        boolean exists = bloomFilterService.mightExist("user@example.com");

        assertFalse(exists);
        assertTrue(testBloomClient.existsCalled);
        assertEquals(1.0, meterRegistry.get("auth.bloom.check.total").counter().count());
        assertEquals(1.0, meterRegistry.get("auth.bloom.miss.total").counter().count());
    }

    @Test
    @DisplayName("mightExist should fallback to true and increment fallback counter on exception")
    void testMightExist_fallbackOnException() {
        testBloomClient.throwOnExists = new RuntimeException("Redis down");

        boolean exists = bloomFilterService.mightExist("user@example.com");

        assertTrue(exists);
        assertEquals(1.0, meterRegistry.get("auth.bloom.fallback.total").counter().count());
    }

    @Test
    @DisplayName("addEmail should throw IllegalArgumentException when email is null or blank")
    void testAddEmail_nullOrBlank() {
        assertThrows(IllegalArgumentException.class, () -> bloomFilterService.addEmail(null));
        assertThrows(IllegalArgumentException.class, () -> bloomFilterService.addEmail(""));
    }

    @Test
    @DisplayName("addEmail should skip when disabled")
    void testAddEmail_disabled() {
        properties.setEnabled(false);
        bloomFilterService.addEmail("user@example.com");
        assertFalse(testBloomClient.addCalled);
    }

    @Test
    @DisplayName("addEmail should normalize email, call client.add and increment counter")
    void testAddEmail_success() {
        bloomFilterService.addEmail("  User@Example.COM  ");

        assertTrue(testBloomClient.addCalled);
        assertEquals("user@example.com", testBloomClient.lastAddedEmail);
        assertEquals(1.0, meterRegistry.get("auth.bloom.add.total").counter().count());
    }

    @Test
    @DisplayName("addEmail should catch and log exception without throwing")
    void testAddEmail_exceptionCaught() {
        testBloomClient.throwOnAdd = new RuntimeException("Redis error");
        bloomFilterService.addEmail("user@example.com");
        assertTrue(testBloomClient.addCalled);
    }

    @Test
    @DisplayName("initializeBloomFilter should skip if already initialized")
    void testInitializeBloomFilter_alreadyInitialized() {
        when(redisTemplate.hasKey("auth:email:bloom")).thenReturn(true);

        bloomFilterService.initializeBloomFilter();

        assertFalse(testBloomClient.createFilterCalled);
    }

    @Test
    @DisplayName("initializeBloomFilter should call createFilter when not initialized")
    void testInitializeBloomFilter_createsFilter() {
        when(redisTemplate.hasKey("auth:email:bloom")).thenReturn(false);

        bloomFilterService.initializeBloomFilter();

        assertTrue(testBloomClient.createFilterCalled);
    }

    @Test
    @DisplayName("initializeBloomFilter should wrap exception in RuntimeException")
    void testInitializeBloomFilter_exception() {
        when(redisTemplate.hasKey("auth:email:bloom")).thenReturn(false);
        testBloomClient.throwOnCreateFilter = new RuntimeException("Create failed");

        assertThrows(RuntimeException.class, () -> bloomFilterService.initializeBloomFilter());
    }

    @Test
    @DisplayName("rebuildFromDatabase should clear, create filter, and paginate accounts")
    void testRebuildFromDatabase_success() {
        when(redisTemplate.hasKey("auth:email:bloom")).thenReturn(false);

        Page<String> page1 = new PageImpl<>(List.of("a@x.com", "b@x.com"), PageRequest.of(0, 2), 3);
        Page<String> page2 = new PageImpl<>(List.of("c@x.com"), PageRequest.of(1, 2), 3);

        when(accountRepository.findAllEmails(PageRequest.of(0, 1000))).thenReturn(page1);
        when(accountRepository.findAllEmails(page1.nextPageable())).thenReturn(page2);

        long total = bloomFilterService.rebuildFromDatabase();

        assertEquals(3, total);
        assertTrue(testBloomClient.deleteCalled);
        assertTrue(testBloomClient.createFilterCalled);
        assertTrue(testBloomClient.addCalled);
        assertEquals(1.0, meterRegistry.get("auth.bloom.rebuild.total").counter().count());
    }

    @Test
    @DisplayName("rebuildFromDatabase should wrap exceptions in RuntimeException")
    void testRebuildFromDatabase_exception() {
        doThrow(new RuntimeException("DB error"))
                .when(accountRepository)
                .findAllEmails(any(Pageable.class));

        assertThrows(RuntimeException.class, () -> bloomFilterService.rebuildFromDatabase());
    }

    @Test
    @DisplayName("clear should call delete on bloomClient")
    void testClear_success() {
        bloomFilterService.clear();
        assertTrue(testBloomClient.deleteCalled);
    }

    @Test
    @DisplayName("clear should catch exception and not rethrow")
    void testClear_exception() {
        testBloomClient.throwOnDelete = new RuntimeException("Delete failed");
        bloomFilterService.clear();
        assertTrue(testBloomClient.deleteCalled);
    }

    @Test
    @DisplayName("isInitialized should return true when key exists")
    void testIsInitialized_true() {
        when(redisTemplate.hasKey("auth:email:bloom")).thenReturn(true);
        assertTrue(bloomFilterService.isInitialized());
    }

    @Test
    @DisplayName("isInitialized should return false when key does not exist or null")
    void testIsInitialized_false() {
        when(redisTemplate.hasKey("auth:email:bloom")).thenReturn(false);
        assertFalse(bloomFilterService.isInitialized());

        when(redisTemplate.hasKey("auth:email:bloom")).thenReturn(null);
        assertFalse(bloomFilterService.isInitialized());
    }

    @Test
    @DisplayName("isInitialized should return false on exception")
    void testIsInitialized_exception() {
        when(redisTemplate.hasKey(anyString())).thenThrow(new RuntimeException("Redis down"));
        assertFalse(bloomFilterService.isInitialized());
    }

    @Test
    @DisplayName("getFilterInfo should return 0 stats when not initialized")
    void testGetFilterInfo_notInitialized() {
        when(redisTemplate.hasKey("auth:email:bloom")).thenReturn(false);

        BloomFilterInfo info = bloomFilterService.getFilterInfo();

        assertNotNull(info);
        assertEquals(0, info.capacity());
        assertEquals(0, info.size());
    }

    @Test
    @DisplayName("getFilterInfo should return configured capacity when initialized")
    void testGetFilterInfo_initialized() {
        when(redisTemplate.hasKey("auth:email:bloom")).thenReturn(true);

        BloomFilterInfo info = bloomFilterService.getFilterInfo();

        assertNotNull(info);
        assertEquals(1000000, info.capacity());
        assertTrue(info.size() > 0);
        assertEquals(1, info.numberOfFilters());
    }

    @Test
    @DisplayName("getFilterInfo should wrap exception in RuntimeException")
    void testGetFilterInfo_exception() {
        when(redisTemplate.hasKey("auth:email:bloom")).thenReturn(true);
        BloomFilterProperties brokenProps = org.mockito.Mockito.mock(BloomFilterProperties.class);
        when(brokenProps.getKey()).thenReturn("auth:email:bloom");
        when(brokenProps.getInitialCapacity())
                .thenThrow(new RuntimeException("Property read error"));
        ReflectionTestUtils.setField(bloomFilterService, "properties", brokenProps);

        assertThrows(RuntimeException.class, () -> bloomFilterService.getFilterInfo());
    }
}
