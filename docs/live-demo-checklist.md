# MAPPO Live Demo Checklist (Marketplace-Accurate Managed App)

Use this checklist for a demo aligned to the Marketplace managed application model:
- MAPPO control plane in provider tenant,
- managed application instances deployed in customer subscriptions,
- ACA workloads running inside managed resource groups,
- MAPPO orchestrating upgrades against those ACA targets.

## 1) Target Topology

- [ ] Provider tenant exists for MAPPO control plane and publisher identity.
- [ ] At least 2 customer tenants exist (10 preferred for full demo).
- [ ] Each customer subscription has one managed application instance installed.
- [ ] Each managed application instance creates a managed resource group with a target Container App.

## 2) Provider Setup

- [ ] Create service principal + env file via script:
  - `make azure-auth-bootstrap`
- [ ] Load env file in your shell:
  - `source .data/mappo-azure.env`
- [ ] Resolve publisher principal object ID used for managed app definition authorization:
  - `export MAPPO_PUBLISHER_PRINCIPAL_OBJECT_ID="<azure-ad-object-id>"`
- [ ] Configure cross-tenant subscription-to-tenant mapping for MAPPO runtime:
  - `make azure-tenant-map SUBSCRIPTION_IDS="<sub-a>,<sub-b>"`
  - `export MAPPO_AZURE_TENANT_BY_SUBSCRIPTION='{"<sub-a>":"<tenant-a-guid>","<sub-b>":"<tenant-b-guid>"}'`
- [ ] Onboard runtime app across target tenants/subscriptions (multi-tenant app + SP + RBAC):
  - `make azure-onboard-multitenant-runtime CLIENT_ID="$MAPPO_AZURE_CLIENT_ID" SUBSCRIPTION_IDS="<sub-a>,<sub-b>"`

## 3) IaC Provisioning (Pulumi, primary path)

- [ ] Install/select stack:
  - `make iac-install`
  - `make iac-stack-init PULUMI_STACK=<stack>`
- [ ] Configure deterministic 2-target stack (prevents accidental `demo10` target profile usage):
  - `make iac-configure-marketplace-demo PULUMI_STACK=<stack> PROVIDER_SUBSCRIPTION_ID="<provider-sub>" CUSTOMER_SUBSCRIPTION_ID="<customer-sub>"`
  - (default behavior adds your current public IP to Postgres firewall rules so local backend can connect)
- [ ] Preview/apply:
  - `make iac-preview PULUMI_STACK=<stack>`
  - `cd infra/pulumi && pulumi config set --stack <stack> mappo:controlPlanePostgresEnabled true`
  - `cd infra/pulumi && pulumi config set --stack <stack> --secret mappo:controlPlanePostgresAdminPassword "<strong-password>"`
  - `make iac-up PULUMI_STACK=<stack>`
- [ ] Export target inventory from Pulumi output (used as webhook simulation input):
  - `make iac-export-targets PULUMI_STACK=<stack>`
  - `make iac-export-db-env PULUMI_STACK=<stack>`
  - `source .data/mappo-db.env`
  - `make bootstrap-releases`
- [ ] Verify managed application resource exists (`Microsoft.Solutions/applications`) and points to a managed resource group.
- [ ] Verify intended target Container App exists in each managed resource group.

## 4) Marketplace Offering Setup (API/CLI + Portal)

- [ ] Run Partner Center API/CLI steps for offer + plan lifecycle (see `/Users/cvonderheid/workspace/mappo/docs/marketplace-portal-playbook.md`).
- [ ] Use Portal playbook for actions that are still portal-only for this demo.
- [ ] Deploy Function App lifecycle forwarder (webhook receiver -> MAPPO onboarding endpoint):
  - `make marketplace-forwarder-deploy RESOURCE_GROUP="<rg>" FUNCTION_APP_NAME="<name>" SUBSCRIPTION_ID="<provider-sub>" MAPPO_API_BASE_URL="$MAPPO_API_BASE_URL" MAPPO_INGEST_TOKEN="$MAPPO_MARKETPLACE_INGEST_TOKEN"`
  - Capture printed `webhook_url` and place it in Partner Center technical configuration.
- [ ] Validate forwarder path:
  - `make marketplace-forwarder-replay-inventory FORWARDER_URL="<webhook_url>"`
  - `GET /api/v1/admin/onboarding` confirms applied events + registered targets.

## 5) MAPPO Runtime

- [ ] Run readiness check:
  - `make azure-preflight`
  - Default mode is `MAPPO_PREFLIGHT_MODE=marketplace`; set `MAPPO_PREFLIGHT_MODE=inventory` for strict inventory validation.
  - Optional: set `MAPPO_PREFLIGHT_EXPECTED_TARGET_COUNT=10` before preflight when running full 10-target scale rehearsal.
- [ ] Configure Azure guardrail env vars for demo safety (recommended defaults shown):
  - `MAPPO_AZURE_MAX_RUN_CONCURRENCY=6`
  - `MAPPO_AZURE_MAX_SUBSCRIPTION_CONCURRENCY=2`
  - `MAPPO_AZURE_MAX_RETRY_ATTEMPTS=5`
  - `MAPPO_AZURE_RETRY_BASE_DELAY_SECONDS=1.0`
  - `MAPPO_AZURE_RETRY_MAX_DELAY_SECONDS=20.0`
  - `MAPPO_AZURE_RETRY_JITTER_SECONDS=0.35`
  - `MAPPO_AZURE_ENABLE_QUOTA_PREFLIGHT=true`
  - `MAPPO_AZURE_QUOTA_WARNING_HEADROOM_RATIO=0.1`
  - `MAPPO_AZURE_QUOTA_MIN_REMAINING_WARNING=2`
- [ ] Deploy runtime to Azure Container Apps:
  - `make runtime-aca-deploy PULUMI_STACK=<stack> SUBSCRIPTION_ID="<provider-sub>"`
  - `source .data/mappo-runtime.env`
- [ ] Validate runtime endpoints:
  - Backend docs: `$MAPPO_RUNTIME_BACKEND_URL/api/v1/docs`
  - Frontend UI: `$MAPPO_RUNTIME_FRONTEND_URL`

## 6) Validation Run

- [ ] Validate event-driven onboarding path through forwarder:
  - `make marketplace-forwarder-replay-inventory FORWARDER_URL="<webhook_url>"`
  - `curl -s "$MAPPO_RUNTIME_BACKEND_URL/api/v1/admin/onboarding" | jq`
- [ ] Create a single-target canary run and verify stage progression:
  - `VALIDATING -> DEPLOYING -> VERIFYING -> SUCCEEDED`
- [ ] Verify per-target logs include structured error details and Azure correlation IDs.
- [ ] Execute 10-target rollout with stop policy enabled.
- [ ] Confirm deployment run shows guardrail warnings (if any) and effective per-subscription batching settings.
- [ ] Confirm retry/resume behavior on failed or halted runs.
- [ ] Confirm fleet view reflects latest deployed release per successful target.
- [ ] Confirm forwarder security controls:
  - Function URL uses function key (`?code=...`) and MAPPO onboarding ingest remains token-gated.
  - No assumption of marketplace-specific service-tag filtering for inbound webhook traffic.

## 7) Teardown / Reset (Repeatable Demo Cleanup)

- [ ] Destroy Azure IaC resources:
  - `make iac-destroy PULUMI_STACK=<stack>`
- [ ] Remove runtime identity artifacts (role assignments + tenant service principals):
  - `make azure-cleanup-runtime-identity CLIENT_ID="<app-id>" SUBSCRIPTION_IDS="<sub-a>,<sub-b>" [HOME_SUBSCRIPTION_ID="<sub-home>"]`
  - Optional full identity teardown (also deletes app registration): add `DELETE_APP_REGISTRATION=true`
- [ ] Remove local runtime data volumes:
  - `docker compose -f infra/docker-compose.yml down -v --remove-orphans`
- [ ] Remove ACA runtime resources:
  - `make runtime-aca-destroy [RESOURCE_GROUP="<rg>"] [SUBSCRIPTION_ID="<provider-sub>"]`
