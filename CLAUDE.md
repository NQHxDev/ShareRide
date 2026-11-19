# ShareRide - Agent Instructions

See full details in [AGENTS.md](./AGENTS.md).

## Quick Summary

- **Language & Framework**: Java 21, Spring Boot 4.x, Maven multi-module.
- **CI & Testing**: Use **Testcontainers** for integration tests (PostgreSQL 17, Valkey `valkey/valkey-extension:latest`). Never put databases or caches in GitHub Actions `services:`.
- **Formatting**: Run `mvn spotless:apply` and `mvn checkstyle:check` before submitting changes.
- **Task Management**: MCP Todo server is configured in `.mcp.json`. Always use CLI wrapper `omni-todo` via terminal execution (`omni-todo list`, `omni-todo show <ID>`, `omni-todo set --id=<ID> --status=in_progress|completed`). Never start task work without moving `approved -> in_progress`. See [docs/task-management.md](./docs/task-management.md).

## Slash Commands & Skills (.claude/commands)

Claude Code slash commands are mapped in `.claude/commands/` and reference the core skill implementations in `.agents/skills/`:

- `/archify` — Generate standalone architecture / workflow diagrams or explainer HTML pages.
- `/graphify` — Codebase knowledge graph extraction, dependency analysis, and query.
- `/integration-testing` — Integration test guidelines with Testcontainers (PostgreSQL 17, Valkey).
- `/github-mcp` — PR failure diagnostics and GitHub issue sync (PR creation & push strictly forbidden).
- `/speckit-constitution` (or `/constitution`) — View / update project principles.
- `/speckit-specify` (or `/specify`) — Create / update feature specification (spec.md).
- `/speckit-clarify` (or `/clarify`) — Clarify ambiguities in spec.
- `/speckit-plan` (or `/plan`) — Generate architectural plan and tech design (plan.md).
- `/speckit-checklist` (or `/checklist`) — Generate quality & testing checklist.
- `/speckit-tasks` (or `/tasks`) — Break down plan into dependency-ordered tasks (tasks.md).
- `/speckit-analyze` (or `/analyze`) — Cross-artifact consistency check (spec/plan/tasks).
- `/speckit-implement` (or `/implement`) — Execute tasks according to tasks.md and omni-todo.
- `/speckit-converge` (or `/converge`) — Reconcile codebase against spec/plan/tasks.
