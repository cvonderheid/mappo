# MAPPO

MAPPO is a multi-tenant deployment control plane for Azure Managed Apps (CodeDeploy-style rollout management across customer subscriptions/tenants).

## Current Slice
- Python/FastAPI backend with seeded demo inventory (10 targets).
- React/TypeScript dashboard for Fleet, Releases, and Deployment Runs.
- Simulated per-target rollout state machine with retry/resume controls.
- Postgres-backed persistence for targets/releases/runs (schema managed by Flyway).

## Quick start
1. Install dependencies:
```bash
make install
```
2. Run backend:
```bash
make dev-backend
```
3. Run frontend:
```bash
make dev-frontend
```
4. Open:
- API docs: `http://localhost:8010/api/v1/docs`
- UI: `http://localhost:5174`

## Full stack (Docker Compose)
```bash
make dev-up
make dev-logs
make dev-down
```
Default host ports:
- API: `8010`
- UI: `5174`
- Postgres: `5433`

## Core quality commands
- `make workflow-discipline-check`
- `make docs-consistency-check`
- `make golden-principles-check`
- `make check-no-demo-leak`
- `make phase1-gate-fast`
- `make phase1-gate-full`
- `make lint`
- `make typecheck`
- `make test`
- `make openapi`
- `make client-gen`

## Demo operations
- `make demo-reset` (reseed deterministic 10-tenant demo data)
- `make retention-prune RETENTION_DAYS=90` (prune run history by retention window)

## Database workflow
- `make db-migrate`
- `make db-validate`
- `make db-info`
- `make db-clean`
- `make db-reset`
- `make models-gen`

## Working files
- `/Users/cvonderheid/workspace/mappo/tasks/todo.md`
- `/Users/cvonderheid/workspace/mappo/tasks/lessons.md`
- `/Users/cvonderheid/workspace/mappo/plans.md`
- `/Users/cvonderheid/workspace/mappo/plans-next.md`
