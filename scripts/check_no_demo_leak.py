#!/usr/bin/env python3
from __future__ import annotations

from pathlib import Path
import re

BANNED = [
    re.compile(r"demo\s*bypass", re.IGNORECASE),
    re.compile(r"hardcoded\s*demo", re.IGNORECASE),
    re.compile(r"temporary\s*demo\s*hack", re.IGNORECASE),
    re.compile(r"todo_demo_hack", re.IGNORECASE),
    re.compile(r"skip_auth_for_demo", re.IGNORECASE),
    re.compile(r"def\s+reset_demo_data\s*\(", re.IGNORECASE),
    re.compile(r"def\s+_seed_targets\s*\(", re.IGNORECASE),
    re.compile(r"def\s+_seed_releases\s*\(", re.IGNORECASE),
    re.compile(
        r"execution_mode\s*:\s*ExecutionMode\s*=\s*ExecutionMode\.DEMO",
        re.IGNORECASE,
    ),
    re.compile(r"\bcontrol_plane_storage\b", re.IGNORECASE),
]

CODE_SUFFIXES = {".py", ".ts", ".tsx", ".js", ".jsx"}
SCAN_DIRS = ["backend/app", "frontend/src"]


def main() -> int:
    repo = Path(__file__).resolve().parents[1]
    failures: list[str] = []

    for relative in SCAN_DIRS:
        base = repo / relative
        if not base.exists():
            continue
        for path in base.rglob("*"):
            if not path.is_file() or path.suffix not in CODE_SUFFIXES:
                continue
            text = path.read_text(encoding="utf-8", errors="ignore")
            for pattern in BANNED:
                if pattern.search(text):
                    failures.append(f"{path.relative_to(repo)} matched banned pattern: {pattern.pattern}")

    if failures:
        print("check-no-demo-leak: FAIL")
        for failure in failures:
            print(f" - {failure}")
        return 1

    print("check-no-demo-leak: PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
