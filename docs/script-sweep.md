# Script Sweep (2026-03-01)

This is the current script inventory across `scripts/`, with disposition:
- `keep`: still actively used and appropriate as a script.
- `delete`: dead/deprecated and removed.
- `migrate-to-pulumi`: infra lifecycle currently done imperatively and should be Pulumi-managed.
- `keep-wrapper`: script remains useful as thin orchestration around Pulumi/API.

## Summary

- Total reviewed: 34
- Keep / keep-wrapper: 29
- Delete: 3
- Migrate-to-pulumi priority: 2

## Inventory

### `scripts/`

- `scripts/azure_auth_bootstrap.sh`: `keep` (operator auth bootstrap; not Pulumi resource lifecycle).
- `scripts/azure_cleanup_runtime_identity.sh`: `keep` (identity teardown helper).
- `scripts/azure_cleanup_easyauth.sh`: `keep` (EasyAuth app-registration teardown helper).
- `scripts/azure_onboard_multitenant_runtime.sh`: `keep-wrapper` (cross-tenant onboarding steps that involve Entra/SP propagation).
- `scripts/azure_preflight.sh`: `keep` (readiness validation).
- `scripts/azure_tenant_map.sh`: `keep` (tenant mapping utility).
- `scripts/backend_file_size_check.py`: `keep` (engineering quality gate).
- `scripts/check_no_demo_leak.py`: `keep` (engineering quality gate).
- `scripts/docs_consistency_check.py`: `keep` (engineering quality gate).
- `scripts/export_backend_openapi.py`: `keep-wrapper` (Springdoc contract export used by Maven verify).
- `scripts/golden_principles_check.py`: `keep` (engineering quality gate).
- `scripts/iac_configure_marketplace_demo.sh`: `keep-wrapper` (Pulumi stack config normalization).
- `scripts/iac_export_db_env.sh`: `keep-wrapper` (exports Pulumi stack outputs into env file).
- `scripts/marketplace_forwarder_deploy.sh`: `migrate-to-pulumi` (creates/updates Function resources imperatively today).
- `scripts/marketplace_forwarder_package.sh`: `keep` (artifact packaging remains valid).
- `scripts/marketplace_forwarder_replay_inventory.sh`: `keep` (test/simulation utility).
- `scripts/marketplace_ingest_events.sh`: `keep` (test/simulation utility).
- `scripts/partner_center_api.sh`: `keep` (Partner Center API helper; out of Pulumi scope).
- `scripts/partner_center_get_token.sh`: `keep` (Partner Center token helper; out of Pulumi scope).
- `scripts/runtime_aca_deploy.sh`: `migrate-to-pulumi` (creates/updates ACA/ACR imperatively today).
- `scripts/runtime_aca_destroy.sh`: `migrate-to-pulumi` (imperative teardown for runtime RG/resources).
- `scripts/runtime_db_migrate_job_run.sh`: `keep-wrapper` (operator invocation for ACA-hosted Flyway job execution).
- `scripts/runtime_easyauth_configure.sh`: `keep-wrapper` (Entra app registration + ACA auth wiring step).
- `scripts/with_mappo_azure_env.sh`: `keep` (local DX helper).
- `scripts/workflow_discipline_check.py`: `keep` (engineering quality gate).

### Removed backend scripts

- The old `backend/scripts/` utilities belonged to the retired backend implementation and are no longer part of the supported workflow surface.
- Operator automation should live under top-level `scripts/` or as Maven lifecycle steps in the owning module.

## Pulumi Migration Priority

### Priority 1 (required for strict deploy model)

1. Runtime stack resources into Pulumi:
   - ACR
   - Log Analytics workspace
   - ACA environment
   - Backend Container App
   - Frontend Container App
2. Marketplace forwarder resources into Pulumi:
   - Storage account
   - Function App + app settings
   - (Optional) App Insights

### Priority 2 (deploy model alignment)

1. Split the deploy workflow into:
   - artifact publish (build/push images + package function zip/blob)
   - `pulumi up` to roll out image/package references
2. Decommission imperative update scripts:
   - `scripts/runtime_aca_deploy.sh`
   - `scripts/runtime_aca_destroy.sh`
   - `scripts/marketplace_forwarder_deploy.sh`

## Guardrails

- Resource naming collisions should be prevented by Pulumi-managed deterministic names, not ad-hoc script fallback logic.
- Script usage in primary workflow should be limited to:
  - auth/bootstrap
  - validation
  - artifact build/publish
  - simulation/testing helpers
