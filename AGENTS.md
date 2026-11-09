# ShareRide - AI Coding Agent Guidelines

Welcome to the ShareRide monorepo. When writing code, tests, or configuring CI/CD in this repository, follow these standards:

## 1. Architecture & Tech Stack

- **Java 21**, **Spring Boot 4.x**, **Maven Multi-Module**.
- **Databases**: PostgreSQL 17 (per-service database isolation).
- **Cache & State**: Valkey (`valkey/valkey-extension:latest`) with Bloom filter & JSON modules.
- **Code Quality**: Google Java Format enforced by Spotless and Checkstyle. Always run `mvn spotless:apply` and `mvn checkstyle:check` before committing.

## 2. Testing & CI Standards (Crucial)

- **Never bloat CI with services**: Do not add databases, Redis/Valkey, or brokers to GitHub Actions `services:` in `.github/workflows/ci.yml`.
- **Testcontainers for Integration Tests**:
   - All integration tests needing PostgreSQL or Valkey must use **Testcontainers**.
   - Use `@Testcontainers(disabledWithoutDocker = true)` to ensure builds succeed even if Docker is temporarily unavailable on developer machines.
   - For PostgreSQL: Use `@Container @ServiceConnection PostgreSQLContainer` from `org.testcontainers.postgresql`.
   - For Valkey: Use `@Container GenericContainer<?>` with image `valkey/valkey-extension:latest` and configure properties via `@DynamicPropertySource`.
   - Reference class: `com.shareride.auth.support.AbstractIntegrationTest`.
- **Unit Tests**:
   - Keep unit tests fast with `@ExtendWith(MockitoExtension.class)`.
   - Do not boot full Spring Context or start Docker containers for pure unit tests.

## 3. Rules & Skills

- Testing rule: `.agents/rules/testing-strategy.md`
- Integration testing skill: `.agents/skills/integration-testing/SKILL.md`
- Detailed docs: `docs/testing-strategy.md`
