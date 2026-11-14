# CI Pipeline Failure Report

**Workflow:** `Continuous Integration`
**Associated PR:** #{{PR_NUMBER}}
**Run ID / URL:** [{{RUN_ID}}]({{RUN_URL}})
**Failed Job:** `{{FAILED_JOB_NAME}}`
**Failed Step:** `{{FAILED_STEP_NAME}}`
**Target Module:** `{{TARGET_MODULE}}`
**Head Commit:** `{{HEAD_SHA}}`

---

## Failure Summary

{{FAILURE_SUMMARY}}

---

### Error Log Excerpt

```txt
{{ERROR_LOG_EXCERPT}}
```

---

## Root Cause Analysis

- **Category:** `{{FAILURE_CATEGORY}}` (e.g. Spotless Formatting / Checkstyle Violation / Unit Test Failure / Integration Test Failure / ArchUnit Assertion / Maven Build)
- **Root Cause:** {{ROOT_CAUSE_DESCRIPTION}}

---

## Remediation Plan

- [ ] Reproduce locally: `{{LOCAL_REPRODUCE_COMMAND}}`
- [ ] Apply necessary code / formatting / test fix
- [ ] Re-run local verification: `mvn spotless:apply && mvn checkstyle:check && mvn test -pl {{TARGET_MODULE}}`
- [ ] Push fix commit to PR branch
- [ ] Update tracking task in `omni-todo`

---

_Created automatically via ShareRide `github-mcp` skill_
