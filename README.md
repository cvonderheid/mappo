# MAPPO

MAPPO is a multi-tenant deployment control plane for Azure Managed Apps (CodeDeploy-style rollout management across customer subscriptions/tenants).

## Marketplace-Accurate Demo Quick Start
1. Install dependencies:
```bash
make install
```
   - `make install` now runs full bootstrap (deps + DB migrate + checks + build).
   - If local Postgres is not up, install auto-starts/waits for compose Postgres on `localhost:5433`.
   - Use `make install-deps` if you only want dependency installation.
2. Create/load Azure runtime credentials:
```bash
make azure-auth-bootstrap
source .data/mappo-azure.env
```
3. Set publisher principal object ID for managed app definition authorization:
```bash
export MAPPO_PUBLISHER_PRINCIPAL_OBJECT_ID="<azure-ad-object-id>"
```
4. Provision managed app demo targets with Pulumi IaC:
```bash
make iac-install
make iac-stack-init
make iac-up
make iac-export-targets
make import-targets
make bootstrap-releases
```
5. Run readiness check:
```bash
make azure-preflight
```
6. Start backend (Azure mode) and frontend:
```bash
make dev-backend-azure
make dev-frontend
```
   - If you use `docker compose -f infra/docker-compose.yml up`, backend now auto-sources `/workspace/.data/mappo-azure.env` when present.
5. Open:
- API docs: `http://localhost:8010/api/v1/docs`
- UI: `http://localhost:5174`

## Primary Demo Commands
- `make iac-prepare-dual-stack CUSTOMER_SUBSCRIPTION_ID="<sub2>" [PULUMI_STACK=dual-demo]`
- `make iac-install`
- `make iac-stack-init [PULUMI_STACK=<name>]`
- `make iac-preview [PULUMI_STACK=<name>]`
- `make iac-up [PULUMI_STACK=<name>]`
- `make iac-export-targets [PULUMI_STACK=<name>]`
- `make iac-destroy [PULUMI_STACK=<name>]`
- `make managed-app-discover-targets SUBSCRIPTION_IDS="<sub1>,<sub2>"`
- `make import-targets`
- `make bootstrap-releases`
- `make managed-demo-refresh SUBSCRIPTION_IDS="<sub1>,<sub2>" [MANAGED_APP_NAME_PREFIX="<prefix>"]`
- `make dev-backend-azure`
- `make dev-frontend`

## Quality Commands
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
- `make retention-prune RETENTION_DAYS=90`

## Live Demo Guides
- Checklist: `/Users/cvonderheid/workspace/mappo/docs/live-demo-checklist.md`
- Portal playbook (manual-only steps): `/Users/cvonderheid/workspace/mappo/docs/marketplace-portal-playbook.md`
- Pulumi details: `/Users/cvonderheid/workspace/mappo/infra/pulumi/README.md`

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
