# ShareRide - Agent Instructions

See full details in [AGENTS.md](./AGENTS.md).

## Quick Summary

- **Language & Framework**: Java 21, Spring Boot 4.x, Maven multi-module.
- **CI & Testing**: Use **Testcontainers** for integration tests (PostgreSQL 17, Valkey `valkey/valkey-extension:latest`). Never put databases or caches in GitHub Actions `services:`.
- **Formatting**: Run `mvn spotless:apply` and `mvn checkstyle:check` before submitting changes.
