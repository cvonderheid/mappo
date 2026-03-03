# MAPPO Architecture

## Overview
MAPPO is a provider-tenant control plane that orchestrates release rollouts across customer subscriptions in multiple tenants using Azure Managed Application onboarding.

## Core Model
- Target: a customer-tenant subscription + managed app instance.
- Release: a Template Spec version and deployment metadata.
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
- Azure APIs accessed through provider identity authorization on managed resource groups created by managed application instances.
- Runtime Azure credentials are resolved per subscription tenant authority (via target tenant ID and/or `MAPPO_AZURE_TENANT_BY_SUBSCRIPTION`) to support cross-tenant deployments.
- Target discovery is registration-driven (marketplace lifecycle events), not runtime subscription scanning.
- UI and API are separate deployable containers.
- Demo automation boundary:
  - Pulumi IaC provisions managed app definitions, managed app instances, shared ACA environments, and exports MAPPO inventory.
  - Runtime ACA deployment is scripted outside Pulumi (`make runtime-aca-deploy`) into a dedicated runtime resource group to keep Pulumi destroy deterministic.
  - Runtime deploy also manages an ACA Job for Flyway migrations and runs it before app revision rollout (`make runtime-db-migrate-job-run` for on-demand reruns).
  - EasyAuth app registration + frontend auth wiring is handled by script (`make runtime-easyauth-configure`) for deterministic post-deploy callback URL binding.
  - CLI scripts provision/deploy the Function App webhook forwarder and replay inventory events through the webhook path.
  - Partner Center offer lifecycle is handled via API/CLI helper scripts.
  - Portal-only steps are documented in `/Users/cvonderheid/workspace/mappo/docs/marketplace-portal-playbook.md`.

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
Azure execution now supports two release-scoped deployment modes:
- `container_patch`: updates the target Container App resource directly (image/env path).
- `template_spec`: runs an ARM deployment from a Template Spec version per target (resource-group scope by default, subscription scope supported).

Behavior:
- Run orchestration, waves, retries/resume, and stop policies are unchanged.
- `DeploymentRun.execution_mode` snapshots the selected release mode for immutable history.
- Template-spec mode records deployment metadata (deployment name/scope/template-spec version id) and surfaces normalized operation/error details in target logs.

## Determinism + Legibility Contract
- Stage transitions are append-only and timestamped.
- Every failed target has a structured error and correlation IDs.
- Run-level summaries derive from per-target stage truth.

## Related Design Docs
- Template Spec rollout mode: `/Users/cvonderheid/workspace/mappo/docs/template-spec-executor-design.md`
