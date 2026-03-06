#!/usr/bin/env python3
from __future__ import annotations

from pathlib import Path
import sys

REQUIRED_FILES = [
    "README.md",
    "plans.md",
    "plans-next.md",
    "docs/architecture.md",
    "docs/documentation.md",
    "docs/engineering-playbook.md",
    "docs/golden-principles.md",
    "docs/implement.md",
    "docs/plans.md",
]

NO_MAKE_REFERENCE_FILES = [
    "README.md",
    "plans.md",
    "plans-next.md",
    "docs/architecture.md",
    "docs/documentation.md",
    "docs/engineering-playbook.md",
    "docs/golden-principles.md",
    "docs/implement.md",
    "docs/live-demo-checklist.md",
    "docs/marketplace-forwarder-runbook.md",
    "docs/marketplace-portal-playbook.md",
    "docs/runtime-aca-runbook.md",
]

FORBIDDEN_ACTIVE_DOC_MARKERS = [
    "backend-java",
    "FastAPI",
    "uv run",
    "SQLAlchemy-style",
]

CONTENT_RULES = {
    "plans.md": ["## Status", "## Milestone Plan", "## Review Checklist Before Coding"],
    "plans-next.md": ["## Verification Checklist", "## Status Snapshot", "## Phase"],
    "docs/documentation.md": ["## Engineering workflow discipline (before implementation)"],
    "pom.xml": ["<module>backend</module>", "<module>frontend</module>"],
    "README.md": [
        "./mvnw -pl backend verify",
        "./mvnw -pl frontend package",
        "/Users/cvonderheid/workspace/mappo/backend/target/openapi/openapi.json",
    ],
}


def main() -> int:
    repo = Path(__file__).resolve().parents[1]
    failures: list[str] = []

    for relative in REQUIRED_FILES:
        path = repo / relative
        if not path.exists():
            failures.append(f"missing required documentation file: {relative}")

    for relative, markers in CONTENT_RULES.items():
        path = repo / relative
        if not path.exists():
            continue
        text = path.read_text(encoding="utf-8")
        for marker in markers:
            if marker not in text:
                failures.append(f"{relative} missing marker: {marker}")

    for relative in NO_MAKE_REFERENCE_FILES:
        path = repo / relative
        if not path.exists():
            continue
        text = path.read_text(encoding="utf-8")
        if "make " in text or "`make" in text:
            failures.append(f"{relative} still references removed Makefile workflow")
        for marker in FORBIDDEN_ACTIVE_DOC_MARKERS:
            if marker in text:
                failures.append(f"{relative} still references removed Python-era marker: {marker}")

    if failures:
        print("docs-consistency-check: FAIL")
        for failure in failures:
            print(f" - {failure}")
        return 1

    print("docs-consistency-check: PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
