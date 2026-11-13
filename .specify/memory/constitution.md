<!--
SYNC IMPACT REPORT
==================
Version Change: 1.1.0 → 1.2.0
Amendment Date: 2026-11-13
Bump Rationale: MINOR - Added new core principle "VII. Test-First & Red-Green-Refactor Cycle" mandating TDD workflow for all implementation work.

Modified Principles: None (existing principles preserved exactly)

Added Sections:
- New Principle VII: Test-First & Red-Green-Refactor Cycle (NON-NEGOTIABLE)

Removed Sections: None

Deferred Items: None
-->

# ShareRide Constitution

## Core Principles

### I. Microservice Architecture & Single Standard

All services MUST follow the `service-template` standard and structure.

- Java 21, Spring Boot 4.x, Maven Multi-Module architecture.
- Database isolation: PostgreSQL 17 per service to ensure service autonomy and independent scaling.
- Valkey (`valkey/valkey-extension:latest`) with Bloom filter & JSON modules for distributed caching, session state, and idempotency tracking.
- New services MUST be initialized using `make new-service NAME=<name> PORT=<port>`.

**Rationale**: A single, enforced template ensures consistency across the monorepo, reduces onboarding friction, and enables architectural compliance testing. Database isolation prevents tight coupling and cascading failures across service boundaries.

### II. Enterprise Service Layer Pattern & Controller Encapsulation

Strictly enforce the Enterprise service layer pattern with clear separation of concerns.

- Service interfaces MUST be defined in `.service` package; implementations in `.service.impl`.
- Controllers MUST be annotated with `@RestController` and `@ApiVersion("v1")`.
- All controller endpoints MUST wrap responses in `ResponseEntity<BaseResponse<T>>` for consistent API contracts.
- Controllers MUST NEVER inject Repositories directly. All data access MUST be encapsulated within the service layer.

**Rationale**: Direct repository access from controllers violates separation of concerns, makes testing difficult, and bypasses business logic. ArchUnit rules enforce this boundary automatically during builds.

### III. Resilient Domain Entities & Concurrency Control

Domain entities MUST be designed for resilience and safe concurrent access.

- All entities MUST extend `BaseEntity` or `SoftDeletableEntity`.
- Primary keys MUST use time-ordered UUIDv7 for distributed ID generation without coordination.
- Optimistic locking MUST be enforced using `@Version` to prevent lost updates.

**Rationale**: UUIDv7 provides sortable, collision-resistant IDs without database sequences. Optimistic locking prevents silent data loss in concurrent write scenarios common in distributed systems.

### IV. Inter-Service Communication & Idempotency

Inter-service communication MUST be reliable, observable, and idempotent.

- **gRPC**: Synchronous RPCs MUST use `<Service>GrpcClient` backed by `GrpcChannelFactory` with standardized error mapping via `GrpcStatusExceptionMapper`.
- **RabbitMQ**: Asynchronous events MUST use `<Service>EventPublisher` (Transactional Outbox + direct async) and `<Service>EventConsumer` protected by `IdempotentMessageService` for at-least-once delivery semantics.

**Rationale**: Transactional outbox ensures message publishing is atomic with database changes. Idempotency protection prevents duplicate processing in the face of retries and network partitions.

### V. Rigorous Testing & Non-Bloated CI

Testing MUST be fast, reliable, and executable in any environment.

- **Unit Tests**: MUST be fast and isolated using `@ExtendWith(MockitoExtension.class)`. MUST NOT boot full Spring Context or spin up Docker containers.
- **Integration Tests**: MUST use Testcontainers with `@Testcontainers(disabledWithoutDocker = true)`. Use `@ServiceConnection PostgreSQLContainer` for database tests and `GenericContainer<?>` with `valkey/valkey-extension:latest` for cache tests. Reference implementation: `com.shareride.auth.support.AbstractIntegrationTest`.
- **CI Safety**: MUST NEVER add external services directly to GitHub Actions `services:` in `.github/workflows/ci.yml`. Testcontainers handles all infrastructure dependencies.
- **Architecture Tests**: Every service MUST include `<Service>ArchitectureTest` with ArchUnit to enforce architectural compliance as executable tests.

**Rationale**: Testcontainers provides reproducible infrastructure across local and CI environments without CI configuration bloat. Fast unit tests enable rapid feedback loops. ArchUnit transforms architectural rules into failing tests rather than review comments.

### VI. Code Quality & Formatting Gates

Code quality and formatting MUST be consistent and automatically enforced.

- Google Java Format is strictly enforced via Spotless and Checkstyle.
- Developers MUST run `mvn spotless:apply` and `mvn checkstyle:check` before committing.
- Pre-commit hooks SHOULD be configured to catch formatting violations early.

**Rationale**: Consistent formatting eliminates bikeshedding in code reviews and reduces merge conflicts. Automated enforcement scales better than manual review.

### VII. Test-First & Red-Green-Refactor Cycle (NON-NEGOTIABLE)

All implementation work MUST follow the Test-Driven Development (TDD) workflow.

- **Red**: Write a failing test that defines the desired behavior or fix. The test MUST fail initially, proving it's testing the right thing.
- **Green**: Write the minimal code necessary to make the test pass. Focus on making it work, not making it perfect.
- **Refactor**: Clean up the implementation while keeping tests green. Improve design, remove duplication, enhance readability.

**Workflow Requirements**:
- Tests MUST be written BEFORE implementation code.
- Initial test runs MUST show failures (Red phase verification).
- Implementation proceeds ONLY after the Red phase is confirmed.
- Refactoring MUST NOT change behavior; tests remain green throughout.

**Rationale**: TDD ensures every line of code is justified by a test, catches regressions immediately, and produces inherently testable designs. The Red-Green-Refactor cycle prevents false positives (tests that pass without exercising real code) and creates a safety net for refactoring. Writing tests first forces clarity about requirements before implementation begins.

## Task Management & Workflow Governance

All feature additions, refactoring, and bug fixes MUST follow the OmniHarness / omni-todo workflow state machine:

`pending` → `approved` → `in_progress` → `completed` → `verified`

- Agents MUST set status to `in_progress` before modifying code: `omni-todo set --id=<ID> --status=in_progress`.
- Tasks MUST be marked `completed` only when all checks and tests pass: `omni-todo set --id=<ID> --status=completed`.
- New tasks MUST include `--title`, `--description`, `--content`, and at least one `--tag`.

**Rationale**: Explicit state transitions create an audit trail, prevent duplicate work, and enable coordination across human and AI agents.

## Development Workflow

This constitution is the primary governance document. Runtime development guidance is provided in:

- `docs/service-template-standards.md` - Service scaffolding and structure patterns
- `docs/task-management.md` - OmniHarness workflow details and CLI reference
- `docs/testing-strategy.md` - Testing patterns, Testcontainers setup, and integration test examples
- `AGENTS.md` - AI agent instructions and quick reference

## Governance

### Amendment Process

This constitution supersedes all other practices and documentation. Amendments require:

1. Documented rationale for the change (problem statement + proposed solution).
2. Review and approval from at least one maintainer.
3. Migration plan if the amendment introduces breaking changes to existing services.
4. Version bump following semantic versioning rules (see below).

### Versioning Policy

Constitution versions follow semantic versioning (`MAJOR.MINOR.PATCH`):

- **MAJOR**: Backward-incompatible governance changes, principle removals, or redefinitions that require code changes.
- **MINOR**: New principles added, material expansions to existing guidance, or new workflow requirements.
- **PATCH**: Clarifications, wording improvements, typo fixes, or non-semantic refinements.

### Compliance Review

All pull requests and code reviews MUST verify compliance with this constitution. Architectural violations caught by ArchUnit MUST be resolved before merge. Complexity and deviations from established patterns MUST be explicitly justified in PR descriptions.

Use `AGENTS.md` for day-to-day agent guidance; refer agents to this constitution when governance questions arise.

---

**Version**: 1.2.0 | **Ratified**: 2026-11-13 | **Last Amended**: 2026-11-13
