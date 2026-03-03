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
- Azure auth bootstrap (`make azure-auth-bootstrap`)
- Optional simulation inventory export (`make iac-export-targets`)
- MAPPO runtime deploy to ACA (`make runtime-aca-deploy`, `make runtime-db-migrate-job-run`, `make runtime-easyauth-configure`, `make runtime-aca-destroy`)
- Function App forwarder package/deploy/replay (`make marketplace-forwarder-package`, `make marketplace-forwarder-deploy`, `make marketplace-forwarder-replay-inventory`)
- Partner Center token acquisition (`make partner-center-token`)
- Partner Center API invocation wrapper (`make partner-center-api URL=...`)
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
- Deploy:
  - `make iac-install`
  - `make iac-stack-init PULUMI_STACK=<stack>`
  - `make iac-up PULUMI_STACK=<stack>`
- Export target snapshot + bootstrap releases:
  - `make iac-export-db-env PULUMI_STACK=<stack>`
  - `source .data/mappo-db.env`
  - `make bootstrap-releases`
  - Optional simulation-only: `make iac-export-targets PULUMI_STACK=<stack>`

## 2) Deploy MAPPO runtime to ACA
- `make azure-preflight`
- `make runtime-aca-deploy PULUMI_STACK=<stack> SUBSCRIPTION_ID="<provider-sub>"`
- `make runtime-db-migrate-job-run PULUMI_STACK=<stack> SUBSCRIPTION_ID="<provider-sub>"` (optional rerun)
- `make runtime-easyauth-configure PULUMI_STACK=<stack> SUBSCRIPTION_ID="<provider-sub>"`
- `source .data/mappo-runtime.env`
- `source .data/mappo-easyauth.env`

## 2b) Deploy webhook forwarder Function App
- `make marketplace-forwarder-deploy RESOURCE_GROUP="<rg>" FUNCTION_APP_NAME="<name>" SUBSCRIPTION_ID="<provider-sub>" MAPPO_API_BASE_URL="$MAPPO_API_BASE_URL" MAPPO_INGEST_TOKEN="$MAPPO_MARKETPLACE_INGEST_TOKEN"`
- Capture printed `webhook_url` and use it in Partner Center technical config.
- Validate path:
  - Production-like: trigger real marketplace lifecycle event.
  - Simulation fallback: `make marketplace-forwarder-replay-inventory FORWARDER_URL="<webhook_url>"`

## 3) Partner Center API path (non-IaC)
- Acquire token:
  - `make partner-center-token`
- Call API (endpoint from current Microsoft Partner Center docs):
  - `make partner-center-api URL="<partner-center-api-url>" [METHOD=GET] [BODY_FILE=payload.json]`

## 4) Portal-only completion steps
- Complete remaining Partner Center listing/certification/publish tasks in portal.
- Record the portal action and resulting artifact IDs in demo notes.
- Re-run `make azure-preflight` and a MAPPO canary rollout before presenting.

## Security Boundary Note
- Prefer token/auth based controls for inbound webhooks (Function key + MAPPO ingest token + payload validation).
- Do not assume a dedicated marketplace webhook service tag exists for Function inbound restrictions.
