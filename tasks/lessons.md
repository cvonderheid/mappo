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

- Date: 2026-02-26
- Pattern: UI behavior regressions slipped because frontend tests only asserted broad render smoke and did not assert critical operator controls (release selector presence, group member preview, action enable/disable states).
- Preventative rule: For UI changes affecting deployment controls, add explicit assertions for control presence, disabled/enabled behavior, and screen-level visibility in both component tests and browser-flow checks.
- Detection signal: UI change lands with no test asserting expected control labels/state transitions; only snapshot/smoke checks pass.
- Enforcement (test/lint/checklist): require at least one frontend behavior test update per UX change and add Playwright smoke checks for Deployments critical path in phase gate.

- Date: 2026-02-26
- Pattern: Progress UI represented terminal completion as all-success (single-color 100%) and hid failed-target composition.
- Preventative rule: For rollout status UI, always visualize outcome composition with distinct segments/colors and assert failed-segment rendering in tests.
- Detection signal: Halted/partial runs display a fully-success color bar or labels that imply all-success despite non-zero failed count.
- Enforcement (test/lint/checklist): require progress-component tests (or Playwright checks) that validate presence of failed segments when `failed_targets > 0`.
