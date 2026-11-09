# ShareRide Testing & CI Guidelines

## 1. Core Testing Philosophy

- **Decoupled CI Infrastructure**: Do NOT add microservice databases, caches, or message brokers to the GitHub Actions `services:` section in `.github/workflows/ci.yml`.
- **Testcontainers for Integration Tests**: All integration tests that require external systems (PostgreSQL, Valkey, Kafka) MUST be managed programmatically via **Testcontainers**.
- **Fast Unit Tests**: Unit tests and slice tests (`@WebMvcTest`, service unit tests) must use Mockito or lightweight in-memory alternatives (H2) and run in seconds without requiring Docker.

## 2. Integration Test Standards

- Integration test classes should be named `*IntegrationTest` (e.g. `AuthPersistenceIntegrationTest`).
- Use `@Testcontainers(disabledWithoutDocker = true)` so tests execute with containers when Docker is available (in CI or developer machines with Docker) and gracefully pass/skip on machines without Docker.
- For relational databases (PostgreSQL), use `@Container @ServiceConnection` from Spring Boot Testcontainers for automatic datasource property binding.
- For Valkey / Redis, use `GenericContainer("valkey/valkey-extension:latest")` and register host/port dynamically using `@DynamicPropertySource`.
- Reference implementation: `com.shareride.auth.support.AbstractIntegrationTest`.

## 3. Maven & Dependency Rules

- Testcontainers versions are centrally managed in root `pom.xml` (`<testcontainers.version>`).
- Microservices should declare:
   - `org.springframework.boot:spring-boot-testcontainers` (scope: test)
   - `org.testcontainers:testcontainers-junit-jupiter` (scope: test)
   - Relevant modules such as `org.testcontainers:testcontainers-postgresql` (scope: test)
- Code style and formatting must strictly conform to Spotless (`mvn spotless:apply`) and Checkstyle (`mvn checkstyle:check`).
