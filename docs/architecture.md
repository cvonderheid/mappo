# MAPPO Architecture

## Overview
MAPPO is a provider-tenant control plane that orchestrates release rollouts across customer subscriptions in multiple tenants.

Production intent:
- Marketplace lifecycle events register customer targets.
- Publisher management access authorizes MAPPO to mutate customer resources.

Current hosted demo:
- uses simulated Marketplace lifecycle events,
- uses Deployment Stacks,
- does not use live `Microsoft.Solutions/applications`,
- does not use Template Specs.

## Core Model
- Target: a customer-tenant subscription + workload deployment context.
- Release: a versioned deployment artifact reference and rollout metadata.
- Deployment Run: execution of one release across selected targets.

## Control Plane Components
1. API service
- Owns Targets/Releases/Runs APIs.
- Owns marketplace onboarding APIs (`/admin/onboarding`, `/admin/onboarding/events`) used by event-forwarders.
- Owns onboarding registration maintenance APIs (`PATCH/DELETE /admin/onboarding/registrations/{target_id}`) for operator CRUD fixes.
- Exposes UI-facing status/query surfaces.

2. Orchestrator service
- Expands target sets by tags/groups.
- Applies rollout strategy (all-at-once or waves).
- Enforces stop thresholds and concurrency caps.
- Applies Azure guardrails before run start:
  - run-level concurrency cap,
  - per-subscription concurrency cap,
  - quota preflight checks (ACA usage per subscription/region).

3. Per-target executor
- Executes state machine per target:
  - `QUEUED` -> `VALIDATING` -> `DEPLOYING` -> `VERIFYING` -> (`SUCCEEDED` | `FAILED`)
- Captures stage timestamps, errors, and correlation IDs.
- Normalizes Azure API failure payloads into operator-facing run logs (error code/message, HTTP status, request/correlation IDs, detail entries) so troubleshooting does not require portal navigation.
- Adapter boundary supports execution modes (`demo` and `azure`) so orchestration and persistence stay unchanged across runtimes.

4. Persistence
- Stores fleet state, run history, per-target stage records, and logs.
- Stores onboarding registry records and event-ingest history for idempotent target registration.
- Retains run/deployment history for 3 months.
- Cloud runtime path uses Azure Database for PostgreSQL Flexible Server (local dev keeps Docker Postgres).

5. Marketplace webhook forwarder (Azure Function App)
- Receives marketplace technical-config webhook calls.
- Normalizes payloads to MAPPO onboarding contract and forwards to `/api/v1/admin/onboarding/events`.
- Runs as independent ingress boundary so MAPPO API can stay token-gated and internal behind stricter controls.

## Control / Data / Verification Boundaries
- Control flow: run/wave scheduling and per-target stage transitions.
- Data flow: release parameters and target-scoped overrides.
- Verification flow: post-deploy health checks and rollout halt decisions.

## Azure Rate-Limit + Quota Guardrails
- API retries use exponential backoff + jitter for transient Azure failures (`408`, `409`, `429`, `5xx`) and honor `Retry-After` when present.
- Scheduler batches target execution by subscription to avoid write bursts against ARM.
- Optional quota preflight queries ACA usage for each target subscription/region and can reduce concurrency when quota headroom is low.
- Guardrail decisions are persisted with the deployment run (`concurrency`, `subscription_concurrency`, `guardrail_warnings`) for operator visibility.

## Deployment Direction
- App services hosted on Azure Container Apps.
- Frontend sign-in is protected with Azure EasyAuth (Microsoft Entra ID) on the frontend Container App.
- Azure APIs are accessed through the MAPPO runtime principal, including cross-tenant customer-side RBAC where needed.
- Runtime Azure credentials are resolved per subscription tenant authority (via target tenant ID and/or `MAPPO_AZURE_TENANT_BY_SUBSCRIPTION`) to support cross-tenant deployments.
- Target discovery is registration-driven (marketplace lifecycle events), not runtime subscription scanning.
- UI and API are separate deployable containers.
- Demo automation boundary:
  - Pulumi IaC provisions Postgres plus the demo-fleet target resource groups, shared ACA environments, and exports target inventory.
  - Runtime ACA deployment is scripted outside Pulumi (`./scripts/runtime_aca_deploy.sh`) into a dedicated runtime resource group to keep Pulumi destroy deterministic.
  - Runtime deploy also manages an ACA Job for Flyway migrations and runs it before app revision rollout (`./scripts/runtime_db_migrate_job_run.sh` for on-demand reruns).
  - EasyAuth app registration + frontend auth wiring is handled by script (`./scripts/runtime_easyauth_configure.sh`) for deterministic post-deploy callback URL binding.
  - CLI scripts provision/deploy the Function App webhook forwarder and replay inventory events through the webhook path.
  - Partner Center offer lifecycle is handled via API/CLI helper scripts.
  - Portal-only steps are documented in `/Users/cvonderheid/workspace/mappo/docs/marketplace-portal-playbook.md`.
  - Current topology is documented in `/Users/cvonderheid/workspace/mappo/docs/demo-azure-topology.md`.

## Production Auth Model (Marketplace)
MAPPO production auth is based on managed-application publisher authorization, not ad-hoc customer-side RBAC scripts.

1. Publisher identity
- MAPPO uses a publisher Entra application/service principal (multi-tenant) with client credentials.
- This identity is configured once in the managed application plan (publisher management access).

2. Offer authorization contract
- Managed application definition/plan authorizations specify publisher principal + role scope expectations.
- When a customer deploys the managed application, Azure grants that publisher principal access to the managed resource group for that instance.

3. Runtime execution
- MAPPO authenticates with the publisher identity and resolves tenant authority per subscription.
- MAPPO updates only targets already registered through onboarding events.
- Managed identity inside the deployed managed app is a separate concern for workload-to-resource access and is not MAPPO control-plane auth.

4. Automation vs manual boundary
- Automatable:
  - Publisher app/SP bootstrap.
  - Offer plan definition updates via API/CLI.
  - Runtime deployment and webhook handling.
  - Target registration from lifecycle/onboarding events.
- Manual/approval driven:
  - Partner Center onboarding/compliance/certification workflows.
  - Final publish and go-live confirmation steps.

## Deployment Scope
Azure execution is modeled around release source types:
- `template_spec`: legacy ARM deployment path from a Template Spec version per target.
- `bicep`: direct Bicep-based deployment source.
- `deployment_stack`: deployment stack-driven rollout source.

Behavior:
- Current implementation:
  - `deployment_stack` + `resource_group`: current live demo path; executed for real through the Azure Java SDK.
  - `template_spec` + `resource_group`: legacy execution path kept in code, but not used in the current hosted demo.
  - `template_spec` + `subscription`: not implemented yet; MAPPO falls back to simulator mode and records a guardrail warning.
  - `bicep`: not implemented yet; MAPPO falls back to simulator mode and records a guardrail warning.
- Run orchestration, waves, retries/resume, and stop policies are unchanged.
- `DeploymentRun.execution_source_type` snapshots the selected release source type for immutable history.
- Execution settings remain release-scoped (`deployment_scope`, ARM mode, what-if on canary, verify-after-deploy).
- Deployment-stack execution records stack metadata and surfaces normalized operation/error details in target logs.

## Determinism + Legibility Contract
- Stage transitions are append-only and timestamped.
- Every failed target has a structured error and correlation IDs.
- Run-level summaries derive from per-target stage truth.

## Related Design Docs
- Current Azure demo topology: `/Users/cvonderheid/workspace/mappo/docs/demo-azure-topology.md`
- Legacy template-spec rollout design: `/Users/cvonderheid/workspace/mappo/docs/template-spec-executor-design.md`
