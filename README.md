# MAPPO

MAPPO is a multi-tenant deployment control plane for Azure Managed Apps (CodeDeploy-style rollout management across customer subscriptions/tenants).

## Marketplace-Accurate Demo Quick Start
Single-command workflow (after your IaC stack + env files are ready):
```bash
make install
make deploy PULUMI_STACK=<stack> [SUBSCRIPTION_ID=<provider-sub-id>]
source .data/mappo-runtime.env
```

`make deploy` orchestrates:
- runtime ACA deploy (build + push backend/frontend images)
- runtime migration Container Apps Job deploy + execution (Flyway in-cluster)
- runtime frontend EasyAuth configure (Entra app registration + ACA auth)
- Function App package build
- Function App deploy (marketplace forwarder)

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
make iac-export-db-env PULUMI_STACK=<stack>
make bootstrap-releases
# Use FORCE=1 to replace existing releases with current defaults.
make bootstrap-releases FORCE=1
```
   - The stack configurator auto-resolves tenant-local principal object IDs and adds your current public IP to Postgres firewall rules for local demo connectivity (can be overridden).
   - Do not run `make import-targets` for marketplace-realistic demos.
   - Optional simulation-only path: `make iac-export-targets` exports source data you can replay as fake webhook events before a real offer is wired.

   - Load managed DB env output when running backend locally:
```bash
source .data/mappo-db.env
```
5. Run readiness check:
```bash
make azure-preflight
```
   - Default mode is `MAPPO_PREFLIGHT_MODE=marketplace` (inventory optional, webhook registration model).
   - Use `MAPPO_PREFLIGHT_MODE=inventory` for strict inventory validation workflows.
   - `MAPPO_PREFLIGHT_EXPECTED_TARGET_COUNT` defaults to `2` when inventory is present.
6. Deploy MAPPO runtime (backend + frontend) to ACA:
```bash
make runtime-aca-deploy PULUMI_STACK=<stack> SUBSCRIPTION_ID="<provider-sub-id>"
make runtime-easyauth-configure PULUMI_STACK=<stack> SUBSCRIPTION_ID="<provider-sub-id>"
source .data/mappo-runtime.env
```
   - Runtime deploy builds/pushes backend + frontend images to ACR, creates/updates migration job `job-mappo-db-<stack>`, executes Flyway migrations in ACA, and writes runtime URLs into `.data/mappo-runtime.env`.
   - Re-run migrations on demand without redeploying apps:
```bash
make runtime-db-migrate-job-run PULUMI_STACK=<stack> SUBSCRIPTION_ID="<provider-sub-id>"
```
   - EasyAuth configure creates/updates an Entra app registration and enables frontend sign-in redirect via Container App auth.
7. Deploy the Function App lifecycle forwarder (marketplace webhook path):
```bash
make marketplace-forwarder-deploy \
  RESOURCE_GROUP="rg-mappo-marketplace-forwarder" \
  FUNCTION_APP_NAME="fa-mappo-marketplace-forwarder-<suffix>" \
  LOCATION="eastus" \
  SUBSCRIPTION_ID="<provider-sub-id>" \
  MAPPO_API_BASE_URL="$MAPPO_API_BASE_URL" \
  MAPPO_INGEST_TOKEN="$MAPPO_MARKETPLACE_INGEST_TOKEN"
```
   - The deploy output prints `webhook_url` for Partner Center technical configuration.
8. Register targets through onboarding events (forwarder path):
```bash
make marketplace-forwarder-replay-inventory FORWARDER_URL="<webhook_url>"
```
   - This is simulation-only. In production flow, registrations come from actual marketplace lifecycle events.
9. Open:
- API docs: `$MAPPO_RUNTIME_BACKEND_URL/api/v1/docs`
- UI: `$MAPPO_RUNTIME_FRONTEND_URL`

## Primary Demo Commands
- `make iac-install`
- `make iac-stack-init [PULUMI_STACK=<name>]`
- `make iac-configure-marketplace-demo PROVIDER_SUBSCRIPTION_ID="<sub1>" CUSTOMER_SUBSCRIPTION_ID="<sub2>" [PULUMI_STACK=<name>]`
- `make iac-preview [PULUMI_STACK=<name>]`
- `make iac-up [PULUMI_STACK=<name>]`
- `make iac-export-db-env [PULUMI_STACK=<name>]`
- `make iac-destroy [PULUMI_STACK=<name>]`
- `make runtime-aca-deploy [PULUMI_STACK=<name>] [SUBSCRIPTION_ID=<provider-sub>]`
- `make runtime-db-migrate-job-run [PULUMI_STACK=<name>] [SUBSCRIPTION_ID=<provider-sub>]`
- `make runtime-easyauth-configure [PULUMI_STACK=<name>] [SUBSCRIPTION_ID=<provider-sub>] [EASYAUTH_SIGN_IN_AUDIENCE=AzureADMyOrg|AzureADMultipleOrgs]`
- `make deploy [PULUMI_STACK=<name>] [SUBSCRIPTION_ID=<provider-sub>] [FUNCTION_APP_NAME=<name>] [FORWARDER_RESOURCE_GROUP=<rg>] [FORWARDER_LOCATION=<region>]`
- `make runtime-aca-destroy [RESOURCE_GROUP=<rg>] [SUBSCRIPTION_ID=<provider-sub>]`
- `make clean-slate-local`
- `make azure-tenant-map SUBSCRIPTION_IDS="<sub1>,<sub2>"`
- `make azure-cleanup-runtime-identity CLIENT_ID="<app-id>" SUBSCRIPTION_IDS="<sub1>,<sub2>" [DELETE_APP_REGISTRATION=true]`
- `make azure-cleanup-easyauth [CLIENT_ID="<easy-auth-app-id>"]`
- `make marketplace-ingest-events [INVENTORY_FILE=.data/mappo-target-inventory.json] [API_BASE_URL=http://localhost:8010]` (simulation-only)
- `make marketplace-forwarder-package [OUTPUT_ZIP=.data/marketplace-forwarder-function.zip]`
- `make marketplace-forwarder-deploy RESOURCE_GROUP="<rg>" FUNCTION_APP_NAME="<name>" [MAPPO_API_BASE_URL=<url>] [MAPPO_INGEST_ENDPOINT=<url>]`
- `make marketplace-forwarder-replay-inventory FORWARDER_URL="<https://.../api/marketplace/events?code=...>" [INVENTORY_FILE=.data/mappo-target-inventory.json]` (simulation-only)
- `make bootstrap-releases`
- `make dev-backend-azure`
- `make dev-frontend`

Legacy fallback:
- `make import-targets` (direct DB import, not production-like marketplace onboarding)
- `make dev-backend-azure` / `make dev-frontend` (local runtime fallback)

## Marketplace Onboarding API
- `GET /api/v1/admin/onboarding`: returns registration snapshot + recent onboarding events.
- `POST /api/v1/admin/onboarding/events`: registers/updates targets from marketplace lifecycle events (idempotent on `event_id`).
- `POST /api/v1/admin/onboarding/forwarder-logs`: records forwarder delivery/normalization failures for operator visibility (idempotent on `log_id`).
- `GET /api/v1/admin/onboarding/forwarder-logs`: returns recent forwarder logs.
- `PATCH /api/v1/admin/onboarding/registrations/{target_id}`: updates editable registration metadata (display name, customer name, tags, managed app references).
- `DELETE /api/v1/admin/onboarding/registrations/{target_id}`: removes a registered target from onboarding/fleet state.
- Admin UI `/admin`: includes `Registered Targets`, `Recent Onboarding Events`, and `Forwarder Logs` tabs.
- Optional token gate: set `MAPPO_MARKETPLACE_INGEST_TOKEN`, then send `x-mappo-ingest-token` header.

## EasyAuth Notes
- MAPPO runtime uses Azure EasyAuth on the frontend Container App.
- App registration lifecycle is currently script-managed (`make runtime-easyauth-configure`) so callback URLs can be bound after runtime URL allocation.
- Pulumi-only management of Entra app registrations can be added later via the AzureAD provider if you want full IaC ownership.

## Quality Commands
- `make workflow-discipline-check`
- `make docs-consistency-check`
- `make golden-principles-check`
- `make check-no-demo-leak`
- `make lint-backend-file-size`
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
- Runtime ACA runbook: `/Users/cvonderheid/workspace/mappo/docs/runtime-aca-runbook.md`
- Portal playbook (manual-only steps): `/Users/cvonderheid/workspace/mappo/docs/marketplace-portal-playbook.md`
- Function forwarder runbook: `/Users/cvonderheid/workspace/mappo/docs/marketplace-forwarder-runbook.md`
- Script sweep / migration map: `/Users/cvonderheid/workspace/mappo/docs/script-sweep.md`
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
