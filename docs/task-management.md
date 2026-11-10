# ShareRide Task Management Guidelines (OmniHarness / omni-todo)

## 1. Overview & Architecture

- **MCP Configuration**: The MCP TODO server runs at `http://127.0.0.1:20130/mcp/todo` and is configured in `.mcp.json`:

    ```json
    {
        "mcpServers": {
            "todo": {
                "type": "sse",
                "url": "http://127.0.0.1:20130/mcp/todo"
            }
        }
    }
    ```

- **Global Task Board**: Managed by OmniHarness, keeping task state, descriptions, acceptance criteria, and review notes consistent across agent and developer sessions.

## 2. CLI Execution Standard (Strictly Enforced)

- **Do NOT invoke raw MCP tools directly**: Direct MCP tool calls (`add_todo`, `update_todo`, `delete_todo`, `list_todos`) are **deprecated for agents**.
- **Always use the system CLI wrapper**: Agents MUST run the `omni-todo` CLI via terminal execution (`exec_command`).
- **Why?**:
    - `omni-todo` strictly asserts and validates state transitions.
    - Automatically appends timestamped review logs (`## Review YYYY-MM-DD HH:MM`) without accidentally overwriting previous notes.
    - Prevents multiline title/description formatting errors.
    - Prevents second-granularity ID collisions on task creation.
    - Warns on missing content or incomplete tags.

## 3. Task State Machine & Lifecycle

Tasks move strictly according to this state graph:

```workflow
 pending ─────────► approved ─────────► in_progress ─────────► completed ─────────► verified
    │                  │                      │                    │
    ▼                  ▼                      ▼                    ▼
cancelled           pending           approved / pending      in_progress (reopened)
```

| State         | Role            | Description                                               |
| ------------- | --------------- | --------------------------------------------------------- |
| `pending`     | User / Queue    | Task is proposed or queued, awaiting user review.         |
| `approved`    | User            | User accepts the task. Ready for an agent to pick up.     |
| `in_progress` | Agent           | Agent actively implements the task.                       |
| `completed`   | Agent           | Implementation, tests, and formatting are fully finished. |
| `verified`    | User / Reviewer | Quality check passed; task is archived.                   |
| `cancelled`   | User            | Task discarded.                                           |

### Allowed Transitions:

- `pending -> approved`: User acceptance.
- `pending -> cancelled`: Task rejected.
- `approved -> in_progress`: Agent begins implementation (**Agent starts ONLY from approved**).
- `approved -> pending`: Task unapproved back to queue.
- `in_progress -> completed`: Agent completes implementation and tests.
- `in_progress -> approved` / `in_progress -> pending`: Agent yields or postpones task.
- `completed -> verified`: Verification passes.
- `completed -> in_progress`: Verification finds defects; task reopened for fixes.
- `cancelled -> pending` / `cancelled -> approved`: Task restored.

## 4. Agent Execution Rules

1. **Before Starting Any Work**:
    - For medium-to-large tasks or explicit TODO items, your **VERY FIRST ACTION** (before reading deep code or editing files) MUST be:

        ```bash
        omni-todo set --id=<ID> --status=in_progress
        ```

    - An agent picks up work **only after the status is `approved`**.
    - If a task is in `pending`, run `omni-todo approve --id=<ID>` (or ask the user to approve / run `make todo-approve ID=<ID>`).
    - For quick fixes or trivial 1-line edits, you may proceed directly without creating unnecessary tasks.

2. **While Working**:
    - Inspect requirements and technical specs using:

        ```bash
        omni-todo show <ID>
        ```

    - Follow project guidelines: Java 21, Spring Boot 4.x, Testcontainers, Spotless formatting, Checkstyle rules.
    - Run tests and verifications (`make pre-commit-check` / `make test`).

3. **When Finished**:
    - Ensure all acceptance criteria are met, tests pass, and code is formatted (`mvn spotless:apply`).
    - Mark the task completed:

        ```bash
        omni-todo set --id=<ID> --status=completed
        ```

4. **Creating New Tasks**:
    - Every task MUST carry single-line `--title`, single-line `--description`, detailed `--content`, and at least one `--tag`:

        ```bash
        omni-todo add --title="<Title>" --description="<Brief Summary>" --content="<Details & AC>" --tag="<tag>"
        ```

    - For subtasks, specify the parent task ID:

        ```bash
        omni-todo add --title="<Title>" --description="<Summary>" --content="<Details>" --tag="<tag>" --parent=<PARENT_ID>
        ```

    - Tags cannot be empty. Multiple tags: `--tags="persistence, optimization"`.

5. **Review & Verification Workflow (`verify:: <todo-id>`)**:
    - When reviewing or executing verification:
        1. Inspect target task via `omni-todo show <ID>`.
        2. Inspect git diff and run verification commands (`git status`, `git diff`, `make test`, `make check-style`).
        3. If defects or violations are found:
            - Append a review finding to the parent task:

                ```bash
                omni-todo set --id=<PARENT_ID> --note="Review findings: <summary of issues>"
                ```

            - Create subtask(s) under the parent task:

                ```bash
                omni-todo add --title="[Fix] <Issue Title>" --description="<Brief Summary>" --content="<Root cause & steps>" --tag="fix" --parent=<PARENT_ID>
                ```

        4. If clean and fully compliant:
            - Mark task verified:

                ```bash
                omni-todo set --id=<PARENT_ID> --status=verified
                ```

6. **Deletion & Pruning**:
    - Deletion is destructive: `omni-todo del --id=<ID> --yes` (execute **ONLY** upon explicit user request).
    - Maintenance: `omni-todo prune` removes verified tasks older than 3 days and completed tasks older than 5 days.

## 5. CLI Command Reference

| Action                      | Command                                                                             |
| --------------------------- | ----------------------------------------------------------------------------------- |
| List active tasks           | `omni-todo list`                                                                    |
| List tasks ready to start   | `omni-todo list --status=approved`                                                  |
| Filter by tag               | `omni-todo list --tag=<tag>`                                                        |
| Output as JSON              | `omni-todo list --json`                                                             |
| View task details           | `omni-todo show <ID>`                                                               |
| Create new task             | `omni-todo add --title=... --description=... --content=... --tag=...`               |
| Create subtask              | `omni-todo add --title=... --description=... --content=... --tag=... --parent=<ID>` |
| Approve task                | `omni-todo approve --id=<ID>`                                                       |
| Start task                  | `omni-todo set --id=<ID> --status=in_progress`                                      |
| Complete task               | `omni-todo set --id=<ID> --status=completed`                                        |
| Append review note          | `omni-todo set --id=<ID> --note="..."`                                              |
| Delete task (explicit only) | `omni-todo del --id=<ID> --yes`                                                     |
| Connectivity check          | `omni-todo ping`                                                                    |
| List MCP tools              | `omni-todo tools`                                                                   |
