#!/usr/bin/env python3
from __future__ import annotations

from pathlib import Path
import sys

REQUIRED_PRINCIPLES = [
    "Determinism first",
    "Source-of-truth consistency",
    "No demo leakage in production paths",
    "Contract-before-change",
    "Legibility is required",
]

REQUIRED_ARCHITECTURE_MARKERS = [
    "## Core Model",
    "## Control / Data / Verification Boundaries",
    "## Determinism + Legibility Contract",
]


def main() -> int:
    repo = Path(__file__).resolve().parents[1]
    failures: list[str] = []

    principles_path = repo / "docs/golden-principles.md"
    architecture_path = repo / "docs/architecture.md"

    if not principles_path.exists():
        failures.append("missing required file: docs/golden-principles.md")
    else:
        text = principles_path.read_text(encoding="utf-8")
        for marker in REQUIRED_PRINCIPLES:
            if marker not in text:
                failures.append(f"docs/golden-principles.md missing principle: {marker}")

    if not architecture_path.exists():
        failures.append("missing required file: docs/architecture.md")
    else:
        text = architecture_path.read_text(encoding="utf-8")
        for marker in REQUIRED_ARCHITECTURE_MARKERS:
            if marker not in text:
                failures.append(f"docs/architecture.md missing marker: {marker}")

    if failures:
        print("golden-principles-check: FAIL")
        for failure in failures:
            print(f" - {failure}")
        return 1

    print("golden-principles-check: PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
