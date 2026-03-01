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
make azure-tenant-map SUBSCRIPTION_IDS="<sub1>,<sub2>"
make azure-onboard-multitenant-runtime CLIENT_ID="$MAPPO_AZURE_CLIENT_ID" SUBSCRIPTION_IDS="<sub1>,<sub2>"
```
   - For cross-tenant targets, set per-subscription tenant mapping:
```bash
export MAPPO_AZURE_TENANT_BY_SUBSCRIPTION='{"c0d51042-7d0a-41f7-b270-151e4c4ea263":"abe468b2-18bb-4dd2-90b9-5b8982337eb7","1adaaa48-139a-477b-a8c8-0e6289d6d199":"5476530d-fba1-4cd5-b2c0-fa118c5ff36e"}'
```
3. Set publisher principal object ID for managed app definition authorization:
```bash
export MAPPO_PUBLISHER_PRINCIPAL_OBJECT_ID="<azure-ad-object-id>"
```
4. Provision managed app demo targets with Pulumi IaC:
```bash
make iac-install
make iac-stack-init PULUMI_STACK=<stack>
make iac-configure-marketplace-demo PULUMI_STACK=<stack> \
  PROVIDER_SUBSCRIPTION_ID="<provider-sub-id>" \
  CUSTOMER_SUBSCRIPTION_ID="<customer-sub-id>"
cd infra/pulumi && pulumi config set --stack <stack> mappo:controlPlanePostgresEnabled true
cd infra/pulumi && pulumi config set --stack <stack> --secret mappo:controlPlanePostgresAdminPassword "<strong-password>"
make iac-up PULUMI_STACK=<stack>
make iac-export-targets PULUMI_STACK=<stack>
make iac-export-db-env PULUMI_STACK=<stack>
make import-targets
make bootstrap-releases
# Use FORCE=1 to replace existing releases with current defaults.
make bootstrap-releases FORCE=1
```
   - The stack configurator auto-resolves tenant-local principal object IDs and adds your current public IP to Postgres firewall rules for local demo connectivity (can be overridden).

   - Load managed DB env output when running backend locally:
```bash
source .data/mappo-db.env
```
5. Run readiness check:
```bash
make azure-preflight
```
   - `azure-preflight` expects at least `2` targets by default (`MAPPO_PREFLIGHT_EXPECTED_TARGET_COUNT` to override, e.g. `10`).
6. Start backend (Azure mode) and frontend:
```bash
make dev-backend-azure
make dev-frontend
```
   - If you use `docker compose -f infra/docker-compose.yml up`, backend auto-sources `/workspace/.data/mappo-azure.env` and `/workspace/.data/mappo-db.env` when present.
5. Open:
- API docs: `http://localhost:8010/api/v1/docs`
- UI: `http://localhost:5174`

## Primary Demo Commands
- `make iac-install`
- `make iac-stack-init [PULUMI_STACK=<name>]`
- `make iac-configure-marketplace-demo PROVIDER_SUBSCRIPTION_ID="<sub1>" CUSTOMER_SUBSCRIPTION_ID="<sub2>" [PULUMI_STACK=<name>]`
- `make iac-preview [PULUMI_STACK=<name>]`
- `make iac-up [PULUMI_STACK=<name>]`
- `make iac-export-targets [PULUMI_STACK=<name>]`
- `make iac-export-db-env [PULUMI_STACK=<name>]`
- `make iac-destroy [PULUMI_STACK=<name>]`
- `make azure-tenant-map SUBSCRIPTION_IDS="<sub1>,<sub2>"`
- `make import-targets`
- `make bootstrap-releases`
- `make dev-backend-azure`
- `make dev-frontend`

## Marketplace Onboarding API
- `GET /api/v1/admin/onboarding`: returns registration snapshot + recent onboarding events.
- `POST /api/v1/admin/onboarding/events`: registers/updates targets from marketplace lifecycle events (idempotent on `event_id`).
- Optional token gate: set `MAPPO_MARKETPLACE_INGEST_TOKEN`, then send `x-mappo-ingest-token` header.

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
