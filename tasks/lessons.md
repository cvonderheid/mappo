# MAPPO Lessons Log

Purpose: capture recurring correction patterns and preventative guardrails.

## Template
- Date:
- Pattern:
- Preventative rule:
- Detection signal:
- Enforcement (test/lint/checklist):

## Entries
- Date: 2026-02-25
- Pattern: Starting implementation before governance/process contract is explicitly established.
- Preventative rule: For non-trivial work, update `tasks/todo.md` and relevant `plans*.md` before writing implementation code.
- Detection signal: Code changes appear without corresponding plan/checklist updates.
- Enforcement (test/lint/checklist): `make workflow-discipline-check` in phase gates.
