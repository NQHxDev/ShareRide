package com.shareride.template.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base class for Template Service Integration Tests using Testcontainers. Provides on-demand,
 * isolated PostgreSQL 17 and Valkey containers. Uses Singleton Container pattern without @Container
 * lifecycle hooks so containers remain active across all integration test classes, preventing
 * connection drop and timeout issues. Annotated with disabledWithoutDocker = true so local runs
 * without Docker gracefully pass.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@SuppressWarnings("resource")
public abstract class AbstractIntegrationTest {

    @ServiceConnection
    protected static final PostgreSQLContainer postgres =
            new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));

    protected static final GenericContainer<?> valkey =
            new GenericContainer<>(DockerImageName.parse("valkey/valkey-extension:latest"));

    static {
        postgres.withDatabaseName("shareride_template");
        postgres.withUsername("shareride_template_usr");
        postgres.withPassword("template_secret_password");

        valkey.withExposedPorts(6379);

        if (DockerClientFactory.instance().isDockerAvailable()) {
            postgres.start();
            valkey.start();
        }
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        if (valkey.isRunning()) {
            registry.add("spring.data.redis.host", valkey::getHost);
            registry.add("spring.data.redis.port", valkey::getFirstMappedPort);
            registry.add("shareride.cache.shared.host", valkey::getHost);
            registry.add("shareride.cache.shared.port", valkey::getFirstMappedPort);
            registry.add("shareride.cache.enabled", () -> "true");
            registry.add("shareride.redis.shared.host", valkey::getHost);
            registry.add("shareride.redis.shared.port", valkey::getFirstMappedPort);
            registry.add("shareride.redis.enabled", () -> "true");
        }
    }
}
