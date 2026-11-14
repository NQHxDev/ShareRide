# Continuous Deployment (CD) Failure Report

**Workflow:** `{{WORKFLOW_NAME}}` (Staging / Production)
**Environment:** `{{ENVIRONMENT}}`
**Trigger Event:** `{{TRIGGER_EVENT}}` (Push / Workflow Dispatch)
**Associated PR / Commit:** #{{PR_NUMBER}} (`{{HEAD_SHA}}`)
**Run URL:** [View Deployment Run]({{RUN_URL}})
**Failed Stage:** `{{FAILED_STAGE}}` (e.g., `build-and-push` / `deploy-staging` / `deploy-production`)

---

## Failure Summary

{{FAILURE_SUMMARY}}

---

### Deployment Log Excerpt

```txt
{{DEPLOYMENT_LOG_EXCERPT}}
```

---

## Triage & Diagnostics

- **Target Service:** `{{TARGET_SERVICE}}`
- **Failure Point:**
    - [ ] Docker Buildx compilation / Dockerfile syntax error
    - [ ] Docker Hub credentials / Registry push timeout
    - [ ] SSH Host connection / Key verification error
    - [ ] SCP configuration copy failed
    - [ ] Remote environment variable (`.env`) missing key
    - [ ] Docker Compose startup failure or crashed container
    - [ ] Database migration / Connectivity probe failure

---

## Action Items

- [ ] Inspect remote container status: `docker compose logs {{TARGET_SERVICE}}`
- [ ] Verify environment secrets in GitHub Settings -> Environments -> `{{ENVIRONMENT}}`
- [ ] Re-trigger deployment after fixing configuration / image
- [ ] Sync status with `omni-todo`

---

_Created automatically via ShareRide `github-mcp` skill_
