#!/usr/bin/env python3
from __future__ import annotations

from pathlib import Path
import sys

REQUIRED = {
    "tasks/todo.md": [
        "## Scope",
        "## Plan",
        "## Verification Commands",
    ],
    "tasks/lessons.md": [
        "## Template",
        "## Entries",
    ],
}


def main() -> int:
    repo = Path(__file__).resolve().parents[1]
    failures: list[str] = []

    for relative_path, required_markers in REQUIRED.items():
        path = repo / relative_path
        if not path.exists():
            failures.append(f"missing required file: {relative_path}")
            continue
        text = path.read_text(encoding="utf-8")
        for marker in required_markers:
            if marker not in text:
                failures.append(f"{relative_path} missing marker: {marker}")

    if failures:
        print("workflow-discipline-check: FAIL")
        for failure in failures:
            print(f" - {failure}")
        return 1

    print("workflow-discipline-check: PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
