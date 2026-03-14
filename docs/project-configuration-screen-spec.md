# MAPPO Project Configuration Screen Spec

Date: 2026-03-13
Status: proposed

## Purpose
Define a production-grade Project Configuration experience in MAPPO so operators can manage multi-driver projects without editing Flyway seed data.

## Design Goals
- Keep project setup explicit and auditable.
- Separate release-ingest configuration from deployment-execution configuration.
- Validate configuration before a run is attempted.
- Support multiple driver types without one giant generic JSON editor.

## Information Architecture
Use one Project Settings page with tabs.

1. `General`
- Project ID (immutable)
- Project display name
- Project status (enabled/disabled)
- Project description

2. `Release Ingest`
- Provider type:
  - GitHub webhook
  - Azure DevOps service hook
  - Manual ingest only
- Repo/org/project pointers
- Branch/ref filters
- Manifest path or pipeline event contract
- Webhook auth secret reference
- Dedupe behavior

3. `Deployment Driver`
- Driver type:
  - `azure_deployment_stack`
  - `pipeline_trigger`
  - (future drivers)
- Driver-specific fields:
  - Deployment Stack: preview mode, timeout/concurrency defaults
  - Pipeline Trigger: organization, project, pipeline ID, branch, service connection name
- Capability matrix (read-only badges):
  - supports preview
  - supports cancel
  - supports external logs
  - supports external execution handle

4. `Access & Identity`
- Access strategy:
  - `azure_workload_rbac`
  - `lighthouse_delegated_access`
  - `simulator`
- Auth model display and requirements:
  - tenant/service-principal references
  - required target metadata keys
  - required RBAC scopes

5. `Target Contract`
- Required execution metadata keys by project:
  - example for ADO App Service: `resourceGroup`, `appServiceName`
- Optional metadata keys
- Validation examples

6. `Runtime Health`
- Provider:
  - `azure_container_app_http`
  - `http_endpoint`
- Probe path
- Expected status
- Timeout
- Probe cadence (if project-level override is enabled)

7. `Validation`
- `Test release webhook` action
- `Test driver credentials` action
- `Test target contract` action (against selected target)
- Last validation results table (pass/fail + timestamp + details)

8. `Audit`
- Recent config changes
- Recent webhook deliveries
- Recent driver execution failures related to this project

## Screen Pattern
- Use shadcn tabs for section switching.
- Use a sticky action bar at top-right:
  - `Save draft`
  - `Validate`
  - `Publish config`
- Use inline field validation plus Sonner toasts for non-persistent notifications.
- Use a read-only JSON preview panel at the bottom showing the normalized config payload MAPPO will persist.

## Backend Contract Alignment
Current read endpoint:
- `GET /api/v1/projects`

Needed write endpoints:
1. `POST /api/v1/projects`
- create project definition

2. `PATCH /api/v1/projects/{projectId}`
- partial update for tabs
- optimistic concurrency via `etag` or `version`

3. `POST /api/v1/projects/{projectId}/validate`
- run configuration checks and return structured findings

4. `GET /api/v1/projects/{projectId}/audit`
- config and operational audit stream

## Data Model Notes
- Keep current typed domain structure (`accessStrategy`, `deploymentDriver`, `releaseArtifactSource`, `runtimeHealthProvider`) and avoid raw untyped JSON in the UI.
- Persist provider-specific config in typed objects server-side, not in frontend-owned free-form blobs.
- Do not allow changing `projectId` after creation.

## UX Inspiration References
- Argo CD Projects: policy boundaries and source/destination guardrails
  - https://argo-cd.readthedocs.io/en/latest/user-guide/projects/
- Azure DevOps Service Hooks: event subscription model and delivery history
  - https://learn.microsoft.com/en-us/azure/devops/service-hooks/overview?view=azure-devops
- Octopus Project Settings: lifecycle + deployment controls model
  - https://octopus.com/docs/projects/setting-up-projects

## Rollout Plan
1. Add backend write + validation endpoints.
2. Build Project Settings UI route and forms.
3. Introduce read-only JSON preview and validation panel.
4. Enable project cloning to simplify onboarding new projects.
5. Remove DB-seeded project-only dependency for normal operations.

## Non-Goals
- No dynamic plugin marketplace in this phase.
- No fully custom per-project schema designer in UI.
- No migration of old project IDs in-place.
