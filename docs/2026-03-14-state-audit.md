# MAPPO State Audit (2026-03-14)

## Scope
- Validate identity/account usage for Azure + Azure DevOps.
- Confirm how projects/targets/releases are actually registered today.
- Identify hardcoded/demo coupling that conflicts with production-like operation.

## Update (2026-03-14, post-audit cleanup)
- Executed ADO identity cleanup using:
  - `/Users/cvonderheid/workspace/mappo/scripts/ado_cleanup_service_connection_identity.sh`
- Cleanup outcome:
  - Deleted ADO service connection `mappo-ado-demo-rg-contributor` (`de2c3812-602d-40da-abc7-a3f46dc4b65b`)
  - Deleted tenant-local service principal for app/client `35bd7871-e6eb-406e-8909-34711fb3f8dc`
  - Deleted app registration `35bd7871-e6eb-406e-8909-34711fb3f8dc`
  - No matching ADO service-hook subscriptions found for the default MAPPO ADO webhook URL filter

## Ground Truth

### 1) Identity and account context
- Azure CLI default account is a personal Microsoft account, with access to:
  - subscription `c0d51042-7d0a-41f7-b270-151e4c4ea263` (tenant `abe468b2-18bb-4dd2-90b9-5b8982337eb7`)
  - subscription `1adaaa48-139a-477b-a8c8-0e6289d6d199` (tenant `5476530d-fba1-4cd5-b2c0-fa118c5ff36e`)
  - subscription `597f46c7-2ce0-440e-962d-453e486f159d` (tenant `5476530d-fba1-4cd5-b2c0-fa118c5ff36e`)
- ADO org/project:
  - org: `https://dev.azure.com/pg123`
  - project: `demo-app-service`
  - user identity in ADO: `cvonde1@yahoo.com` (MSA / Windows Live ID origin)
- ADO service connection in project:
  - name: `mappo-ado-demo-rg-contributor`
  - type: Azure RM (Service Principal auth)
  - app/client id: `35bd7871-e6eb-406e-8909-34711fb3f8dc`
  - home tenant: `5476530d-fba1-4cd5-b2c0-fa118c5ff36e`
  - creation mode: manual app registration
- RBAC for that SP is present:
  - Contributor on `/subscriptions/1adaaa48.../resourceGroups/rg-mappo-appservice-target-ado-target-01`
  - Contributor on `/subscriptions/597f46c7.../resourceGroups/rg-mappo-appservice-target-ado-target-02`

### 2) Live DB state
- Projects present:
  - `azure-managed-app-deployment-stack`
  - `azure-managed-app-template-spec`
  - `azure-appservice-ado-pipeline`
- Target counts by project:
  - deployment-stack: 2
  - ado-pipeline: 2
  - template-spec: 0
- Current target registration sources:
  - managed-app targets: `demo-fleet-up`
  - ado targets: `appservice-fleet-up`
- ADO project currently has blank runtime pipeline config in DB:
  - `organization=""`, `project=""`, `pipelineId=""`, `azureServiceConnectionName=""`
- Releases currently exist for managed-app projects only (stack + template-spec history), not for ADO.
- Flyway history in live DB is up to `V13`; `V14` is not applied in this database yet.

### 3) Resource groups currently in use
- Subscription `c0d...`:
  - runtime/control-plane: `rg-mappo-runtime-demo`, `rg-mappo-control-plane-c0d51042`
  - forwarder: `rg-mappo-marketplace-forwarder-demo`
  - DNS: `rg-mappo-dns-demo`
  - managed-app demo target: `rg-mappo-demo-target-demo-target-01`
- Subscription `1ada...`:
  - managed-app demo target: `rg-mappo-demo-target-demo-target-02`
  - appservice demo target: `rg-mappo-appservice-target-ado-target-01`
  - legacy demo fleet env: `rg-mappo-demo-fleet-demo-fleet-1adaaa48`
- Subscription `597f...`:
  - appservice demo target: `rg-mappo-appservice-target-ado-target-02`

## Findings

### F1 (High): ADO project is not fully configured inside MAPPO runtime
- The ADO project exists, but `deployment_driver_config` fields required to trigger pipelines are blank in DB.
- Effect: ADO run execution will fail configuration validation even though ADO infra exists.
- Evidence:
  - query result for `projects.id='azure-appservice-ado-pipeline'` (organization/project/pipelineId/service connection blank)
  - driver validation in [AzureDevOpsPipelineTriggerExecutor.java](/Users/cvonderheid/workspace/mappo/backend/src/main/java/com/mappo/controlplane/infrastructure/pipeline/ado/AzureDevOpsPipelineTriggerExecutor.java)

### F2 (High): Project/target onboarding is still script-driven, not UI-driven
- The source of truth is populated through scripts (`appservice_fleet_up.sh` -> `marketplace_ingest_events.sh`), not an in-app project configuration workflow.
- Effect: operator mental model is fragmented; setup is not self-describing in UI.
- Evidence:
  - [appservice_fleet_up.sh](/Users/cvonderheid/workspace/mappo/scripts/appservice_fleet_up.sh)
  - [MarketplaceIngestEventsCommand.java](/Users/cvonderheid/workspace/mappo/tooling/src/main/java/com/mappo/tooling/MarketplaceIngestEventsCommand.java)
  - spec is only proposed, not implemented: [project-configuration-screen-spec.md](/Users/cvonderheid/workspace/mappo/docs/project-configuration-screen-spec.md)

### F3 (Medium): Built-in project seeding and demo defaults are still opinionated
- Project IDs are seeded as built-ins, and appservice fleet configure script still seeds fixed target IDs/customer labels.
- Effect: still feels “demo-shaped” and less tenant/project onboarding-native.
- Evidence:
  - [B11__current_schema.sql](/Users/cvonderheid/workspace/mappo/backend/src/main/resources/db/migration/B11__current_schema.sql)
  - [BuiltinProjects.java](/Users/cvonderheid/workspace/mappo/backend/src/main/java/com/mappo/controlplane/domain/project/BuiltinProjects.java)
  - [appservice_fleet_configure.sh](/Users/cvonderheid/workspace/mappo/scripts/appservice_fleet_configure.sh)

### F4 (Medium): Template Spec legacy data is still present in DB history
- Template Spec project and historical releases/runs remain in DB, despite current direction favoring deployment stack and ADO drivers.
- Effect: operational view includes historical patterns you may consider deprecated.
- Evidence:
  - `projects` includes `azure-managed-app-template-spec`
  - `releases` includes template-spec rows
  - `runs` includes template-spec rows

### F5 (Medium): ADO "Manage app registration" portal link confusion is expected with MSA identity
- The ADO user is MSA (`cvonde1@yahoo.com` / live.com origin). The Entra “Manage app registration” page requires an identity present in the app’s tenant.
- Effect: clicking that link can show interaction-required errors if account context is not tenant-member.
- Evidence:
  - ADO user entitlement result shows origin `msa` (Windows Live ID)
  - service connection app tenant is `5476530d-fba1-4cd5-b2c0-fa118c5ff36e`

## What is *not* true (clarifications)
- Product 2 is not using a separate/custom schema. It uses the same MAPPO schema with:
  - `targets.project_id = 'azure-appservice-ado-pipeline'`
  - target-specific pipeline inputs in `target_execution_config_entries`
- Codex did not require a hidden Entra account for the service connection. The SP object currently tied to service connection is visible and queryable.
- The migration concern is specific to versioning semantics:
  - changing old migration files does not alter already-migrated DBs
  - new behavior for existing DBs must come from a new migration version

## Recommended next actions (audit-first order)
0. Run the dedicated ADO identity cleanup dry-run and execute path:
   - script: `/Users/cvonderheid/workspace/mappo/scripts/ado_cleanup_service_connection_identity.sh`
   - runbook: `/Users/cvonderheid/workspace/mappo/docs/ado-identity-cleanup-runbook.md`
1. Apply pending migrations to runtime DB (`V14+`) and verify `flyway_schema_history`.
2. Explicitly configure the ADO project in MAPPO (organization/project/pipelineId/service connection) via API/script.
3. Trigger one end-to-end ADO release webhook and verify:
   - release row created under project `azure-appservice-ado-pipeline`
   - run launched
   - external execution handle persisted.
4. Remove or archive template-spec legacy records from active UI views (or keep only in audit/history filters).
5. Implement the Project Configuration page so setup is no longer script-only.

## Suggested cleanup candidates
- `rg-mappo-demo-fleet-demo-fleet-1adaaa48` appears legacy relative to current dual-project target model.
- Keep current active RGs for:
  - control-plane/runtime/forwarder/dns in `c0d...`
  - deployment-stack target RGs in `c0d...` and `1ada...`
  - appservice target RGs in `1ada...` and `597f...`
