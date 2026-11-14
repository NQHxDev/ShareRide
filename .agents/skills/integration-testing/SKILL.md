---
name: integration-testing
description: >-
    Use this skill when creating, modifying, or debugging integration tests for microservices in ShareRide.
    Covers setting up Testcontainers with Spring Boot 4.x, configuring isolated PostgreSQL 17 and Valkey containers,
    and keeping CI pipelines clean and scalable.
compatibility: Requires Java 21, Spring Boot 4.x, Maven, and Docker for Testcontainers
metadata:
    author: NQHxZeion
---

# Integration Testing with Testcontainers in ShareRide

This skill outlines how to write clean, isolated, and scalable integration tests across the ShareRide microservices monorepo using **Testcontainers**.

---

## 1. Golden Rules

1. **Never add service containers to `.github/workflows/ci.yml`**:
   Do NOT add databases, Redis, or Valkey to the CI `jobs.<job>.services` section. Containers must be spun up on-demand in test code via Testcontainers.
2. **Graceful Local Execution**:
   Always use `@Testcontainers(disabledWithoutDocker = true)`. This ensures tests run with full Docker containers in CI and on Docker-enabled workstations, while allowing tests to compile and pass gracefully if Docker is temporarily inactive.
3. **Automatic Spring Connection**:
   Use `@Container @ServiceConnection` on PostgreSQL containers so Spring Boot auto-configures Datasource URLs, users, and passwords.

---

## 2. Dependencies Setup

Ensure the service module's `pom.xml` contains:

```xml
<!-- Testcontainers in <dependencies> (versions managed by root pom and parent) -->
<dependency>
   <groupId>org.springframework.boot</groupId>
   <artifactId>spring-boot-testcontainers</artifactId>
   <scope>test</scope>
</dependency>
<dependency>
   <groupId>org.testcontainers</groupId>
   <artifactId>testcontainers-junit-jupiter</artifactId>
   <scope>test</scope>
</dependency>
<dependency>
   <groupId>org.testcontainers</groupId>
   <artifactId>testcontainers-postgresql</artifactId>
   <scope>test</scope>
</dependency>
```

---

## 3. Writing an Integration Test

### Pattern 1: Extending the Base Class (Recommended)

Microservices can define or extend an abstract base test class (e.g. `AbstractIntegrationTest`):

```java
package com.shareride.<service>.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@SuppressWarnings("resource")
public abstract class AbstractIntegrationTest {

    // Note: Do NOT use @Container here. Using Singleton Container Pattern ensures
    // containers stay alive across multiple test classes without being shut down by JUnit's afterAll.
    @ServiceConnection
    protected static final PostgreSQLContainer postgres =
            new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));

    protected static final GenericContainer<?> valkey =
            new GenericContainer<>(DockerImageName.parse("valkey/valkey-extension:latest"));

    static {
        postgres.withDatabaseName("shareride_<service>");
        postgres.withUsername("shareride_<service>_usr");
        postgres.withPassword("secret_password");

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
```

### Pattern 2: Service-Specific Test Class

```java
@SpringBootTest
class OrderPersistenceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private OrderRepository orderRepository;

    @Test
    void testCreateOrder() {
        // Test executes against real PostgreSQL 17 in container
    }
}
```

---

## 4. Running Tests

```bash
# Run all integration tests across all modules
./mvnw test -Dtest="*IntegrationTest" --batch-mode

# Run integration tests for a specific module
./mvnw test -pl auth-service -Dtest="*IntegrationTest"

# Verify formatting before push
./mvnw spotless:check checkstyle:check
```
