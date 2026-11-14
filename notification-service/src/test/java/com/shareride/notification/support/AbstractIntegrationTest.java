package com.shareride.notification.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
public abstract class AbstractIntegrationTest {

    @ServiceConnection
    protected static final PostgreSQLContainer postgresContainer =
            new PostgreSQLContainer("postgres:17-alpine");

    protected static final GenericContainer<?> valkeyContainer =
            new GenericContainer<>("valkey/valkey-extension:latest");

    static {
        postgresContainer
                .withDatabaseName("shareride_notification_test")
                .withUsername("test_user")
                .withPassword("test_password");

        valkeyContainer.withExposedPorts(6379);

        if (DockerClientFactory.instance().isDockerAvailable()) {
            postgresContainer.start();
            valkeyContainer.start();
        }
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        if (valkeyContainer.isRunning()) {
            registry.add("spring.data.redis.host", valkeyContainer::getHost);
            registry.add("spring.data.redis.port", valkeyContainer::getFirstMappedPort);
            registry.add("shareride.cache.shared.host", valkeyContainer::getHost);
            registry.add("shareride.cache.shared.port", valkeyContainer::getFirstMappedPort);
        }
    }
}
