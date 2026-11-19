package com.shareride.gateway.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Base class for Gateway Service Integration Tests using Testcontainers. Provides on-demand,
 * isolated Valkey container. Uses Singleton Container pattern with explicit lifecycle management.
 * Containers remain active across all integration test classes via static initialization and are
 * closed via JVM shutdown hook. Annotated with disabledWithoutDocker = true so local runs without
 * Docker gracefully pass.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
public abstract class AbstractIntegrationTest {

    protected static final GenericContainer<?> valkey =
            new GenericContainer<>(DockerImageName.parse("valkey/valkey-extension:latest"));

    static {
        valkey.withExposedPorts(6379);

        if (DockerClientFactory.instance().isDockerAvailable()) {
            valkey.start();

            Runtime.getRuntime()
                    .addShutdownHook(
                            new Thread(
                                    () -> {
                                        valkey.stop();
                                    }));
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
