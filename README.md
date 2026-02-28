# MAPPO

MAPPO is a multi-tenant deployment control plane for Azure Managed Apps (CodeDeploy-style rollout management across customer subscriptions/tenants).

## Managed App Demo Quick Start
1. Install dependencies:
```bash
make install
```
2. Create/load Azure runtime credentials:
```bash
make azure-auth-bootstrap
source .data/mappo-azure.env
```
3. Refresh fleet from real managed apps and run readiness check:
```bash
make managed-demo-refresh SUBSCRIPTION_IDS="<provider-sub>,<customer-sub>" MANAGED_APP_NAME_PREFIX="<optional-prefix>"
```
4. Start backend (Azure mode) and frontend:
```bash
make dev-backend-azure
make dev-frontend
```
5. Open:
- API docs: `http://localhost:8010/api/v1/docs`
- UI: `http://localhost:5174`

## Primary Demo Commands
- `make managed-demo-refresh SUBSCRIPTION_IDS="<sub1>,<sub2>" [MANAGED_APP_NAME_PREFIX="<prefix>"]`
- `make managed-app-discover-targets SUBSCRIPTION_IDS="<sub1>,<sub2>"`
- `make import-targets`
- `make bootstrap-releases`
- `make azure-preflight`
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

## Live demo guide
- Checklist: `/Users/cvonderheid/workspace/mappo/docs/live-demo-checklist.md`

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
