# MAPPO Engineering Playbook

Date: 2026-02-25

## Purpose
Codify how MAPPO work is planned, executed, verified, and learned from so quality improves consistently and regressions drop over time.

## Operating Rules

### 1) Plan First for Non-trivial Work
- Enter plan mode for work that has:
  - 3+ meaningful steps, or
  - architecture/data model impact, or
  - expected touch across multiple modules.
- Write the plan in `/Users/cvonderheid/workspace/mappo/tasks/todo.md` before implementation.
- If assumptions break, stop and re-plan in `tasks/todo.md`.

### 2) Execute with Focused Workstreams
- Keep each workstream single-purpose.
- Prefer small, reviewable diffs.
- Parallelize only independent discovery or verification tasks.

### 3) Verification Before Done
- Do not mark work complete without proof.
- Minimum for non-trivial changes:
  - relevant backend/frontend tests,
  - relevant UI/e2e verification when UI is touched,
  - command output confirming behavior.

### 4) Root Cause Over Bandaids
- No demo-specific runtime hacks in production paths.
- Fix underlying contract/scope/state issues and add regression coverage.

### 5) Lessons Loop (High-signal Only)
- After recurring/systemic corrections, update `/Users/cvonderheid/workspace/mappo/tasks/lessons.md`.
- Do not log one-off wording/taste edits.

## Required Working Files
- `/Users/cvonderheid/workspace/mappo/tasks/todo.md`
- `/Users/cvonderheid/workspace/mappo/tasks/lessons.md`

`tasks/todo.md` must include:
- scope/objective,
- checkable plan items with status,
- verification commands,
- results/review notes.

`tasks/lessons.md` must include:
- pattern/problem,
- preventative rule,
- detection signal,
- enforcement path.

## Definition of Done (MAPPO)
1. Plan items in `tasks/todo.md` are marked complete.
2. Verification commands in `tasks/todo.md` were run and captured.
3. Regressions are covered by tests or explicit gate checks.
4. If a recurring pattern appeared, `tasks/lessons.md` is updated.
5. Relevant phase gate is green (`phaseX-gate-fast` minimum).

## Enforcement
- Use `make workflow-discipline-check` to validate required playbook artifacts.
- Include discipline checks in phase gates.
