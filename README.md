# MAPPO

MAPPO is a multi-tenant deployment control plane for Azure Managed Apps (CodeDeploy-style rollout management across customer subscriptions/tenants).

## Current Slice
- Python/FastAPI backend with seeded demo inventory (10 targets).
- React/TypeScript dashboard for Fleet, Releases, and Deployment Runs.
- Simulated per-target rollout state machine with retry/resume controls.
- Postgres-backed persistence for targets/releases/runs (schema managed by Flyway).
- Execution-mode boundary (`demo` default, `azure` adapter scaffold for live integration).

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

## Demo IaC (Pulumi)
- Pulumi project path: `/Users/cvonderheid/workspace/mappo/infra/pulumi`
- Stack default: `dev` (override with `PULUMI_STACK=<stack>`)
- Local Pulumi backend is used by default via Make targets (`pulumi login --local`).
- Default local secrets passphrase: `mappo-local-dev` (override with `PULUMI_CONFIG_PASSPHRASE`).
- Commands:
  - `make iac-install`
  - `make iac-preview`
  - `make iac-up`
  - `make iac-export-targets`
  - `make iac-destroy`
- 10-target config template:
  - `/Users/cvonderheid/workspace/mappo/infra/pulumi/Pulumi.demo-10tenants.yaml.example`

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

## Execution modes
- `MAPPO_EXECUTION_MODE=demo` (default): deterministic seeded behavior for local dev/test.
- `MAPPO_EXECUTION_MODE=azure`: enables Azure executor boundary mode.
- Azure mode expects credential env vars:
  - `MAPPO_AZURE_TENANT_ID`
  - `MAPPO_AZURE_CLIENT_ID`
  - `MAPPO_AZURE_CLIENT_SECRET`

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
