# MAPPO Task Plan

Date: 2026-03-07
Owner: Codex

## Current State
- Canonical backend module is `/Users/cvonderheid/workspace/mappo/backend`.
- Frontend build/test/codegen lifecycle is owned by `/Users/cvonderheid/workspace/mappo/frontend/pom.xml`.
- Active operator docs use Maven for build lifecycles and direct scripts/Pulumi for runtime operations.
- The legacy backend implementation has been removed from the active repo surface.
- Real Azure execution currently exists for `template_spec` at resource-group scope and
  `deployment_stack` at resource-group scope.
- MAPPO runtime + forwarder live in `/Users/cvonderheid/workspace/mappo`.
- The customer workload/release-definition repo lives in `/Users/cvonderheid/workspace/mappo-managed-app`.
- The hosted Azure demo now uses Deployment Stacks only; provider/customer Template Spec resources have been removed.

## Scope (Current Slice)
Post-demo production-path planning and execution setup:
- keep this repo focused on MAPPO runtime, forwarder, and rollout orchestration,
- treat `mappo-managed-app` as the release-definition repo that publishes workload artifacts,
- pivot from the demo-only mirrored Template Spec model toward Deployment Stacks +
  Blob-hosted artifacts + publisher ACR image pulls,
- make GitHub webhook-triggered release ingest the default release registration path.

## Plan (Current Slice)
- [x] Milestone 1: Finalize the deploy contract for this repo.
- [x] Milestone 2: Wire release ingestion against `/Users/cvonderheid/workspace/mappo-managed-app`.
- [x] Milestone 3: Rebuild the Azure demo from the clean Java/Maven baseline.
- [x] Milestone 4: Run the full Azure-hosted demo end to end and record operator runbooks/gaps.
- [x] Milestone 5: Define the production release artifact contract for Deployment Stacks.
- [x] Milestone 6: Implement GitHub webhook-triggered release ingest.
- [x] Milestone 7: Implement real `deployment_stack` execution with Blob-hosted artifacts.
- [x] Milestone 8: Add publisher ACR pull-auth flow to the customer runtime model.
- [x] Milestone 9: Validate deployment-stack + publisher ACR rollout in Azure.

## Verification Commands (Current Slice)
- [x] `./mvnw clean install`
- [x] `./mvnw deploy` (dry-verified with `-Ddocker.skip=true`)
- [x] `./mvnw deploy -Pazure` (dry-verified with `-Ddocker.skip=true -Dexec.skip=true`)
- [x] Azure demo smoke: release ingest -> cross-tenant rollout -> workload version/data-model verification
- [x] GitHub webhook -> manifest fetch -> release ingest
- [x] Deployment Stack rollout across both demo targets
- [x] Publisher ACR image pull validation in both demo targets

## Results Log (Current Slice)
- 2026-03-06: Cleaned active docs to remove removed Makefile and Python-backend workflow references.
- 2026-03-06: Rewrote `plans-next.md` around the current Java-era backlog instead of the old Python-to-Java migration sequence.
- 2026-03-06: Collapsed `tasks/todo.md` into an active task plan instead of keeping a large migration diary with stale Python references.
- 2026-03-06: Pruned `tasks/lessons.md` to Java/Maven-era lessons that are still actionable.
- 2026-03-06: Removed the obsolete root `pyproject.toml` workspace file.
- 2026-03-06: Verified docs consistency, demo-leak checks, and full reactor `clean install` are green after the cleanup.
- 2026-03-06: Pinned `RunLifecycleIntegrationTests` to simulator mode so ambient `MAPPO_AZURE_*` shell variables cannot flip it into the real Azure execution path and cause nondeterministic `500` failures.
- 2026-03-06: Verified the simulator-path test and the Azure-enabled template-spec execution test both pass under forced fake Azure credentials.
- 2026-03-06: Removed two noisy backend warnings from normal test/build output by explicitly enabling SpringDoc API docs and suppressing jOOQ's version-support logger for the generic OSS `POSTGRES` dialect.
- 2026-03-06: Replaced the Python marketplace forwarder with a Java Azure Functions module, switched forwarder packaging to Maven-generated artifacts, and removed the last tracked `.py` runtime file from the repo.
- 2026-03-06: Moved `marketplace_ingest_events.sh`, `marketplace_forwarder_replay_inventory.sh`, and `release_ingest_from_repo.sh` off embedded Python and onto Java tooling commands behind the `tooling` module.
- 2026-03-07: Replaced the remaining embedded-Python Azure helper scripts with Java-backed `azure-script-support` tooling commands, added shell-safe args-file bootstrapping for raw JSON payloads, and verified the full reactor `./mvnw clean install` stays green.
- 2026-03-07: Decoupled the frontend image from the backend URL at build time by switching it to runtime-supplied `MAPPO_API_BASE_URL` config generated at container start.
- 2026-03-07: Added `delivery/pom.xml` and Maven deploy wiring so `mvn deploy` publishes MAPPO runtime artifacts and `mvn deploy -Pazure` composes the Azure rollout path on top.
- 2026-03-07: Added `--skip-build` and `--skip-app-deploy` to `runtime_aca_deploy.sh` so runtime rollout can be composed into prepare/migrate/apply phases.
- 2026-03-07: Added stack-based defaults to `marketplace_forwarder_deploy.sh` so forwarder rollout no longer depends on manually typed resource names.
- 2026-03-07: Added backend release-manifest ingest from GitHub (`/api/v1/admin/releases/ingest/github`), defaulted it to `cvonderheid/mappo-managed-app`, and verified backend tests plus OpenAPI export.
- 2026-03-07: Wired the Admin UI to ingest managed-app releases from the GitHub manifest and refresh the release list without mixing that action into the Demo panel.
- 2026-03-07: Fixed the managed-app ARM template for real Template Spec publishing by changing `containerCpu` to a string parameter and converting it with ARM `json(...)`, then published Template Spec versions `2026.03.04.1` and `2026.03.07.1`.
- 2026-03-07: Fixed the managed-app output field to use `latestRevisionFqdn` instead of the nonexistent `properties.configuration.ingress.fqdn`, eliminating the provider-side nested deployment failure.
- 2026-03-07: Updated `AzureTemplateSpecExecutor` to mirror provider-side Template Spec version IDs into the target subscription for the two-tenant demo because customer-tenant ARM deployments cannot directly consume provider-tenant Template Spec IDs.
- 2026-03-07: Reconciled the demo-fleet stack after drift removed the provider target resource group, refreshed Pulumi state, and recreated the missing provider target resources.
- 2026-03-07: Resolved the remaining customer-target deployment failures by granting the customer-tenant runtime principal Reader on the mirrored Template Spec resource group and Contributor on the shared demo-fleet managed environment resource group.
- 2026-03-07: Verified an end-to-end hosted run (`run-ee6b20eff2`) succeeded across both target subscriptions and confirmed both target endpoints now return `softwareVersion=2026.03.07.1` and `dataModelVersion=3`.
- 2026-03-07: Wrote the post-demo production execution plan in `docs/azure-production-execution-plan.md`, capturing the Deployment Stack + Blob + publisher ACR + GitHub webhook direction that replaces the demo-only mirrored Template Spec model.
- 2026-03-07: Reworked `/Users/cvonderheid/workspace/mappo-managed-app/releases/releases.manifest.json` around a production-shaped release contract and added `scripts/create_release.mjs` plus `scripts/publish_release.mjs` to create draft releases and publish Blob/ACR-backed artifacts.
- 2026-03-07: Added GitHub webhook-triggered release ingest at `/api/v1/admin/releases/webhooks/github`, including HMAC verification, repo/ref/path allowlisting, manifest refetch, and draft-release suppression.
- 2026-03-07: Added a real Java `deployment_stack` executor strategy behind the existing run orchestration seam and verified it with targeted backend integration coverage.
- 2026-03-07: Modeled publisher ACR pull-auth into target registration metadata, onboarding defaults, deployment-stack parameter injection, and the managed workload template so the remaining work is Azure-side validation rather than schema/runtime design.
- 2026-03-08: Validated the `deployment_stack` path live across both Azure demo targets and fixed the real Azure constraints the code hit:
  - Deployment Stacks must be created at resource-group scope for the current managed-resource-group permission model.
  - The Azure Java SDK emitted an invalid `template: null` payload when using `templateLink`, so MAPPO now reads Blob artifacts directly and submits inline templates.
  - Deployment Stacks require explicit `denySettings`; MAPPO now sets `mode = none`.
  - MAPPO's Azure principal needed `Storage Blob Data Reader` on the release-artifact storage account.
- 2026-03-08: Verified the publisher ACR pull-auth path end to end in Azure; both demo targets rolled to release `2026.03.07.2` and reported `dataModelVersion = 4`.
- 2026-03-08: Added `scripts/github_release_webhook_bootstrap.sh`, configured the hosted backend with `MAPPO_MANAGED_APP_RELEASE_WEBHOOK_SECRET`, and reduced the remaining live GitHub step to repository-side webhook creation.
- 2026-03-08: Removed the live demo's provider/customer Template Spec resources, deleted the customer legacy control-plane resource group, removed fake demo `managedApplicationId` config, and rewrote the topology docs around the active deployment-stack path.
- 2026-03-08: Reworked Azure deployment failure normalization to pull the deepest available failed-operation/failed-resource message, preserve Azure request/correlation/deployment/resource metadata, and stop surfacing raw SDK object text in operator errors.
- 2026-03-08: Simplified the shell header to `MAPPO Control Plane` + `MULTI-TENANT MANAGED APP ORCHESTRATOR`, removed the non-actionable `Attention Needed` KPI, and expanded run-detail Azure error rendering to show the normalized summary plus deployment metadata.
- 2026-03-08: Added `/api/v1/runs/preview` and a Deployment Stack preview flow that runs ARM `what-if` against the exact resolved Blob-backed template + parameters for resource-group-scoped stack releases, then renders per-target change summaries and caveats in the deployment drawer.
- 2026-03-08: Verified the live failure-path finding that Azure Deployment Stack SDK responses can still hide the actionable nested error until MAPPO re-reads the stack resource after failure; the executor now does that follow-up read so operator errors show the deepest available resource-specific detail.
- 2026-03-09: Added backend-backed pagination for deployment runs (`GET /api/v1/runs`) with page metadata, server-side filters (`runId`, `releaseId`, `status`), frontend pagination controls, and integration coverage so the first large table no longer relies on full-list polling.
- 2026-03-09: Extended backend-backed pagination to Fleet and Admin surfaces (`targets`, registrations, onboarding events, forwarder logs, release webhook deliveries), wired the frontend tables to shared pagination controls and generated contracts, and verified the full reactor build stays green.

## Milestones

### Milestone 1: Deploy Contract For MAPPO Runtime
**Goal**
- `mvn clean install` remains a pure local build/test/package workflow.
- `mvn deploy` publishes MAPPO runtime artifacts without mutating Azure.
- `mvn deploy -Pazure` performs Azure rollout for MAPPO itself:
  - Pulumi apply for runtime/infra,
  - DB migration container job run,
  - runtime revision update without relying on manual restart as the primary path.

**Acceptance criteria**
- No Azure side effects occur during `install`.
- Artifact versions flow directly from Maven into the Azure rollout path.
- Demo-event replay is not coupled to Maven deploy.

### Milestone 2: Release Repo Integration
**Goal**
- Treat `/Users/cvonderheid/workspace/mappo-managed-app` as the system of record for customer workload releases.
- Ingest release metadata from its `releases/releases.manifest.json`.
- Keep MAPPO and managed-app release cadences independent.

**Acceptance criteria**
- MAPPO can ingest releases from the managed-app repo without hand-editing MAPPO runtime code.
- Release records clearly reflect source type/scope metadata needed for execution.
- Docs show the repo boundary unambiguously:
  - MAPPO repo deploys the deployer,
  - managed-app repo defines what MAPPO deploys.

**Status**
- [x] Backend release ingest from GitHub manifest
- [x] Admin UI action for managed-app release ingest
- [x] Azure demo exercised against the managed-app repo content and release manifest

### Milestone 3: Azure Demo Rebuild
**Goal**
- Recreate the provider runtime and two-target demo from the cleaned Java baseline.
- Use simulated Marketplace events through the real forwarder/onboarding path.
- Keep startup free of hidden seed data beyond explicit release/bootstrap actions.

**Acceptance criteria**
- Azure runtime, forwarder, and demo-fleet can be brought up from a clean slate.
- Target registration comes from onboarding events, not direct inventory import as the default operator flow.
- Hosted MAPPO UI shows fleet, releases, runs, and logs against the real Azure-backed demo.

### Milestone 4: Full Demo Validation
**Goal**
- Run the end-to-end Azure-hosted demo that the current account model can support.
- Validate the production-shaped path short of real Partner Center delivery.

**Acceptance criteria**
- Demo covers:
  - onboarding event ingestion,
  - registered target visibility,
  - release ingest from `mappo-managed-app`,
  - canary deployment,
  - broader rollout,
  - run logs and target health/version updates.
- Any remaining gaps are documented as product backlog, not hidden in operator procedure.

**Current state**
- [x] Hosted release ingest from `mappo-managed-app`
- [x] Successful full rollout across both demo target subscriptions
- [x] Target health/version updates reflected in MAPPO and in the deployed workload endpoints
- [ ] Clean-slate rerun of the hosted forwarder path in this slice

## Active Backlog After Demo
- Add operator-visible GitHub webhook delivery audit records so release-webhook success, skip, and failure paths are visible without inferring from side effects.
- Decide whether to keep the current inline-template Deployment Stack path as the production implementation or invest in a lower-level Azure REST path for direct `templateLink` support.
- Move more steady-state runtime/forwarder lifecycle into Pulumi where appropriate.
- Keep the real Partner Center/private-offer path documented and ready for later validation when publisher-account prerequisites are available.
- Extend the new preview path beyond `deployment_stack` + resource-group scope once the production execution surface expands to subscription-scope stacks or additional release source types.
- Decide whether preview cancellation should remain client-side request abort only or become a first-class backend cancelable job model.
- Add backend-backed pagination, filtering, and sorting to the remaining log-heavy detail surfaces before the demo data model grows further.
- Replace the current stored runtime badge with a real probe/check model that distinguishes runtime availability from deployment outcome.
- Introduce SSE invalidation events once the paginated query contracts are stable enough to stream targeted refreshes instead of polling snapshots.
- Harden retention/indexing/auditability for runs, logs, webhook deliveries, and previews so table growth does not degrade query latency.

## Current Focus
- Capture the final operator runbook for the deployment-stack demo path.
- Follow through on the new hosted custom domains:
  - backend webhook URL at `api.mappopoc.com`
  - frontend UI at `www.mappopoc.com`
- Improve preview readability and delivery observability based on the hosted demo feedback.
- [x] Add Admin-visible GitHub release webhook delivery logs.
- [x] Move the “new release available” banner into the shared app shell.
- [x] Split Fleet target state into version freshness + runtime health + last deployment outcome.
- [x] Reframe preview output around release impact + infrastructure risk, with raw ARM paths demoted to technical details.
- [x] Redeploy the hosted runtime so `www.mappopoc.com` talks to `api.mappopoc.com` with working CORS.
- [x] Move `Refresh Snapshot` into the Registered Targets tab context and replace transient Admin action banners with Sonner toasts.
- [x] Make run creation asynchronous so the Deployments drawer can close immediately after the run is accepted instead of blocking on full execution.
- [x] Honor `strategyMode` and `concurrency` in run execution so `all_at_once` can execute multiple targets in parallel and `waves` respects wave ordering.
- [ ] Replace stored `healthStatus` as the long-term runtime signal with an explicit probe/check model so Fleet runtime state is no longer dependent on historical deployment status or manual correction.
- [x] Convert the Deployments runs table to backend-backed pagination with shared pagination controls and server-side filters.
- [x] Extend backend-backed pagination to Fleet, Admin tabs, and any log-heavy detail surface that currently transfers full snapshots.
- [ ] Tighten Springdoc/OpenAPI as the authoritative contract surface: keep generated clients current, remove stale wrapper paths, and make paginated/query DTO changes contract-first.
- [ ] Add SSE-based invalidate/refetch updates for runs, fleet, releases, and admin events so the UI can drop the current 1.2s polling model.
- [ ] Add explicit runtime probe storage/API/UI so Fleet `Runtime` is backed by actual checks instead of historical deployment state.

## Platform Hardening Program

### Sprint 1: Contract And Pagination Baseline
**Goal**
- Stabilize the API surface that future SSE and larger datasets will rely on.

**Scope**
- Make paginated responses the default pattern for large collections.
- Keep Springdoc/OpenAPI authoritative for the Java backend.
- Use generated frontend clients/types for every new paginated contract.

**Acceptance criteria**
- Deployment runs are fully paginated end to end.
- Page metadata shape is standardized and reusable.
- `./mvnw clean install` proves backend OpenAPI export plus frontend client/typecheck/test/build in one pass.

**Status**
- [x] Runs pagination end to end
- [x] Standardize the same pattern for targets/admin/log-heavy views

### Sprint 2: Runtime Signal And Scale Readiness
**Goal**
- Separate operational concepts cleanly and keep queries fast as data grows.

**Scope**
- Introduce explicit runtime probes/check-ins.
- Add server-side pagination/filtering/sorting to Fleet/Admin.
- Add indexes/retention where needed for runs, logs, and webhook deliveries.

**Acceptance criteria**
- Fleet `Runtime` is backed by probe data.
- Fleet/Admin list APIs no longer return full snapshots by default.
- Large tables remain responsive under realistic demo/prod volumes.

### Sprint 3: Live Updates
**Goal**
- Replace fast polling with server-driven invalidate/refetch events.

**Scope**
- Add SSE endpoints/events for runs, run detail, fleet, releases, and admin logs.
- Keep a slower polling fallback while the SSE path hardens.
- Do not stream giant payload patches; emit coarse invalidation events and reuse current fetch paths.

**Acceptance criteria**
- Normal operator workflows update live without 1.2s polling.
- Reconnect and fallback behavior are documented and tested.
- SSE does not break current authenticated/custom-domain deployment paths.

## Detailed Plan Reference
- `/Users/cvonderheid/workspace/mappo/docs/azure-production-execution-plan.md`
