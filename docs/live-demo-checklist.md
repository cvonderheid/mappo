# MAPPO Live Demo Checklist (Marketplace-Accurate Managed App)

Current state note:
- This checklist describes the future real Marketplace validation path.
- The current hosted demo does **not** create live `Microsoft.Solutions/applications` managed application instances.
- The current hosted demo uses simulated lifecycle events plus Deployment Stacks.
- Current hosted demo topology: `/Users/cvonderheid/workspace/mappo/docs/demo-azure-topology.md`

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
  - `./scripts/azure_auth_bootstrap.sh`
- [ ] Load env file in your shell:
  - `source .data/mappo-azure.env`
- [ ] Resolve publisher principal object ID used for managed app definition authorization:
  - `export MAPPO_PUBLISHER_PRINCIPAL_OBJECT_ID="<azure-ad-object-id>"`
- [ ] Configure cross-tenant subscription-to-tenant mapping for MAPPO runtime:
  - `./scripts/azure_tenant_map.sh --subscriptions "<sub-a>,<sub-b>"`
  - `export MAPPO_AZURE_TENANT_BY_SUBSCRIPTION='{"<sub-a>":"<tenant-a-guid>","<sub-b>":"<tenant-b-guid>"}'`
- [ ] Onboard runtime app across target tenants/subscriptions (multi-tenant app + SP + RBAC):
  - `./scripts/azure_onboard_multitenant_runtime.sh --client-id "$MAPPO_AZURE_CLIENT_ID" --target-subscriptions "<sub-a>,<sub-b>"`
- [ ] Confirm managed app offer/plan is configured with publisher management authorization (tenant + principal) for production purchase flow.
- [ ] Confirm onboarding source for target registration is marketplace lifecycle events (not direct target import).

## 3) IaC Provisioning (Pulumi, primary path)

- [ ] Compile/select stack:
  - `./mvnw -pl infra/pulumi -DskipTests compile`
  - `cd infra/pulumi && pulumi login --local`
  - `cd infra/pulumi && pulumi stack select <stack> || pulumi stack init <stack>`
- [ ] Configure deterministic 2-target stack (prevents accidental `demo10` target profile usage):
  - `./scripts/iac_configure_marketplace_demo.sh --stack <stack> --provider-subscription-id "<provider-sub>" --customer-subscription-id "<customer-sub>"`
  - (default behavior adds your current public IP to Postgres firewall rules so local backend can connect)
- [ ] Preview/apply:
  - `cd infra/pulumi && pulumi preview --stack <stack>`
  - `cd infra/pulumi && pulumi config set --stack <stack> mappo:controlPlanePostgresEnabled true`
  - `cd infra/pulumi && pulumi config set --stack <stack> --secret mappo:controlPlanePostgresAdminPassword "<strong-password>"`
  - `cd infra/pulumi && pulumi up --stack <stack> --yes`
- [ ] Export DB env and register releases:
  - `./scripts/iac_export_db_env.sh --stack <stack>`
  - `source .data/mappo-db.env`
  - Register releases through the MAPPO UI or `POST /api/v1/releases`
- [ ] Optional simulation-only inventory export (for fake webhook replay):
  - `cd infra/pulumi && pulumi stack output mappoTargetInventory --stack <stack> --json > ../../.data/mappo-target-inventory.json`
- [ ] Future marketplace validation only: verify the managed application resource exists (`Microsoft.Solutions/applications`) and points to a managed resource group.
- [ ] Verify intended target Container App exists in each managed resource group.

## 4) Marketplace Offering Setup (API/CLI + Portal)

- [ ] Run Partner Center API/CLI steps for offer + plan lifecycle (see `/Users/cvonderheid/workspace/mappo/docs/marketplace-portal-playbook.md`).
- [ ] Use Portal playbook for actions that are still portal-only for this demo.
- [ ] Deploy Function App lifecycle forwarder (webhook receiver -> MAPPO onboarding endpoint):
  - `./scripts/marketplace_forwarder_deploy.sh --resource-group "<rg>" --function-app-name "<name>" --subscription-id "<provider-sub>" --mappo-api-base-url "$MAPPO_API_BASE_URL" --mappo-ingest-token "$MAPPO_MARKETPLACE_INGEST_TOKEN"`
  - Capture printed `webhook_url` and place it in Partner Center technical configuration.
- [ ] Validate forwarder path:
  - Production-like: trigger a real onboarding event from marketplace/private offer flow.
  - Simulation fallback: `./scripts/marketplace_forwarder_replay_inventory.sh --forwarder-url "<webhook_url>"`
  - `GET /api/v1/admin/onboarding` confirms applied events + registered targets.

## 5) MAPPO Runtime

- [ ] Run readiness check:
  - `./scripts/azure_preflight.sh`
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
  - `./scripts/runtime_aca_deploy.sh --stack <stack> --subscription-id "<provider-sub>"`
  - `./scripts/runtime_db_migrate_job_run.sh --stack <stack> --subscription-id "<provider-sub>"` (optional rerun)
  - `./scripts/runtime_easyauth_configure.sh --stack <stack> --subscription-id "<provider-sub>"`
  - `source .data/mappo-runtime.env`
  - `source .data/mappo-easyauth.env`
- [ ] Validate runtime endpoints:
  - Backend docs: `$MAPPO_RUNTIME_BACKEND_URL/api/v1/docs`
  - Frontend UI: `$MAPPO_RUNTIME_FRONTEND_URL`
  - Frontend sign-in redirects through Microsoft Entra (EasyAuth).

## 6) Validation Run

- [ ] Validate event-driven onboarding path through forwarder:
  - Production-like: submit/renew marketplace subscription to trigger lifecycle event.
  - Simulation fallback: `./scripts/marketplace_forwarder_replay_inventory.sh --forwarder-url "<webhook_url>"`
  - `curl -s "$MAPPO_RUNTIME_BACKEND_URL/api/v1/admin/onboarding" | jq`
- [ ] Create a single-target canary run and verify stage progression:
  - `VALIDATING -> DEPLOYING -> VERIFYING -> SUCCEEDED`
- [ ] Validate current deployment scope expectation:
  - Container App patch path updates workload revision/config.
  - Full template-managed-resource upgrades (for example Container Jobs + additional infra) require template-spec deployment mode.
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
  - `cd infra/pulumi && pulumi destroy --stack <stack> --yes`
- [ ] Remove runtime identity artifacts (role assignments + tenant service principals):
  - `./scripts/azure_cleanup_runtime_identity.sh --client-id "<app-id>" --target-subscriptions "<sub-a>,<sub-b>" [--home-subscription-id "<sub-home>"] --yes`
  - Optional full identity teardown (also deletes app registration): add `--delete-app-registration true`
- [ ] Remove EasyAuth app registration artifacts:
  - `./scripts/azure_cleanup_easyauth.sh [--client-id "<easy-auth-app-id>"] --yes`
- [ ] Remove local runtime data volumes:
  - `docker compose -f infra/docker-compose.yml down -v --remove-orphans`
- [ ] Remove local MAPPO env/artifact files:
  - `./scripts/clean_slate_local.sh`
- [ ] Remove ACA runtime resources:
  - `./scripts/runtime_aca_destroy.sh [--resource-group "<rg>"] [--subscription-id "<provider-sub>"]`
