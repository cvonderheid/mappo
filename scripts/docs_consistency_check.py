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

CONTENT_RULES = {
    "plans.md": ["## Status", "## Milestone Plan", "## Review Checklist Before Coding"],
    "plans-next.md": ["## Verification Checklist", "## Status Snapshot", "## Phase"],
    "docs/documentation.md": ["## Engineering workflow discipline (before implementation)"],
    "Makefile": ["phase1-gate-fast", "phase1-gate-full"],
    "README.md": ["phase1-gate-fast", "phase1-gate-full"],
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

    if failures:
        print("docs-consistency-check: FAIL")
        for failure in failures:
            print(f" - {failure}")
        return 1

    print("docs-consistency-check: PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
