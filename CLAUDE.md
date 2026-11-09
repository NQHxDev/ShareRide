# ShareRide - Agent Instructions

See full details in [AGENTS.md](./AGENTS.md).

## Quick Summary

- **Language & Framework**: Java 21, Spring Boot 4.x, Maven multi-module.
- **CI & Testing**: Use **Testcontainers** for integration tests (PostgreSQL 17, Valkey `valkey/valkey-extension:latest`). Never put databases or caches in GitHub Actions `services:`.
- **Formatting**: Run `mvn spotless:apply` and `mvn checkstyle:check` before submitting changes.
- **Task Management**: MCP Todo server is configured in `.mcp.json`. Always use CLI wrapper `omni-todo` via terminal execution (`omni-todo list`, `omni-todo show <ID>`, `omni-todo set --id=<ID> --status=in_progress|completed`). Never start task work without moving `approved -> in_progress`. See [docs/task-management.md](./docs/task-management.md).
