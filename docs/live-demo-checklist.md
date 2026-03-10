# MAPPO Live Demo Checklist

This checklist describes the current hosted demo path:

- control-plane Postgres and Front Door from `infra/pulumi`
- target fleet from `infra/demo-fleet`
- runtime and forwarder deployed by scripts
- simulated marketplace lifecycle events
- Deployment Stack rollouts

Current topology:
- [`/Users/cvonderheid/workspace/mappo/docs/demo-azure-topology.md`](/Users/cvonderheid/workspace/mappo/docs/demo-azure-topology.md)

## 1) Control-plane IaC

- [ ] Compile and select the control-plane stack:
  - `./mvnw -pl infra/pulumi -DskipTests compile`
  - `cd infra/pulumi && pulumi login --local`
  - `cd infra/pulumi && pulumi stack select <stack> || pulumi stack init <stack>`
- [ ] Configure control-plane DB and optional Front Door:
  - `pulumi config set --stack <stack> mappo:controlPlanePostgresEnabled true`
  - `pulumi config set --stack <stack> --secret mappo:controlPlanePostgresAdminPassword "<strong-password>"`
  - Optional Front Door / custom domain:
    - `pulumi config set --stack <stack> mappo:frontDoorEnabled true`
    - `pulumi config set --stack <stack> mappo:frontDoorOriginHost "<aca-backend-host>"`
    - `pulumi config set --stack <stack> mappo:frontDoorCustomDomainHostName "api.<domain>"`
- [ ] Apply:
  - `cd infra/pulumi && pulumi up --stack <stack> --yes`
- [ ] Export DB env:
  - `./scripts/iac_export_db_env.sh --stack <stack>`
  - `source .data/mappo-db.env`

## 2) Runtime + Forwarder

- [ ] Bootstrap runtime identity:
  - `./scripts/azure_auth_bootstrap.sh`
  - `source .data/mappo-azure.env`
- [ ] Run readiness check:
  - `./scripts/azure_preflight.sh`
- [ ] Deploy runtime:
  - `./scripts/runtime_aca_deploy.sh --stack <stack> --subscription-id "<provider-sub>"`
  - `./scripts/runtime_db_migrate_job_run.sh --stack <stack> --subscription-id "<provider-sub>"` (optional rerun)
  - `./scripts/runtime_easyauth_configure.sh --stack <stack> --subscription-id "<provider-sub>"`
  - `source .data/mappo-runtime.env`
  - `source .data/mappo-easyauth.env`
- [ ] Deploy the marketplace forwarder:
  - `./scripts/marketplace_forwarder_deploy.sh --resource-group "<rg>" --function-app-name "<name>" --subscription-id "<provider-sub>" --mappo-api-base-url "$MAPPO_API_BASE_URL" --mappo-ingest-token "$MAPPO_MARKETPLACE_INGEST_TOKEN"`

## 3) Demo Fleet

- [ ] Compile/select the demo-fleet stack:
  - `./mvnw -pl infra/demo-fleet -DskipTests compile`
  - `cd infra/demo-fleet && pulumi login --local`
  - `cd infra/demo-fleet && pulumi stack select <stack> || pulumi stack init <stack>`
- [ ] Apply and emit simulated onboarding events:
  - `./scripts/demo_fleet_up.sh --stack <stack> --api-base-url "$MAPPO_API_BASE_URL"`
- [ ] Optional strict inventory export for local checks:
  - `cd infra/demo-fleet && pulumi stack output --stack <stack> mappoTargetInventory --json > ../../.data/mappo-target-inventory.json`

## 4) Release Publication

- [ ] In `/Users/cvonderheid/workspace/mappo-managed-app`, create a new release:
  - `./scripts/create_release.mjs`
- [ ] Publish the versioned ARM template to Blob and the workload image to ACR:
  - `./scripts/publish_release.mjs --version "<version>" --storage-account "<storage-account>" --storage-resource-group "<storage-rg>" --storage-container releases --acr-name "<acr-name>" [--auth-mode key]`
- [ ] Commit and push:
  - `git add . && git commit -m "Publish managed app release <version>" && git push`
- [ ] Verify MAPPO ingests the release through:
  - GitHub webhook logs in Admin
  - Releases list / Fleet banner

## 5) Validation Run

- [ ] Validate UI endpoints:
  - Backend docs: `$MAPPO_RUNTIME_BACKEND_URL/docs`
  - Frontend UI: `$MAPPO_RUNTIME_FRONTEND_URL`
- [ ] Create a canary or full run and verify:
  - `VALIDATING -> DEPLOYING -> VERIFYING -> SUCCEEDED`
- [ ] Verify:
  - Fleet version freshness / runtime / last deployment columns
  - preview risk panel
  - per-target logs and Azure IDs
  - retry/resume behavior
  - release webhook deliveries in Admin

## 6) Reset / Teardown

- [ ] Destroy demo fleet:
  - `./scripts/demo_fleet_down.sh --stack <stack> --api-base-url "$MAPPO_API_BASE_URL"`
- [ ] Destroy control-plane IaC:
  - `cd infra/pulumi && pulumi destroy --stack <stack> --yes`
- [ ] Remove runtime identity artifacts if needed:
  - `./scripts/azure_cleanup_runtime_identity.sh --client-id "<app-id>" --target-subscriptions "<sub-a>,<sub-b>" --yes`
- [ ] Remove EasyAuth app registration if needed:
  - `./scripts/azure_cleanup_easyauth.sh [--client-id "<easy-auth-app-id>"] --yes`
