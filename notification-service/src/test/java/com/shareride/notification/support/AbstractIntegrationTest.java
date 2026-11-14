package com.shareride.notification.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
public abstract class AbstractIntegrationTest {

    @Container @ServiceConnection
    protected static final PostgreSQLContainer<?> postgresContainer =
            new PostgreSQLContainer<>("postgres:17-alpine")
                    .withDatabaseName("shareride_notification_test")
                    .withUsername("test_user")
                    .withPassword("test_password");

    @Container
    protected static final GenericContainer<?> valkeyContainer =
            new GenericContainer<>("valkey/valkey-extension:latest").withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", valkeyContainer::getHost);
        registry.add("spring.data.redis.port", valkeyContainer::getFirstMappedPort);
        registry.add("shareride.cache.shared.host", valkeyContainer::getHost);
        registry.add("shareride.cache.shared.port", valkeyContainer::getFirstMappedPort);
    }
}
