#!/usr/bin/env python3
"""
ShareRide - Pull Request Diagnostics & Issue Payload Generator
Helper utility for the github-mcp skill.

Specialized for:
1. CI Failures (Maven, Spotless, Checkstyle, ArchUnit, Testcontainers, Surefire)
2. Codecov Coverage Regressions (Patch coverage drop, uncovered lines)
3. CD Failures (Docker build/push, Staging/Prod SSH deployment, runtime compose)
4. GitHub Issue payload generation with omni-todo linking
"""

import argparse
import json
import os
import re
import subprocess
import sys
from typing import Any, Dict, List, Optional, Tuple

def run_cmd(cmd: List[str]) -> Tuple[int, str, str]:
    """Execute a shell command safely."""
    try:
        proc = subprocess.run(
            cmd,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
            check=False
        )
        return proc.returncode, proc.stdout.strip(), proc.stderr.strip()
    except Exception as e:
        return 1, "", str(e)


def extract_error_slice(raw_log: str, max_lines: int = 60) -> str:
    """
    Extract relevant failure lines from verbose Maven, Docker, or CI/CD logs.
    Filters out noise such as artifact downloads, progress bars, and standard setup steps.
    """
    lines = raw_log.splitlines()
    error_markers = [
        r"\[ERROR\]",
        r"BUILD FAILURE",
        r"Tests run:.*Failures: [1-9]",
        r"Tests run:.*Errors: [1-9]",
        r"AssertionError:",
        r"Architecture Violation",
        r"Spotless.*failed",
        r"Checkstyle.*violation",
        r"Error response from daemon:",
        r"failed to solve with frontend",
        r"::error::",
        r"Host key verification failed",
        r"Connection refused",
        r"FATAL",
        r"Exception in thread",
    ]
    regex = re.compile("|".join(error_markers), re.IGNORECASE)

    matched_indices = [i for i, line in enumerate(lines) if regex.search(line)]
    if not matched_indices:
        # Fallback to the last lines if no explicit markers found
        return "\n".join(lines[-max_lines:])

    # Find the cluster of errors
    start_idx = max(0, matched_indices[0] - 5)
    end_idx = min(len(lines), matched_indices[-1] + 20)

    selected_lines = lines[start_idx:end_idx]
    if len(selected_lines) > max_lines:
        selected_lines = selected_lines[:max_lines] + ["... [truncated for brevity] ..."]

    return "\n".join(selected_lines)


def parse_codecov_comment(comment_body: str) -> Dict[str, Any]:
    """
    Extract patch coverage, project coverage, and uncovered lines from Codecov bot comments.
    """
    result = {
        "patch_coverage": "Unknown",
        "project_coverage": "Unknown",
        "target_coverage": "80%",
        "uncovered_files": []
    }

    # Extract Patch Coverage
    patch_match = re.search(r"Patch(?: Coverage)?\s*\|\s*([0-9\.]+)%", comment_body, re.IGNORECASE)
    if not patch_match:
        patch_match = re.search(r"([0-9\.]+)%\s*of diff hit", comment_body, re.IGNORECASE)
    if patch_match:
        result["patch_coverage"] = f"{patch_match.group(1)}%"

    # Extract Project Coverage
    proj_match = re.search(r"Project(?: Coverage)?\s*\|\s*([0-9\.]+)%", comment_body, re.IGNORECASE)
    if proj_match:
        result["project_coverage"] = f"{proj_match.group(1)}%"

    # Extract file lines breakdown
    # Codecov tables typically have lines like: `| file.java | 50.0% | 2 |`
    file_lines = re.findall(r"\|\s*([a-zA-Z0-9_\-\.\/]+\.java)\s*\|\s*([0-9\.]+)%.*?\|\s*([0-9\-,# ]+)?\s*\|", comment_body)
    for f in file_lines:
        file_path, cov, lines_uncovered = f
        result["uncovered_files"].append({
            "file": file_path.strip(),
            "coverage": f"{cov.strip()}%",
            "uncovered_lines": lines_uncovered.strip() if lines_uncovered else "N/A"
        })

    return result


def classify_check_runs(check_runs: List[Dict[str, Any]]) -> Dict[str, List[Dict[str, Any]]]:
    """
    Categorize check runs into CI, Codecov, CD, and other.
    """
    categorized: Dict[str, List[Dict[str, Any]]] = {
        "ci_failed": [],
        "ci_passed": [],
        "codecov_failed": [],
        "codecov_passed": [],
        "cd_failed": [],
        "cd_passed": [],
        "other": []
    }

    for run in check_runs:
        name = run.get("name", "").lower()
        conclusion = (run.get("conclusion") or run.get("status") or "").lower()
        is_failure = conclusion in ["failure", "cancelled", "timed_out", "action_required"]

        if "codecov" in name:
            if is_failure:
                categorized["codecov_failed"].append(run)
            else:
                categorized["codecov_passed"].append(run)
        elif any(cd_kw in name for cd_kw in ["deploy", "cd-", "docker-build", "staging", "production"]):
            if is_failure:
                categorized["cd_failed"].append(run)
            else:
                categorized["cd_passed"].append(run)
        elif any(ci_kw in name for ci_kw in ["code quality", "spotless", "checkstyle", "unit test", "integration", "package", "ci", "build"]):
            if is_failure:
                categorized["ci_failed"].append(run)
            else:
                categorized["ci_passed"].append(run)
        else:
            categorized["other"].append(run)

    return categorized


def generate_triage_markdown(
    pr_number: int,
    pr_title: str,
    branch: str,
    categories: Dict[str, List[Dict[str, Any]]],
    codecov_data: Optional[Dict[str, Any]] = None,
    log_excerpt: Optional[str] = None
) -> str:
    """Generate a clean, structured diagnostic report in GitHub-flavored Markdown."""
    report = []
    report.append(f"# Pull Request #{pr_number} Diagnostics Report")
    report.append(f"**Title**: {pr_title}  ")
    report.append(f"**Branch**: `{branch}`  \n")

    # Overall Status Summary
    ci_fails = len(categories["ci_failed"])
    cov_fails = len(categories["codecov_failed"])
    cd_fails = len(categories["cd_failed"])

    if ci_fails == 0 and cov_fails == 0 and cd_fails == 0:
        report.append("> [!NOTE]\n> All CI, Codecov, and CD checks are currently **passing**.\n")
        return "\n".join(report)

    report.append("## Diagnostic Summary\n")
    if ci_fails > 0:
        report.append(f"- **CI Status**: FAILED ({ci_fails} failed check{'s' if ci_fails > 1 else ''})")
    else:
        report.append("- **CI Status**: PASSED")

    if cov_fails > 0:
        report.append(f"- **Codecov Status**: FAILED (Coverage below threshold)")
    else:
        report.append("- **Codecov Status**: PASSED / SKIPPED")

    if cd_fails > 0:
        report.append(f"- **CD Status**: FAILED ({cd_fails} failed deployment stage{'s' if cd_fails > 1 else ''})")
    else:
        report.append("- **CD Status**: PASSED / NOT TRIGGERED")

    report.append("")

    # Section 1: CI Failures
    if ci_fails > 0:
        report.append("### 1. CI Pipeline Failures")
        for run in categories["ci_failed"]:
            report.append(f"- **Check**: `{run.get('name')}` | **Conclusion**: `{run.get('conclusion')}`")
            if run.get("html_url"):
                report.append(f"  - Run Link: {run.get('html_url')}")
            if run.get("output", {}).get("title"):
                report.append(f"  - Details: {run.get('output', {}).get('title')}")
        report.append("")

    # Section 2: Codecov Regressions
    if cov_fails > 0 or (codecov_data and codecov_data.get("patch_coverage") != "Unknown"):
        report.append("### 2. Codecov Coverage Diagnostics")
        if codecov_data:
            report.append(f"- **Patch Coverage**: `{codecov_data.get('patch_coverage')}` (Target: {codecov_data.get('target_coverage')})")
            report.append(f"- **Project Coverage**: `{codecov_data.get('project_coverage')}`")
            if codecov_data.get("uncovered_files"):
                report.append("\n**Uncovered Files & Lines in this PR:**")
                report.append("| File | Coverage | Uncovered Lines |")
                report.append("| --- | --- | --- |")
                for item in codecov_data["uncovered_files"]:
                    report.append(f"| `{item['file']}` | {item['coverage']} | `{item['uncovered_lines']}` |")
        report.append("")

    # Section 3: CD Failures
    if cd_fails > 0:
        report.append("### 3. Continuous Deployment (CD) Failures")
        for run in categories["cd_failed"]:
            report.append(f"- **Check**: `{run.get('name')}` | **Conclusion**: `{run.get('conclusion')}`")
            if run.get("html_url"):
                report.append(f"  - Deployment Link: {run.get('html_url')}")
        report.append("")

    # Section 4: Log Excerpt
    if log_excerpt:
        report.append("### 4. Extracted Error Log Excerpt")
        report.append("```txt")
        report.append(log_excerpt)
        report.append("```\n")

    # Section 5: Remediation Recommendations
    report.append("### 5. Recommended Actions")
    if ci_fails > 0:
        report.append("- [ ] Run `mvn spotless:apply && mvn checkstyle:check` if code-quality failed.")
        report.append("- [ ] Check unit or integration test failures locally with `./mvnw test -pl <module>`.")
    if cov_fails > 0:
        report.append("- [ ] Add targeted unit tests for uncovered lines indicated in the Codecov section.")
    if cd_fails > 0:
        report.append("- [ ] Check Docker build context or SSH secrets configuration for deployment target.")

    return "\n".join(report)


def build_issue_payload(
    issue_type: str,
    pr_number: int,
    title: str,
    summary: str,
    log_snippet: str,
    service_name: Optional[str] = None
) -> Dict[str, Any]:
    """
    Construct a JSON payload conforming to GitHub issue creation specs.
    """
    labels = ["bug"]
    type_prefix = "[CI/CD]"

    if issue_type == "ci":
        labels.extend(["ci-failure", "automated-triage"])
        type_prefix = "[CI Failure]"
    elif issue_type == "codecov":
        labels.extend(["codecov", "testing", "coverage-regression"])
        type_prefix = "[Codecov]"
    elif issue_type == "cd":
        labels.extend(["cd-failure", "deployment", "critical"])
        type_prefix = "[CD Failure]"

    if service_name:
        labels.append(service_name)

    body = f"""## Failure Details

**Associated PR:** #{pr_number}
**Type:** `{issue_type.upper()}`
**Target Service:** `{service_name or 'monorepo'}`

### Summary
{summary}

### Error Excerpt
```txt
{log_snippet}
```

### Remediation Checklist
- [ ] Investigate root cause in PR #{pr_number}
- [ ] Fix failing checks / add missing tests
- [ ] Verify locally before re-pushing
- [ ] Link resolution to omni-todo

---
*Created via ShareRide GitHub MCP Diagnostic Skill*
"""

    return {
        "title": f"{type_prefix} {title} (PR #{pr_number})",
        "body": body.strip(),
        "labels": labels
    }


def main():
    parser = argparse.ArgumentParser(description="ShareRide PR Diagnostics & Issue Formatter")
    subparsers = parser.add_subparsers(dest="command", required=True)

    # Subcommand: parse-log
    parse_parser = subparsers.add_parser("parse-log", help="Extract error slice from raw logs")
    parse_parser.add_argument("--file", "-f", help="Path to raw log file (reads stdin if omitted)")
    parse_parser.add_argument("--lines", "-n", type=int, default=60, help="Max lines to extract")

    # Subcommand: triage
    triage_parser = subparsers.add_parser("triage", help="Triage PR failures from JSON checks and logs")
    triage_parser.add_argument("--pr", type=int, required=True, help="Pull Request number")
    triage_parser.add_argument("--title", default="Unknown PR Title", help="Pull Request title")
    triage_parser.add_argument("--branch", default="unknown", help="Head branch name")
    triage_parser.add_argument("--checks-file", help="Path to JSON file containing check_runs array")
    triage_parser.add_argument("--comments-file", help="Path to JSON file containing PR comments array")
    triage_parser.add_argument("--log-file", help="Path to raw job log file")

    # Subcommand: make-issue
    issue_parser = subparsers.add_parser("make-issue", help="Generate GitHub Issue JSON payload")
    issue_parser.add_argument("--type", choices=["ci", "codecov", "cd", "bug"], required=True)
    issue_parser.add_argument("--pr", type=int, required=True, help="PR number")
    issue_parser.add_argument("--title", required=True, help="Issue title")
    issue_parser.add_argument("--summary", required=True, help="Summary description")
    issue_parser.add_argument("--log-file", help="Path to error log excerpt")
    issue_parser.add_argument("--service", help="Service name (e.g. auth-service)")

    args = parser.parse_args()

    if args.command == "parse-log":
        if args.file and os.path.exists(args.file):
            with open(args.file, "r", encoding="utf-8", errors="replace") as f:
                content = f.read()
        else:
            content = sys.stdin.read()
        print(extract_error_slice(content, args.lines))

    elif args.command == "triage":
        check_runs = []
        if args.checks_file and os.path.exists(args.checks_file):
            with open(args.checks_file, "r", encoding="utf-8") as f:
                data = json.load(f)
                check_runs = data.get("check_runs", data if isinstance(data, list) else [])

        codecov_info = None
        if args.comments_file and os.path.exists(args.comments_file):
            with open(args.comments_file, "r", encoding="utf-8") as f:
                comments = json.load(f)
                if isinstance(comments, dict):
                    comments = comments.get("comments", [])
                for comment in comments:
                    body = comment.get("body", "")
                    if "codecov" in comment.get("user", {}).get("login", "").lower() or "codecov report" in body.lower():
                        codecov_info = parse_codecov_comment(body)
                        break

        log_slice = None
        if args.log_file and os.path.exists(args.log_file):
            with open(args.log_file, "r", encoding="utf-8", errors="replace") as f:
                log_slice = extract_error_slice(f.read())

        categories = classify_check_runs(check_runs)
        report = generate_triage_markdown(
            pr_number=args.pr,
            pr_title=args.title,
            branch=args.branch,
            categories=categories,
            codecov_data=codecov_info,
            log_excerpt=log_slice
        )
        print(report)

    elif args.command == "make-issue":
        log_snippet = "No error log provided."
        if args.log_file and os.path.exists(args.log_file):
            with open(args.log_file, "r", encoding="utf-8", errors="replace") as f:
                log_snippet = extract_error_slice(f.read())

        payload = build_issue_payload(
            issue_type=args.type,
            pr_number=args.pr,
            title=args.title,
            summary=args.summary,
            log_snippet=log_snippet,
            service_name=args.service
        )
        print(json.dumps(payload, indent=2))


if __name__ == "__main__":
    main()
