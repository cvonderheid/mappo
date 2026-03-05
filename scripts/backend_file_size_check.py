#!/usr/bin/env python3
"""
Enforce backend Python file size limits.

Default policy:
- Any backend Python source file must be <= 750 lines.
- Temporary per-file exceptions are explicit and capped.
"""

from __future__ import annotations

import os
import sys
from dataclasses import dataclass
from pathlib import Path


MAX_LINES_DEFAULT = int(os.getenv("MAPPO_BACKEND_MAX_LINES", "750"))

# Temporary exceptions for legacy-large files. Keep these explicit and reduce over time.
MAX_LINES_EXCEPTIONS: dict[str, int] = {
    "backend/app/modules/control_plane.py": 1400,
    "backend/app/modules/execution.py": 1400,
}

EXCLUDED_PATHS: set[str] = {
    # Generated ORM code.
    "backend/app/db/generated/models.py",
}

SCAN_ROOTS: tuple[Path, ...] = (
    Path("backend/app"),
    Path("backend/tests"),
    Path("backend/scripts"),
)


@dataclass(frozen=True)
class Violation:
    path: str
    line_count: int
    max_lines: int


def _count_lines(path: Path) -> int:
    with path.open("r", encoding="utf-8") as handle:
        return sum(1 for _ in handle)


def _iter_python_files() -> list[Path]:
    results: list[Path] = []
    for root in SCAN_ROOTS:
        if not root.exists():
            continue
        for path in root.rglob("*.py"):
            if any(part.startswith(".") and part not in {".", ".."} for part in path.parts):
                continue
            results.append(path)
    return sorted(set(results))


def _find_violations() -> list[Violation]:
    violations: list[Violation] = []
    for path in _iter_python_files():
        normalized = path.as_posix()
        if normalized in EXCLUDED_PATHS:
            continue

        max_lines = MAX_LINES_EXCEPTIONS.get(normalized, MAX_LINES_DEFAULT)
        line_count = _count_lines(path)
        if line_count > max_lines:
            violations.append(
                Violation(path=normalized, line_count=line_count, max_lines=max_lines)
            )
    return violations


def main() -> int:
    violations = _find_violations()
    if not violations:
        print(
            "backend-file-size-check: PASS "
            f"(default_limit={MAX_LINES_DEFAULT}, files_scanned={len(_iter_python_files())})"
        )
        return 0

    print(
        "backend-file-size-check: FAIL "
        f"(default_limit={MAX_LINES_DEFAULT}, violations={len(violations)})"
    )
    for violation in violations:
        print(
            f"  - {violation.path}: {violation.line_count} lines "
            f"(max allowed {violation.max_lines})"
        )

    print(
        "Hint: split oversized modules, or if absolutely required, add a temporary "
        "file-specific cap in scripts/backend_file_size_check.py."
    )
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
