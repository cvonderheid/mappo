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
- Pulumi inventory export + webhook simulation (`make iac-export-targets`, `make marketplace-ingest-events`)
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
- Export/import targets:
  - `make iac-export-targets PULUMI_STACK=<stack>`
  - `make marketplace-ingest-events`
  - `make bootstrap-releases`

## 2) Start MAPPO runtime
- `make azure-preflight`
- `make dev-backend-azure`
- `make dev-frontend`

## 3) Partner Center API path (non-IaC)
- Acquire token:
  - `make partner-center-token`
- Call API (endpoint from current Microsoft Partner Center docs):
  - `make partner-center-api URL="<partner-center-api-url>" [METHOD=GET] [BODY_FILE=payload.json]`

## 4) Portal-only completion steps
- Complete remaining Partner Center listing/certification/publish tasks in portal.
- Record the portal action and resulting artifact IDs in demo notes.
- Re-run `make azure-preflight` and a MAPPO canary rollout before presenting.
