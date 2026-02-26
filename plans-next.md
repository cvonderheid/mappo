# MAPPO Phase 2 Plan (`plans-next.md`)

Date: 2026-02-25

## Theme
Deliver first runnable product slice:
- backend + UI together,
- CodeDeploy-like deployment visibility,
- deterministic demo of 10 tenant targets.

## Verification Checklist (must stay accurate)
- [x] Backend API serves health + core domain endpoints.
- [x] Frontend renders Fleet, Releases, and Run details from API.
- [x] Seed data includes 10 targets with realistic ring/region/tier tags.
- [x] Deployment run supports all-at-once and tag-wave strategies.
- [x] Per-target stage state machine is visible in run detail.
- [x] `make phase1-gate-full` remains green.
- [x] `make lint`, `make typecheck`, and `make test` run successfully.

## Status Snapshot (2026-02-25)
- [x] Milestone 01 completed
- [x] Milestone 02 completed
- [x] Milestone 03 completed
- [x] Milestone 04 completed

## Phase 2 — Milestone 01: Kickoff + Plan Refresh
**Scope**
- Align active task plan and phase plan to implementation scope.

**Key files/modules**
- `tasks/todo.md`
- `plans-next.md`

**Acceptance criteria**
- Phase plan reflects backend+UI scope and verification checklist.

**Verification commands**
- `make workflow-discipline-check`

## Phase 2 — Milestone 02: Backend Vertical Slice
**Scope**
- Build backend service with targets/releases/runs endpoints and seed data.

**Acceptance criteria**
- API returns seeded targets, releases, run summaries, and run details.

**Verification commands**
- `make lint`
- `make typecheck`
- `make test`

## Phase 2 — Milestone 03: Frontend Vertical Slice
**Scope**
- Build UI dashboard with Fleet, Releases, and Run visibility.

**Acceptance criteria**
- User can start a run from UI and inspect per-target stage progression.

**Verification commands**
- `make lint`
- `make typecheck`
- `make test`

## Phase 2 — Milestone 04: Demo Readiness
**Scope**
- Make deterministic 10-target demo path stable and easy to run.

**Acceptance criteria**
- 10-target demo path is repeatable and documented.

**Verification commands**
- `make phase1-gate-full`
- `make lint`
- `make typecheck`
- `make test`
