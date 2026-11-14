---
name: github-mcp
description: >-
    Use this skill to monitor CI/CD workflows, inspect GitHub Actions run failures and logs, manage GitHub issues,
    and perform repository code searches using the GitHub MCP server in ShareRide. Prohibits automated PR creation and direct code push.
compatibility: Requires GitHub MCP server configured in .mcp.json and active repository access
metadata:
    author: NQHxZeion
---

# GitHub MCP Skill - CI/CD Monitoring & Issue Management

## Purpose

This skill guides agents on utilizing the GitHub MCP Server to:

1. **Monitor and analyze CI/CD failures** (primary use case)
2. **Create and manage GitHub Issues**
3. **Perform code search and repository analysis**

**IMPORTANT NOTICE**: This skill **STRICTLY PROHIBITS** creating Pull Requests or pushing code automatically. Pull Requests must be created manually by developers.

## Prerequisites

- `.mcp.json` configured with the GitHub MCP Server
- GitHub authentication (OAuth or PAT) configured and active
- Agent access permissions granted for the `ShareRide` repository

## Configuration

The GitHub MCP Server is declared in `.mcp.json`:

```json
{
    "mcpServers": {
        "todo": {
            "type": "sse",
            "url": "http://127.0.0.1:20130/mcp/todo"
        },
        "github": {
            "type": "http",
            "url": "https://api.githubcopilot.com/mcp/"
        }
    }
}
```

## Use Case 1: CI/CD Monitoring & Troubleshooting

### Typical Workflow on CI/CD Failure

#### Step 1: List workflow runs to find failures

```bash
# MCP tool: list_workflow_runs
# Parameters:
# - owner: "anhjkr" (or organization name)
# - repo: "ShareRide"
# - status: "failure" or "completed"
# - event: "push" or "pull_request"
# - perPage: 10 (number of runs to retrieve)
```

**Example response structure:**

```json
{
    "workflow_runs": [
        {
            "id": 123456789,
            "name": "CI Pipeline",
            "status": "completed",
            "conclusion": "failure",
            "head_branch": "feature/new-service",
            "head_sha": "abc123def456...",
            "created_at": "2024-01-15T10:30:00Z",
            "html_url": "https://github.com/anhjkr/ShareRide/actions/runs/123456789"
        }
    ]
}
```

#### Step 2: Get workflow run details

```bash
# MCP tool: get_workflow_run
# Parameters:
# - owner: "anhjkr"
# - repo: "ShareRide"
# - runId: 123456789 (from list above)
```

**Key fields to examine:**

- `conclusion`: failure, success, cancelled
- `head_sha`: commit that triggered the run
- `head_branch`: active branch name
- `event`: trigger event (push, pull_request, schedule, etc.)
- `jobs_url`: URL to retrieve jobs breakdown

#### Step 3: List jobs of a workflow run

```bash
# MCP tool: list_workflow_run_jobs
# Parameters:
# - owner: "anhjkr"
# - repo: "ShareRide"
# - runId: 123456789
```

**Example response:**

```json
{
    "jobs": [
        {
            "id": 987654321,
            "name": "build-and-test",
            "status": "completed",
            "conclusion": "failure",
            "started_at": "2024-01-15T10:31:00Z",
            "completed_at": "2024-01-15T10:35:00Z",
            "steps": [
                {
                    "name": "Checkout code",
                    "status": "completed",
                    "conclusion": "success"
                },
                {
                    "name": "Run tests",
                    "status": "completed",
                    "conclusion": "failure",
                    "number": 3
                }
            ]
        }
    ]
}
```

**Job analysis steps:**

- Identify which job failed
- Locate the specific failed step within the job
- Note `job.id` to extract execution logs

#### Step 4: Retrieve failed job logs

```bash
# MCP tool: get_workflow_run_job_logs
# Parameters:
# - owner: "anhjkr"
# - repo: "ShareRide"
# - jobId: 987654321 (from jobs list)
```

**Log analysis checklist:**

- Look for error markers: `ERROR`, `FAILED`, `Exception`, `at line ...`
- Maven build errors: `[ERROR]`, `BUILD FAILURE`
- Test failures: `Tests run:`, `Failures:`, `Errors:`
- Checkstyle violations: `[ERROR] src/...`
- Docker errors: `Error response from daemon`

#### Step 5: Create an Issue if necessary

After analyzing logs and determining the root cause, create an issue to track resolution:

```bash
# MCP tool: create_issue
# Parameters:
# - owner: "anhjkr"
# - repo: "ShareRide"
# - title: "[CI/CD] Build failure in auth-service tests"
# - body: (see markdown template below)
# - labels: ["bug", "ci-cd", "high-priority"]
# - assignees: ["developer-username"]
```

**Issue template for CI/CD failures:**

````markdown
## CI/CD Failure Report

**Workflow:** [workflow name]
**Run ID:** [run id]
**Job:** [failed job name]
**Branch:** [branch name]
**Commit:** [commit sha]
**Run URL:** [github actions url]

## Error Summary

[Concise 1-2 sentence summary of failure]

## Error Details

```txt
[Paste relevant error logs here]
```

## Root Cause Analysis

[Analysis of why the failure occurred]

## Suggested Fix

- [ ] Action item 1
- [ ] Action item 2

## Related

- Related to issue #XXX
- Introduced by commit abc123
````

### Common CI/CD Error Patterns in ShareRide

#### Pattern 1: Maven Build Failure

**Indicators:**

```txt
[ERROR] Failed to execute goal ... on project auth-service
[ERROR] BUILD FAILURE
```

**Actions:**

1. Check whether recent `pom.xml` changes introduced invalid dependencies or version conflicts.
2. Verify dependency versions and parent POM compatibility.
3. Ensure root module builds cleanly locally with `mvn clean compile`.

#### Pattern 2: Test Failures

**Indicators:**

```txt
Tests run: 45, Failures: 2, Errors: 0, Skipped: 0
[ERROR] com.shareride.auth.AuthServiceTest.testLogin:123 expected: <200> but was: <500>
```

**Actions:**

1. Identify the failing test class and method.
2. Review recent changes touching that test or related production code.
3. Verify whether it is a flaky test by inspecting historical runs.

#### Pattern 3: Checkstyle Violations

**Indicators:**

```txt
[ERROR] src/main/java/com/shareride/auth/Service.java:[42,5] (imports) UnusedImports
[ERROR] src/main/java/com/shareride/auth/Controller.java:[15] (whitespace) FileTabCharacter
```

**Actions:**

1. Run `mvn spotless:apply` locally to reformat source files.
2. Run `mvn checkstyle:check` to verify all style violations are resolved.
3. Commit formatting fixes.

#### Pattern 4: ArchUnit Violations

**Indicators:**

```txt
java.lang.AssertionError: Architecture Violation
    Controllers should not depend on repositories directly
```

**Actions:**

1. Review service layer architecture.
2. Ensure Controllers only inject Services (`*Service` interfaces), never Repositories.
3. Ensure implementations follow `service-template` standards.

#### Pattern 5: Docker Build Failures

**Indicators:**

```txt
Error response from daemon: manifest for image:tag not found
failed to solve with frontend dockerfile.v0
```

**Actions:**

1. Verify Dockerfile syntax and build arguments.
2. Check base image tags and availability.
3. Inspect Docker build context and excluded paths.

## Use Case 2: Issue Management

### Create Issue from Bug Report

```bash
# Tool: create_issue
# Parameters:
# - owner: "anhjkr"
# - repo: "ShareRide"
# - title: "[BUG] Auth service returns 500 on valid credentials"
# - body: "## Description\n\n...\n\n## Steps to Reproduce\n\n1. ...\n2. ...\n\n## Expected vs Actual\n\n**Expected:** ...\n**Actual:** ...\n\n## Environment\n\n- Service: auth-service\n- Version: 1.0.0\n- Java: 21"
# - labels: ["bug", "auth-service", "priority-high"]
# - assignees: ["dev-username"]
```

### Create Issue from Task (Sync with omni-todo)

When an omni-todo task needs tracking on GitHub:

```bash
# Tool: create_issue
# Parameters:
# - title: "[TASK] Implement rate limiting for API endpoints"
# - body: "## Task from omni-todo\n\n**Task ID:** TASK-123\n\n## Description\n\n...\n\n## Acceptance Criteria\n\n- [ ] Criterion 1\n- [ ] Criterion 2\n\n## Technical Notes\n\n..."
# - labels: ["enhancement", "task"]
# - milestone: "Sprint 5"
```

### Update Issue Status

```bash
# Tool: update_issue
# Parameters:
# - owner: "anhjkr"
# - repo: "ShareRide"
# - issueNumber: 42
# - state: "closed"  # or "open"
# - body: "[Updated description]"
# - labels: ["bug", "resolved"]
```

### Add Comment to Issue

```bash
# Tool: add_issue_comment
# Parameters:
# - owner: "anhjkr"
# - repo: "ShareRide"
# - issueNumber: 42
# - body: "## Update\n\nFixed in commit abc123.\n\nTests passing: https://github.com/anhjkr/ShareRide/actions/runs/456"
```

### List Issues with Filters

```bash
# Tool: list_issues
# Parameters:
# - owner: "anhjkr"
# - repo: "ShareRide"
# - state: "open"  # "open", "closed", or "all"
# - labels: "bug,high-priority"  # comma-separated
# - assignee: "username"
# - milestone: "Sprint 5"
# - sort: "created"  # "created", "updated", or "comments"
# - direction: "desc"
# - perPage: 20
```

## Use Case 3: Code Search & Analysis

### Search Patterns Across Codebase

```bash
# Tool: search_code
# Parameters:
# - query: "BaseEntity repo:anhjkr/ShareRide language:java"
# - perPage: 10
```

**Query syntax examples:**

```txt
# Find all Controllers
"@RestController repo:anhjkr/ShareRide"

# Find all Service implementations
"ServiceImpl repo:anhjkr/ShareRide path:service/impl"

# Find TODO comments
"TODO language:java repo:anhjkr/ShareRide"

# Find specific method usage
"idempotentMessageService language:java repo:anhjkr/ShareRide"

# Find configuration files
"spring.datasource filename:application.yml repo:anhjkr/ShareRide"
```

### Get File Content

```bash
# Tool: get_file_contents
# Parameters:
# - owner: "anhjkr"
# - repo: "ShareRide"
# - path: "services/auth-service/src/main/java/com/shareride/auth/AuthApplication.java"
# - ref: "main"  # or branch name, commit SHA
```

### List Commits for Analysis

```bash
# Tool: list_commits
# Parameters:
# - owner: "anhjkr"
# - repo: "ShareRide"
# - sha: "main"  # branch name or commit SHA
# - since: "2024-01-01T00:00:00Z"
# - path: "services/auth-service"  # optional: filter by directory path
# - perPage: 20
```

## Workflow Integration with omni-todo

### Scenario: CI/CD Failure -> Create GitHub Issue -> Create omni-todo Task

1. **Agent detects CI/CD failure:**

    ```bash
    # List workflow runs and locate failed run
    # Fetch job details and logs, analyze error
    ```

2. **Create GitHub Issue to track:**

    ```bash
    # Call create_issue with relevant diagnostic information
    # Note created issue number, e.g., #123
    ```

3. **Create omni-todo task for developer assignment:**

    ```bash
    omni-todo add \
      --title="Fix CI failure in auth-service tests" \
      --description="GitHub Issue #123" \
      --content="Details from issue analysis..." \
      --tag="bug" \
      --tag="ci-cd"
    ```

4. **Update GitHub Issue when task is completed:**

    ```bash
    # Once omni-todo task status reaches completed
    # Call add_issue_comment with fix summary and commit hash
    # Call update_issue with state="closed" if issue is fully resolved
    ```

## Best Practices

### 1. CI/CD Monitoring

[DO]

- Check workflow runs periodically upon push or PR activity.
- Analyze logs thoroughly before filing an issue.
- Include relevant logs, commit SHA, and run URL in the issue body.
- Tag issues with accurate labels (`ci-cd`, `bug`, service name).
- Assign issues to the responsible author or team.

[DON'T]

- Do not create issues without inspecting log contents.
- Do not duplicate existing open issues (search before creation).
- Do not create issues for transient network glitches or one-off flakes.
- Do not dump entire log files; extract and format only relevant error excerpts.

### 2. Issue Creation

[DO]

- Use standard markdown templates for bug reports and CI/CD failures.
- Include clear reproduction steps.
- Add appropriate labels, assignees, and milestones.
- Link related issues and commit SHAs.
- Post updates to the issue as progress occurs.

[DON'T]

- Do not create vague issues without context.
- Do not omit essential labels.
- Do not create issues for trivial cosmetic or whitespace fixes.

### 3. Security

[DO]

- Use read-only operations whenever possible.
- Verify `owner` and `repo` parameters before invoking commands.
- Mask sensitive credentials or environmental variables found in logs.

[DON'T]

- **NEVER create Pull Requests automatically** (agents lack authorization for automated PR creation).
- **NEVER push code directly via MCP** (agents operate in read-mostly mode).
- Never include secrets, tokens, or passwords in issue bodies or comments.
- Never expose internal IPs or confidential credentials in public issues.

## Available Tools Reference

### CI/CD & Workflows

- `list_workflow_runs` - List workflow runs with filters
- `get_workflow_run` - Get details of a single workflow run
- `list_workflow_run_jobs` - List jobs within a workflow run
- `get_workflow_run_job_logs` - Retrieve execution logs for a job
- `list_workflows` - List all workflows configured in the repository

### Issues

- `create_issue` - Create a new GitHub issue
- `update_issue` - Update issue metadata (title, body, state, labels)
- `list_issues` - List issues with query filters
- `get_issue` - Retrieve details of a specific issue
- `add_issue_comment` - Add a comment to an existing issue
- `list_issue_comments` - List comments on an issue

### Repository & Code

- `search_code` - Search code across the repository
- `get_file_contents` - Retrieve file contents by path and ref
- `list_commits` - List commit history with optional filters
- `get_commit` - Retrieve details of a specific commit
- `search_commits` - Search commit messages and history
- `list_branches` - List repository branches
- `get_repository` - Retrieve repository metadata

### Read-Only Operations (Safe to use)

- All `list_*` operations
- All `get_*` operations
- All `search_*` operations

### Write Operations

- `create_issue` - [ALLOWED] Safe, encouraged for tracking
- `update_issue` - [ALLOWED] Safe, encouraged for status tracking
- `add_issue_comment` - [ALLOWED] Safe, encouraged for progress updates
- `create_pull_request` - [PROHIBITED] Strictly prohibited for agents
- `push_files` - [PROHIBITED] Strictly prohibited for agents

## Troubleshooting

### Issue: "Authentication required"

**Resolution:**

- Verify GitHub authentication status in your environment.
- In VS Code / editor: Toggle agent authentication or refresh GitHub OAuth.
- Alternatively, verify that `GITHUB_PERSONAL_ACCESS_TOKEN` is exported in the environment.

### Issue: "Resource not found"

**Resolution:**

- Verify that `owner` ("anhjkr") and `repo` ("ShareRide") are spelled accurately.
- Verify that access permissions are granted for the target repository.
- Verify that the specified branch or commit SHA exists in the remote repository.

### Issue: "Rate limit exceeded"

**Resolution:**

- Wait for the GitHub API rate limit reset window.
- Ensure requests are authenticated to take advantage of higher rate limits.
- Optimize query volume by increasing `perPage` and avoiding repetitive polling calls.

## Examples

### Example 1: Complete CI/CD Troubleshooting Flow

```txt
User: "CI build failed, help me debug"

Agent steps:
1. list_workflow_runs(owner="anhjkr", repo="ShareRide", status="failure", perPage=5)
2. Identify latest failure: run_id = 123456
3. get_workflow_run(runId=123456)
4. list_workflow_run_jobs(runId=123456)
5. Identify failed job: job_id = 789012
6. get_workflow_run_job_logs(jobId=789012)
7. Analyze logs -> Identified failure: "AuthServiceTest.testLogin failed"
8. search_code(query="AuthServiceTest.testLogin repo:anhjkr/ShareRide")
9. get_file_contents(path="...AuthServiceTest.java")
10. Analyze test code
11. create_issue(title="[CI/CD] Auth test failure...", body="...", labels=["bug", "ci-cd", "auth-service"])
12. Report back to user: "Created issue #145 to track this. Root cause: ..."
```

### Example 2: Monitor CI Status for Current Branch

```txt
User: "Check if my feature branch CI passed"

Agent steps:
1. list_workflow_runs(owner="anhjkr", repo="ShareRide", branch="feature/rate-limiting", perPage=1)
2. Check latest run conclusion
3. If failed: retrieve logs and summarize failure details
4. If passed: confirm successful run
```

## Summary

In ShareRide, the GitHub MCP Server is used for:

1. **CI/CD Monitoring** (Main use case)
    - Monitor workflow runs
    - Analyze failures
    - Track job logs
    - Create tracking issues for failures

2. **Issue Management** (Secondary use case)
    - Create bug reports
    - Track tasks and sync with omni-todo
    - Update issue progress
    - Comment on issue threads

3. **Code Search** (Supporting use case)
    - Locate patterns across services
    - Inspect codebase without cloning separate trees
    - Understand context and references

**EXPLICITLY PROHIBITED:**

- Creating Pull Requests
- Pushing code or branches
- Merging branches
- Any destructive repository operations

Agents operate in read-mostly mode and only write when managing issues.
