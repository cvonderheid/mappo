# MAPPO Phase 4-5 Plan (`plans-next.md`)

Date: 2026-02-26

## Theme
Postgres-first persistence baseline:
- move control-plane storage from SQLite to Postgres,
- enforce schema lifecycle with Flyway,
- generate ORM classes from live schema using sqlacodegen.

## Verification Checklist (must stay accurate)
- [x] Flyway baseline migration applies cleanly to local Postgres.
- [x] Generated ORM model file exists and is in sync with migration schema.
- [x] Targets/releases/runs persist and reload from Postgres on startup.
- [x] In-flight runs reconcile safely on restart (`running` -> `halted`).
- [x] Deterministic 10-target seed/reset still works.
- [x] Retention prune command still works with configurable day window.
- [x] `make phase1-gate-full` remains green.

## Status Snapshot (2026-02-26)
- [x] Milestone 01 completed
- [x] Milestone 02 completed
- [x] Milestone 03 completed
- [x] Milestone 04 completed
- [x] Milestone 05 completed
- [x] Milestone 06 completed

## Phase 4 — Milestone 01: DB Workflow Alignment
**Scope**
- Add TXero-style DB targets and scripts (`db-migrate`, `db-validate`, `db-info`, `db-clean`, `db-reset`, `models-gen`).

**Acceptance criteria**
- Local commands can create/migrate/reset schema and generate models deterministically.

**Verification commands**
- `make db-migrate`
- `make db-info`
- `make models-gen`

## Phase 4 — Milestone 02: Store Migration
**Scope**
- Refactor control-plane store persistence from SQLite to Postgres using SQLAlchemy + generated models.

**Acceptance criteria**
- Existing API contract remains stable while data persists in Postgres JSONB tables.

**Verification commands**
- `make test-backend`

## Phase 4 — Milestone 03: Runtime/Script Wiring
**Scope**
- Replace SQLite settings and operational scripts with Postgres configuration defaults.

**Acceptance criteria**
- App startup, `demo-reset`, and `retention-prune` all work against Postgres.

**Verification commands**
- `make demo-reset`
- `make retention-prune RETENTION_DAYS=90`

## Phase 4 — Milestone 04: Verification + Gate Closure
**Scope**
- Update tests and docs for Phase 4 and close quality gates.

**Acceptance criteria**
- Lint/typecheck/test and phase gate commands all pass.

**Verification commands**
- `make lint`
- `make typecheck`
- `make test`
- `make phase1-gate-full`

## Phase 4 — Milestone 05: OpenAPI + Client Generation
**Scope**
- Add deterministic OpenAPI generation from FastAPI and frontend generated client types.

**Acceptance criteria**
- `backend/openapi/openapi.json` is generated from backend app contract.
- Frontend API layer uses generated schema/client types instead of handwritten shape definitions.

**Verification commands**
- `make openapi`
- `make client-gen`
- `make typecheck`

## Phase 4 — Milestone 06: Docker Compose Dev Stack
**Scope**
- Add local compose stack for DB + migration + backend + frontend with non-conflicting default ports.

**Acceptance criteria**
- Stack can be started with one command.
- Host ports do not conflict with TXero defaults.

**Verification commands**
- `make dev-up`
- `make dev-logs`
- `make dev-down`

## Phase 5 — Milestone 01: Execution Adapter Boundary
**Scope**
- Introduce execution-mode adapter boundary (`demo` + `azure`) while preserving existing orchestration/data contracts.
- Keep deterministic demo behavior as default mode for local development and tests.

**Acceptance criteria**
- Control-plane execution path delegates per-target stage events via adapter interface.
- Runtime mode is configurable through settings (`MAPPO_EXECUTION_MODE`).
- Azure mode surfaces explicit configuration/implementation errors without breaking run state handling.
- Existing API contract remains stable.

**Verification commands**
- `make openapi`
- `make client-gen`
- `make lint`
- `make typecheck`
- `make test`
- `make phase1-gate-full`

## Phase 5 — Milestone 02: Pulumi Demo Target Provisioning
**Scope**
- Add a Pulumi IaC baseline for demo target provisioning (resource group + ACA environment + ACA app per target).
- Provide a 10-target stack config template compatible with MAPPO target inventory concepts.
- Integrate IaC commands into Make workflow.

**Acceptance criteria**
- `infra/pulumi` contains a runnable Pulumi project with typed target config.
- Stack outputs include target inventory payload (`mappoTargetInventory`) for MAPPO ingestion.
- Make targets exist for install/preview/up/destroy/export.

**Verification commands**
- `make iac-install`
- `make iac-preview`
- `make lint`
- `make typecheck`
- `make test`
