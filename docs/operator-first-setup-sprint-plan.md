# MAPPO Operator-First Setup Sprint Plan

Date: 2026-03-14  
Status: planned

## Purpose
Move MAPPO from script-led setup to operator-led setup in the product UI so project configuration, target onboarding, and release notifications are explicit, auditable, and understandable for first-time Azure/GitHub/ADO users.

## Constraints and Principles
- Keep tenant boundaries explicit at every step:
  - Provider tenant: `abe468b2-18bb-4dd2-90b9-5b8982337eb7`
  - Customer-mimic tenant: `5476530d-fba1-4cd5-b2c0-fa118c5ff36e`
- Avoid hidden setup state in scripts when an operator-facing page can own it.
- Prefer idempotent API-backed setup actions with visible validation/audit logs.
- Do not edit already-applied Flyway migrations for behavioral changes.

## Sprint 0: Identity Boundary Cleanup and Baseline
### Goal
Establish a clean, understandable baseline before new implementation work.

### Scope
1. Remove stale/obsolete ADO setup artifacts not used by the active demo.
2. Verify service connection, webhook, and Entra app object ownership/lifecycle.
3. Reconcile live DB/project state against intended architecture.
4. Capture a one-page environment inventory (what exists, why it exists).

### Deliverables
- Cleanup script with dry-run mode for ADO service-connection identity teardown.
- Updated environment inventory and ownership map.
- Verified post-cleanup baseline checks.

### Acceptance Criteria
- No unknown identity objects remain for retired ADO wiring.
- Every remaining Entra app/SP has an owner, purpose, and reference.
- `projects`, targets, and integrations align with active demo intent.

## Sprint 1: Project Configuration Backend
### Goal
Make project setup first-class in backend APIs and persistence.

### Scope
1. Project CRUD and configuration patch model hardening.
2. Typed config validation for each driver/integration.
3. Validation endpoints:
  - credential test
  - webhook test
  - target-contract test
4. Audit logging for project config changes.

### Deliverables
- Stable `/api/v1/projects` configuration and validation APIs.
- Project-change audit records and query API.
- OpenAPI contracts + generated frontend client updates.

### Acceptance Criteria
- Operators can configure ADO/GitHub/project settings without scripts.
- Invalid configuration is rejected with actionable validation messages.
- Project setup changes are visible in audit history.

## Sprint 2: Project Settings UI
### Goal
Ship a full in-product setup workflow for projects.

### Scope
1. New Project Settings route and navigation entry.
2. Tabs:
  - General
  - Release Ingest
  - Deployment Driver
  - Access & Identity
  - Target Contract
  - Runtime Health
  - Validation
  - Audit
3. Save/validate/publish flow with Sonner notifications.
4. Read-only normalized payload preview.

### Deliverables
- Operator-ready Project Settings experience.
- End-to-end API wiring for all setup tabs.
- Page object coverage for core setup flows.

### Acceptance Criteria
- A new project can be configured from UI only.
- Existing script-managed project can be edited from UI.
- Setup errors are understandable to non-expert operators.

## Sprint 3: Target Onboarding UI and Workflows
### Goal
Replace event-ingest script dependency with guided onboarding in UI.

### Scope
1. Target onboarding wizard and target CRUD.
2. Project-aware target registration contract.
3. Bulk import path (file/API) with validation preview.
4. Registration and metadata edit audit trail.

### Deliverables
- UI workflows for add/edit/delete target registration.
- Bulk import/validate/commit flow.
- Clear target state model: identity, runtime, deployment status.

### Acceptance Criteria
- Targets can be onboarded/updated without shell scripts.
- Operators can see exactly why a target is invalid/misconfigured.
- Registration state is consistent across Fleet/Admin views.

## Sprint 4: Integration Setup and Operator Enablement
### Goal
Give first-time users clear setup guidance for GitHub and ADO integrations.

### Scope
1. Integration setup screens:
  - GitHub webhook config helper
  - ADO service-hook config helper
2. In-app guided instructions with tenant/subscription context hints.
3. Runbooks for manual steps that cannot be fully automated.
4. Troubleshooting playbooks tied to common errors.

### Deliverables
- UI integration setup checklists with status.
- Updated docs for first-time operators.
- In-app links from errors to exact remediation docs.

### Acceptance Criteria
- A new operator can set up release notifications end-to-end.
- No reliance on hidden script side effects for core integration setup.
- Help content uses clear tenant/subscription/principal terminology.

## Sprint 5: Script Decommission and Hardening
### Goal
Keep only infrastructure/bootstrap scripts that are still justified.

### Scope
1. Deprecate runtime/project setup scripts replaced by UI.
2. Keep infrastructure scripts for environment bring-up only.
3. Add regression checks so deprecated script paths do not re-enter docs.
4. Final hardening sweep on data integrity and UX consistency.

### Deliverables
- Script inventory with keep/remove decisions.
- Deprecated scripts removed or marked internal-only.
- Final operator runbook for the demo workflow.

### Acceptance Criteria
- Core operator tasks are UI/API first.
- Scripts are optional tooling, not required product workflow.
- Docs and product behavior are coherent for first-time users.

## Out of Scope (This Program)
- Reworking deployment semantics for all future drivers in this phase.
- Introducing additional cloud providers before setup UX stabilizes.
- Major schema redesign not required for project/setup workflows.
