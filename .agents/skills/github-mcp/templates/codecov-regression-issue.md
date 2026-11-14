# Codecov Coverage Regression Report

**Associated PR:** #{{PR_NUMBER}}
**Head Branch:** `{{BRANCH_NAME}}`
**Head Commit:** `{{HEAD_SHA}}`
**Check Status:** `codecov/patch` failed (Required >= 80%)

---

## Coverage Overview

- **Patch Coverage:** `{{PATCH_COVERAGE}}%` (Target: >= 80.0%)
- **Project Coverage Impact:** `{{PROJECT_COVERAGE_CHANGE}}` (Current: `{{PROJECT_COVERAGE}}%`)

---

### Uncovered Files & Line Ranges

| Service / File | Patch Coverage | Uncovered Lines |
| -------------- | -------------- | --------------- |

{{UNCOVERED_FILES_TABLE}}

---

## Recommended Tests to Add

- [ ] Add unit test for `{{SERVICE_NAME}}`: `{{TEST_SUGGESTION_1}}`
- [ ] Add edge-case test covering lines `{{UNCOVERED_LINES}}`
- [ ] Re-run local JaCoCo report: `./mvnw jacoco:report -pl {{SERVICE_NAME}}`
- [ ] Verify coverage reaches target >= 80%

---

_Created automatically via ShareRide `github-mcp` skill_
