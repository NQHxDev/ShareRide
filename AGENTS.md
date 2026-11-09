# ShareRide - AI Coding Agent Guidelines

Welcome to the ShareRide monorepo. When writing code, tests, or configuring CI/CD in this repository, follow these standards:

## 1. Architecture & Tech Stack

- **Java 21**, **Spring Boot 4.x**, **Maven Multi-Module**.
- **Databases**: PostgreSQL 17 (per-service database isolation).
- **Cache & State**: Valkey (`valkey/valkey-extension:latest`) with Bloom filter & JSON modules.
- **Code Quality**: Google Java Format enforced by Spotless and Checkstyle. Always run `mvn spotless:apply` and `mvn checkstyle:check` before committing.

## 2. Service Architecture & Template Standards (Strictly Enforced)

- **One Single Standard**: All new microservices must follow `service-template`. Initialize new services using `make new-service NAME=<name> PORT=<port>`.
- **Service Layer Pattern**: Strictly enforce Enterprise `*Service` (interface in `.service`) + `*ServiceImpl` (class in `.service.impl`).
- **Controller Rules**: Must be annotated with `@RestController` and `@ApiVersion("v1")`. Must wrap all responses in `ResponseEntity<BaseResponse<T>>`. Controllers must NEVER inject Repositories directly (ArchUnit will fail).
- **Domain Entities**: Must extend `BaseEntity` or `SoftDeletableEntity` with time-ordered UUIDv7 and Optimistic Locking (`@Version`).
- **Inter-service Communication**:
   - **gRPC**: Use `<Service>GrpcClient` backed by `GrpcChannelFactory` and `GrpcStatusExceptionMapper`.
   - **RabbitMQ**: Use `<Service>EventPublisher` (Outbox + direct async) and `<Service>EventConsumer` (protected by `IdempotentMessageService` deduplication).
- **Architecture Testing**: Every service must include `<Service>ArchitectureTest` with ArchUnit.

## 3. Testing & CI Standards (Crucial)

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

## 4. Rules & Skills

- Service standards docs: `docs/service-template-standards.md`
- Testing rule: `.agents/rules/testing-strategy.md`
- Integration testing skill: `.agents/skills/integration-testing/SKILL.md`
- Detailed docs: `docs/testing-strategy.md`
