package com.shareride.auth.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Unit test for ValkeyConfig verifying database 0 selection. TDD Red Phase: This test should FAIL
 * until ValkeyConfig is properly implemented.
 */
@ExtendWith(MockitoExtension.class)
class ValkeyConfigTest {

    @Test
    void authValkeyConnectionFactory_shouldConfigureDatabaseZero() {
        // Given
        ValkeyConfig config = new ValkeyConfig();
        ReflectionTestUtils.setField(config, "host", "localhost");
        ReflectionTestUtils.setField(config, "port", 6379);
        ReflectionTestUtils.setField(config, "password", "");
        ReflectionTestUtils.setField(config, "database", 0);

        // When
        LettuceConnectionFactory factory = config.authValkeyConnectionFactory();
        factory.afterPropertiesSet();

        // Then
        RedisStandaloneConfiguration standaloneConfig =
                (RedisStandaloneConfiguration)
                        ReflectionTestUtils.getField(factory, "configuration");

        assertThat(standaloneConfig).isNotNull();
        assertThat(standaloneConfig.getDatabase())
                .as("Auth service must use Valkey database 0 for isolation")
                .isEqualTo(0);
        assertThat(standaloneConfig.getHostName()).isEqualTo("localhost");
        assertThat(standaloneConfig.getPort()).isEqualTo(6379);
    }

    @Test
    void authValkeyConnectionFactory_shouldConfigureCustomDatabase() {
        // Given
        ValkeyConfig config = new ValkeyConfig();
        ReflectionTestUtils.setField(config, "host", "valkey.example.com");
        ReflectionTestUtils.setField(config, "port", 6380);
        ReflectionTestUtils.setField(config, "password", "secret");
        ReflectionTestUtils.setField(config, "database", 0);

        // When
        LettuceConnectionFactory factory = config.authValkeyConnectionFactory();
        factory.afterPropertiesSet();

        // Then
        RedisStandaloneConfiguration standaloneConfig =
                (RedisStandaloneConfiguration)
                        ReflectionTestUtils.getField(factory, "configuration");

        assertThat(standaloneConfig).isNotNull();
        assertThat(standaloneConfig.getDatabase()).isEqualTo(0);
        assertThat(standaloneConfig.getHostName()).isEqualTo("valkey.example.com");
        assertThat(standaloneConfig.getPort()).isEqualTo(6380);
    }
}
