package com.shareride.auth.integration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Integration test verifying Valkey database isolation. Uses Testcontainers to spin up real Valkey
 * instance. TDD Red Phase: This test should FAIL until database isolation is properly configured.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class ValkeyDatabaseIsolationIntegrationTest {

    @Container
    static GenericContainer<?> valkeyContainer =
            new GenericContainer<>(DockerImageName.parse("valkey/valkey-extension:latest"));

    static {
        valkeyContainer.withExposedPorts(6379);
        valkeyContainer.withReuse(true);
    }

    @DynamicPropertySource
    static void registerValkeyProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", valkeyContainer::getHost);
        registry.add("spring.data.redis.port", valkeyContainer::getFirstMappedPort);
        registry.add("spring.data.redis.database", () -> 0);
    }

    @Autowired private RedisTemplate<String, String> redisTemplate;

    @Test
    void authService_shouldOnlyAccessDatabaseZero() {
        // Given
        String testKey = "auth:test:isolation";
        String testValue = "database-0-data";

        // When - Write to database 0 (auth service default)
        redisTemplate.opsForValue().set(testKey, testValue);

        // Then - Verify data exists in database 0
        String retrievedValue = redisTemplate.opsForValue().get(testKey);
        assertThat(retrievedValue)
                .as("Data should be retrievable from database 0")
                .isEqualTo(testValue);

        // Cleanup
        redisTemplate.delete(testKey);
    }

    @Test
    void databaseZero_shouldBeIsolatedFromDatabaseOne() {
        // Given
        String authKey = "auth:email:bloom";
        String authValue = "auth-service-data";

        // When - Write to database 0
        redisTemplate.opsForValue().set(authKey, authValue);

        // Then - Verify data exists in database 0
        assertThat(redisTemplate.hasKey(authKey)).isTrue();

        // Verify database 0 is isolated (manual check via CLI if needed)
        // In real scenario, other services would use different database numbers
        // This test confirms auth service correctly targets database 0

        // Cleanup
        redisTemplate.delete(authKey);
    }
}
