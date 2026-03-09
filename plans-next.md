# MAPPO Next Plan (`plans-next.md`)

Date: 2026-03-07

## Phase
Platform hardening and scale readiness

## Theme
Stabilize the platform under the production-shaped Azure model that is already running:
- this repo deploys MAPPO itself,
- `/Users/cvonderheid/workspace/mappo-managed-app` defines the customer workload releases,
- Springdoc/OpenAPI is the authoritative contract surface,
- large tables move to backend-backed pagination/filtering/sorting,
- runtime health becomes an explicit probe model,
- SSE replaces fast polling once those contracts are stable.

## Status Snapshot
- [x] Java backend cutover completed.
- [x] Frontend Maven lifecycle wiring completed.
- [x] Repo is Java-only; no `.py` files or embedded Python shell blocks remain.
- [x] `./mvnw clean install` passes from repo root.
- [x] Real `template_spec` resource-group execution exists behind a testable strategy seam.
- [x] Maven deploy contract implemented and dry-verified (`deploy` vs `deploy -Pazure`)
- [x] Release ingest wired cleanly against `/Users/cvonderheid/workspace/mappo-managed-app`
- [x] Azure-hosted two-subscription demo validated end to end
- [x] Production release artifact contract defined in `mappo-managed-app`
- [x] GitHub webhook-triggered release ingest implemented
- [x] Real `deployment_stack` execution implemented in the Java backend
- [x] Publisher ACR pull-auth path modeled into customer runtime rollout
- [x] Deployment-stack + publisher ACR path validated end to end in Azure
- [x] Runs table pagination implemented end to end with backend filters and page metadata
- [x] Fleet/Admin pagination implemented end to end with backend filters, page metadata, and shared frontend pagination controls

## Milestone H: OpenAPI Contract Hardening
**Scope**
- Keep Springdoc as the canonical contract source for the Java backend.
- Standardize paginated response shapes and query DTO patterns.
- Make client generation a routine part of backend contract changes instead of an afterthought.

**Acceptance criteria**
- New collection endpoints follow one reusable page metadata contract.
- Frontend generated types stay in sync with backend DTO/query changes in the same slice.
- No legacy/manual contract surfaces remain in the active workflow.

**Current focus**
- Normalize paginated collection contracts so filtering/sorting parameters and page DTOs follow one naming and shape convention across runs, targets, and admin surfaces.
- Remove remaining compatibility wrappers where the frontend can safely use only the generated paginated contracts.

**Verification**
- `./mvnw -pl backend verify`
- `./mvnw -pl frontend package`
- `./mvnw clean install`

## Milestone I: Backend Pagination And Query Contracts
**Scope**
- Extend backend-backed pagination/filtering/sorting beyond runs to Fleet/Admin/log-heavy views.
- Move away from full-snapshot list responses for operator tables.

**Acceptance criteria**
- `targets`, Admin tables, and other high-volume lists have server-side paging.
- Filters/sorts are query-driven rather than client-only.
- Frontend tables use a shared pagination control surface.

**Status**
- [x] Runs
- [x] Fleet
- [x] Admin tabs
- [ ] Remaining log-heavy/detail surfaces

**Verification**
- API integration tests per paginated endpoint
- Frontend unit tests for pagination/filter state
- Hosted UI smoke on Fleet/Admin/Deployments

## Milestone J: Runtime Health Model
**Scope**
- Separate runtime availability from deployment outcome permanently.
- Add probe/check storage plus API/UI exposure.

**Acceptance criteria**
- Fleet `Runtime` is sourced from explicit checks, not historical deployment state.
- `Last Deployment` remains an independent signal.
- Probe timestamps and failure summaries are operator-visible where they matter.

**Next slice**
- Introduce persisted runtime check records and an API shape that Fleet can consume without overloading target registration/deployment state.

**Verification**
- Backend health/probe integration tests
- Fleet UI assertions for healthy/unhealthy/unknown targets independent of last deployment

## Milestone K: SSE Live Updates
**Scope**
- Introduce SSE invalidate/refetch events for the main operator surfaces:
  - runs list,
  - selected run detail,
  - fleet targets,
  - releases,
  - admin logs/webhook deliveries.

**Acceptance criteria**
- Normal operator workflows no longer depend on 1.2s polling.
- SSE reconnect/fallback behavior is explicit.
- Current fetch functions are reused; no fragile live object patch layer is introduced.

**Next slice**
- Start with invalidate/refetch events for runs list, selected run detail, Fleet targets, releases, and Admin webhook/event tabs on top of the now-stable paginated endpoints.

**Verification**
- backend SSE integration coverage
- frontend event/reconnect tests
- hosted demo soak test with the polling interval reduced to slow fallback only

## Milestone L: Data Retention And Auditability
**Scope**
- Add retention, indexing, and query hygiene for growing operational tables.
- Keep operator-visible audit trails for releases, previews, and deployments usable at larger scale.

**Acceptance criteria**
- Run/log/webhook tables remain responsive under larger demo/prod volumes.
- Retention policy is explicit and documented.
- Admin/audit views keep enough detail without forcing full-table scans.

**Verification**
- explain/analyze or equivalent query review for hot paths
- retention smoke tests
- docs/runbook updates

## Milestone A: MAPPO Deploy Contract
**Scope**
- Keep `install` side-effect free.
- Make `deploy` publish MAPPO runtime artifacts only.
- Make `deploy -Pazure` perform the Azure rollout for MAPPO runtime/forwarder:
  - Pulumi apply,
  - DB migration job execution,
  - runtime revision update.

**Acceptance criteria**
- No live Azure mutation occurs in the default lifecycle.
- Artifact versions come directly from Maven into the Azure rollout path.
- The deploy command surface is simple enough to operate without remembering script order.

**Verification**
- `./mvnw clean install`
- `./mvnw deploy`
- `./mvnw deploy -Pazure`

## Milestone B: Managed-App Repo Integration
**Scope**
- Treat `/Users/cvonderheid/workspace/mappo-managed-app` as the authoritative release-definition repo.
- Ingest `releases/releases.manifest.json` into MAPPO as release records.
- Keep MAPPO runtime deployment separate from managed-app release publication.

**Acceptance criteria**
- Release ingestion works without manual MAPPO code changes per release.
- Docs show the boundary between “deployer” and “thing being deployed.”
- Demo flow uses the managed-app repo as the release source.

**Verification**
- Ingest from `/Users/cvonderheid/workspace/mappo-managed-app/releases/releases.manifest.json`
- Confirm releases appear in MAPPO UI/API

## Milestone C: Production Release Publication Contract
Status: implemented

**Scope**
- Define the production release manifest and publication workflow in `mappo-managed-app`.
- Publish immutable deployment artifacts to Blob.
- Publish immutable workload images to publisher ACR.

**Acceptance criteria**
- A new release can be created with one workflow.
- Manifest metadata points at Blob + ACR artifacts, not provider-tenant Template Specs.
- MAPPO can ingest the published manifest without MAPPO code changes.

**Verification**
- Release publication flow in `mappo-managed-app`
- Manifest ingest into MAPPO
- Artifact references validated before rollout

**Implemented**
- `mappo-managed-app/scripts/create_release.mjs`
- `mappo-managed-app/scripts/publish_release.mjs`
- production-shaped manifest fields in `releases/releases.manifest.json`

## Milestone D: GitHub Webhook Release Ingest
Status: implemented

**Scope**
- Add GitHub webhook-triggered release ingest to MAPPO.
- Verify webhook signatures and fetch the manifest from GitHub after the event.

**Acceptance criteria**
- MAPPO does not trust raw webhook payloads as release definitions.
- New manifests in `cvonderheid/mappo-managed-app` can be ingested automatically.
- Delivery dedupe and repo/ref allowlisting are in place.

**Verification**
- GitHub webhook event -> MAPPO fetch -> release ingest
- Duplicate delivery suppression

**Implemented**
- `POST /api/v1/admin/releases/webhooks/github`
- HMAC verification
- repo/ref/path allowlisting
- published-release ingest with draft suppression

## Milestone E: Deployment Stack Execution
Status: implemented and Azure validated

**Scope**
- Implement real `deployment_stack` execution using shared Blob-hosted artifacts.

**Acceptance criteria**
- MAPPO can roll out a `deployment_stack` release across the current demo targets.
- Run detail includes stack-level logs, correlation, and failure normalization.

**Verification**
- End-to-end stack update in both demo subscriptions
- Version/data-model verification in both target workloads

**Current state**
- Java deployment-stack executor is wired through the existing run orchestration seam.
- Backend integration coverage exists for the execution strategy.
- Azure-hosted execution is validated end to end across both demo targets.
- Current design constraints from live Azure execution:
  - use resource-group-scoped Deployment Stacks for the current MRG permission model,
  - set explicit `denySettings`,
  - fetch Blob artifacts in MAPPO and submit inline templates because the Azure SDK
    `templateLink` path emitted invalid payloads.

## Milestone F: Customer Runtime Registry Auth
Status: implemented and Azure validated
**Scope**
- Add the production image-pull auth model for customer workloads using publisher ACR.

**Acceptance criteria**
- Deployed customer workloads can pull publisher-hosted images without manual customer action.
- Pull credential storage and rotation are explicit operator workflows.

**Verification**
- ACR pull credential injection
- Successful customer workload pull after release rollout

**Current state**
- Target registration metadata captures deployment-stack and registry auth settings.
- Onboarding derives shared publisher-ACR auth defaults from MAPPO runtime config.
- The managed workload template accepts registry credentials and secret-name parameters.
- The deployment-stack executor injects shared-service-principal registry parameters.
- Live Azure validation proved customer pull success across both demo targets.

## Milestone G: End-To-End Production-Shaped Validation
Status: demo green; live GitHub webhook delivery setup pending
**Scope**
- Validate the full post-demo architecture in the current two-subscription environment.

**Acceptance criteria**
- Simulated onboarding
- GitHub-triggered release ingest
- Deployment Stack rollout
- Publisher ACR image pull
- Version/data-model verification in both customer targets

**Verification**
- Full walkthrough from clean Azure state
- Runbooks updated from actual execution

**Current state**
- Simulated Marketplace onboarding is working in the hosted environment.
- `deployment_stack` release execution is green across both demo targets.
- Both target workloads report the expected software/data-model versions after rollout.
- The hosted backend has its GitHub webhook secret configured via
  `./scripts/github_release_webhook_bootstrap.sh`.
- The remaining live step is wiring the real GitHub webhook from
  `cvonderheid/mappo-managed-app` into the hosted MAPPO endpoint.

## Deferred Until After Platform Hardening
- Real Partner Center/private-offer validation once publisher prerequisites exist.
- Alternative customer-local artifact strategy beyond Blob + Deployment Stacks, if needed.

## Verification Checklist
- `./mvnw clean install`
- `./mvnw deploy -Ddocker.image.prefix="$MAPPO_IMAGE_PREFIX" -Dmappo.image.tag="<image-tag>"`
- `./mvnw -Pazure deploy -Ddocker.image.prefix="$MAPPO_IMAGE_PREFIX" -Dmappo.image.tag="<image-tag>" -Dpulumi.stack="<stack>"`
- GitHub webhook -> release ingest
- Deployment Stack rollout across both demo targets
- Workload version/data-model verification across both demo targets
- Pagination contract checks on runs/targets/admin endpoints
- SSE smoke once the event stream exists

## Detailed Plan
- `/Users/cvonderheid/workspace/mappo/docs/azure-production-execution-plan.md`
