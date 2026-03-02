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
- MAPPO runtime deploy to ACA (`make runtime-aca-deploy`, `make runtime-easyauth-configure`, `make runtime-aca-destroy`)
- Function App forwarder package/deploy/replay (`make marketplace-forwarder-package`, `make marketplace-forwarder-deploy`, `make marketplace-forwarder-replay-inventory`)
- Partner Center token acquisition (`make partner-center-token`)
- Partner Center API invocation wrapper (`make partner-center-api URL=...`)
- MAPPO onboarding ingest (`POST /api/v1/admin/onboarding/events`)

### Portal-only (manual) today
- Partner Center UI setup tasks that are not reliably scriptable for this demo:
- Tenant/account onboarding checks and legal/compliance acceptance
- Offer listing text/media and certification workflow screens
- Final publish and go-live confirmation screens

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
