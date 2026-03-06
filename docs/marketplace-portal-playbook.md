# MAPPO Marketplace Playbook

This playbook defines what is automated versus manual for a marketplace-accurate MAPPO demo.

## Automation Boundary

### Automated with Pulumi IaC
- Managed app definitions (`Microsoft.Solutions/applicationDefinitions`)
- Managed app instances (`Microsoft.Solutions/applications`)
- Managed resource groups and target ACA workload shape (via managed app template)
- Shared ACA environments used by target apps
- Target inventory export (`mappoTargetInventory`) as source input for webhook simulation

### Automated with CLI/API scripts
- Azure auth bootstrap (`./scripts/azure_auth_bootstrap.sh`)
- Optional simulation inventory export (`pulumi stack output mappoTargetInventory --json`)
- MAPPO runtime deploy to ACA (`./scripts/runtime_aca_deploy.sh`, `./scripts/runtime_db_migrate_job_run.sh`, `./scripts/runtime_easyauth_configure.sh`, `./scripts/runtime_aca_destroy.sh`)
- Function App forwarder package/deploy/replay (`./scripts/marketplace_forwarder_package.sh`, `./scripts/marketplace_forwarder_deploy.sh`, `./scripts/marketplace_forwarder_replay_inventory.sh`)
- Partner Center token acquisition (`./scripts/partner_center_get_token.sh`)
- Partner Center API invocation wrapper (`./scripts/partner_center_api.sh --url ...`)
- MAPPO onboarding ingest (`POST /api/v1/admin/onboarding/events`)

### Automated in production flow after customer deploy
- Managed app lifecycle events flow to forwarder -> MAPPO onboarding endpoint.
- MAPPO registers/updates targets from those events.
- MAPPO uses publisher identity authorization already attached to each managed resource group by the managed app plan.
- No per-customer manual target import is required.

### Portal-only (manual) today
- Partner Center UI setup tasks that are not reliably scriptable for this demo:
- Tenant/account onboarding checks and legal/compliance acceptance
- Offer listing text/media and certification workflow screens
- Final publish and go-live confirmation screens

## Permission Model (Real World)
1. One-time publisher setup
- Create publisher Entra app/service principal (multi-tenant).
- Configure managed app plan publisher-management authorization (tenant + principal).

2. Per-customer purchase/deploy
- Customer deploys managed application from marketplace/private offer.
- Azure creates managed app instance + managed resource group in customer subscription.
- Azure applies publisher authorization for that instance scope.

3. MAPPO run-time access
- MAPPO authenticates as publisher service principal.
- MAPPO resolves tenant authority per target subscription.
- MAPPO performs deployment operations only against registered targets.

4. Identity boundary
- Publisher service principal: control-plane cross-tenant operations.
- Managed identity inside customer workload: app runtime access to data-plane dependencies.
- These identities solve different problems and are configured independently.

## Deployment Scope Note
Current Azure executor behavior:
- Updates target Container App resources directly.

Not covered by current executor:
- Full managed app template redeploy (for Container Jobs or additional infra resources).

Roadmap-aligned mode:
- Add template-spec deployment path in DEPLOYING stage to apply entire release template per target managed resource group.

## End-to-End Demo Runbook

## 1) Provision target surface with Pulumi
- Export publisher principal object ID:
  - `export MAPPO_PUBLISHER_PRINCIPAL_OBJECT_ID="<azure-ad-object-id>"`
- Compile and select stack:
  - `./mvnw -pl infra/pulumi -DskipTests compile`
  - `cd infra/pulumi && pulumi login --local`
  - `cd infra/pulumi && pulumi stack select <stack> || pulumi stack init <stack>`
- Deploy:
  - `cd infra/pulumi && pulumi up --stack <stack> --yes`
- Export target snapshot + register releases:
  - `./scripts/iac_export_db_env.sh --stack <stack>`
  - `source .data/mappo-db.env`
  - Register releases through the MAPPO UI or `POST /api/v1/releases`
  - Optional simulation-only: `cd infra/pulumi && pulumi stack output mappoTargetInventory --stack <stack> --json > ../../.data/mappo-target-inventory.json`

## 2) Deploy MAPPO runtime to ACA
- `./scripts/azure_preflight.sh`
- `./scripts/runtime_aca_deploy.sh --stack <stack> --subscription-id "<provider-sub>"`
- `./scripts/runtime_db_migrate_job_run.sh --stack <stack> --subscription-id "<provider-sub>"` (optional rerun)
- `./scripts/runtime_easyauth_configure.sh --stack <stack> --subscription-id "<provider-sub>"`
- `source .data/mappo-runtime.env`
- `source .data/mappo-easyauth.env`

## 2b) Deploy webhook forwarder Function App
- `./scripts/marketplace_forwarder_deploy.sh --resource-group "<rg>" --function-app-name "<name>" --subscription-id "<provider-sub>" --mappo-api-base-url "$MAPPO_API_BASE_URL" --mappo-ingest-token "$MAPPO_MARKETPLACE_INGEST_TOKEN"`
- Capture printed `webhook_url` and use it in Partner Center technical config.
- Validate path:
  - Production-like: trigger real marketplace lifecycle event.
  - Simulation fallback: `./scripts/marketplace_forwarder_replay_inventory.sh --forwarder-url "<webhook_url>"`

## 3) Partner Center API path (non-IaC)
- Acquire token:
  - `./scripts/partner_center_get_token.sh --env-file .data/mappo-partnercenter.env`
- Call API (endpoint from current Microsoft Partner Center docs):
  - `./scripts/partner_center_api.sh --url "<partner-center-api-url>" [--method GET] [--body-file payload.json]`

## 4) Portal-only completion steps
- Complete remaining Partner Center listing/certification/publish tasks in portal.
- Record the portal action and resulting artifact IDs in demo notes.
- Re-run `./scripts/azure_preflight.sh` and a MAPPO canary rollout before presenting.

## Security Boundary Note
- Prefer token/auth based controls for inbound webhooks (Function key + MAPPO ingest token + payload validation).
- Do not assume a dedicated marketplace webhook service tag exists for Function inbound restrictions.
