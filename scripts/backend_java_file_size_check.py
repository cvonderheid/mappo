#!/usr/bin/env python3
"""Enforce backend Java file size limits.

Rules:
- Any backend Java source file must be <= 750 lines by default.
- Threshold can be overridden with MAPPO_BACKEND_JAVA_MAX_LINES.
"""

from __future__ import annotations

import argparse
import os
import sys
from pathlib import Path

MAX_LINES_DEFAULT = int(os.getenv("MAPPO_BACKEND_JAVA_MAX_LINES", "750"))


def iter_java_files(root: Path):
    for path in sorted(root.rglob("*.java")):
        if "/target/" in str(path).replace("\\", "/"):
            continue
        yield path


def line_count(path: Path) -> int:
    with path.open("r", encoding="utf-8") as handle:
        return sum(1 for _ in handle)


def main() -> int:
    parser = argparse.ArgumentParser(description="Check backend Java file size limits")
    parser.add_argument(
        "--root",
        default="backend-java/src/main/java",
        help="Root directory to scan for Java source files",
    )
    parser.add_argument(
        "--max-lines",
        type=int,
        default=MAX_LINES_DEFAULT,
        help=f"Maximum allowed lines per file (default: {MAX_LINES_DEFAULT})",
    )
    args = parser.parse_args()

    root = Path(args.root)
    if not root.exists():
        print(f"backend-java-file-size-check: FAIL root not found: {root}")
        return 1

    violations: list[tuple[int, Path]] = []
    checked = 0

    for java_file in iter_java_files(root):
        checked += 1
        lines = line_count(java_file)
        if lines > args.max_lines:
            violations.append((lines, java_file))

    if violations:
        print(
            f"backend-java-file-size-check: FAIL {len(violations)} files exceed {args.max_lines} lines "
            f"(checked {checked})"
        )
        for lines, path in sorted(violations, key=lambda item: item[0], reverse=True):
            print(f"  {path}:{lines}")
        return 1

    print(
        f"backend-java-file-size-check: PASS checked={checked} max_lines={args.max_lines}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
