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

- Date: 2026-02-25
- Pattern: UI implementation started without first applying inherited shadcn/ui standard from project conventions.
- Preventative rule: Before frontend implementation, explicitly verify design-system requirements from inherited standards and scaffold required UI primitives first.
- Detection signal: New UI files appear without shadcn primitives/config (`components.json`, `ui/*`, Tailwind integration).
- Enforcement (test/lint/checklist): frontend kickoff checklist in `tasks/todo.md` plus lint/typecheck pass after shadcn integration.

- Date: 2026-02-26
- Pattern: Leaving legacy naming/style remnants after refactors (relative imports when alias standard exists, `txero` identifiers in MAPPO compose config).
- Preventative rule: Run project-wide grep for old naming and non-standard import paths immediately after large integration changes.
- Detection signal: `rg -n "txero|from \"\\./|from \"\\.\\./"` returns hits in active project code/config where MAPPO conventions require `mappo` and `@/...`.
- Enforcement (test/lint/checklist): add grep sweep to phase-close checklist before final verification commands.
