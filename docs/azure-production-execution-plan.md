# Azure Production Execution Plan

Date: 2026-03-07
Status: active execution plan

## Context

The Azure-hosted demo is now working with real deployments across two subscriptions/tenants using
`deployment_stack` releases.

The older mirrored-Template-Spec path still exists as a fallback demo path, but the validated
production-shaped direction is now:
- use Deployment Stacks as the deployment unit,
- store deployment artifacts in a shared versioned Blob location,
- keep container images in the publisher ACR,
- authenticate runtime image pulls with a dedicated pull-only service principal,
- ingest release metadata into MAPPO from `cvonderheid/mappo-managed-app` via GitHub webhook +
  manifest fetch,
- keep Marketplace publisher management access as the control-plane permission model for
  mutating customer resources.

## Goals

- Replace the demo-only mirrored Template Spec approach with a production-shaped release model.
- Keep MAPPO responsible for rollout orchestration, target selection, retries, and visibility.
- Use immutable release artifacts and deterministic version references.
- Keep MAPPO runtime deployment concerns separate from customer workload release concerns.

## Non-Goals

- Reintroducing provider-tenant Template Spec linkage as the primary production model.
- Making customer targets self-update when shared artifacts change.
- Depending on Partner Center delivery for core engineering progress.

## Target Architecture

### Control Plane
- MAPPO runtime and webhook forwarder remain in this repo.
- Marketplace publisher management access provides MAPPO's principal access to customer managed
  resource groups.

### Release Definition
- `/Users/cvonderheid/workspace/mappo-managed-app` remains the authoritative workload repo.
- That repo produces:
  - immutable stack template artifacts in Blob,
  - immutable container image tags in publisher ACR,
  - release manifest metadata in `releases/releases.manifest.json`.

### Execution
- MAPPO ingests release metadata from GitHub after publication completes.
- MAPPO updates a customer Deployment Stack to a specific immutable template URI + versioned
  parameters.
- The customer workload pulls the referenced image from the publisher ACR using a pull-only
  credential stored in the customer environment.

## Release Artifact Model

Each release should publish:
- `templateUri`: immutable Blob URI for the stack or ARM template
- `templateSha256`: optional integrity checksum for validation
- `image`: publisher ACR image reference including immutable tag or digest
- `softwareVersion`
- `dataModelVersion`
- `executionSourceType=deployment_stack`
- deployment scope metadata

MAPPO should treat the GitHub manifest as the release catalog, but only after the artifact
publication step has succeeded.

## Auth Model

Three auth paths must stay separate:

1. MAPPO -> customer resources
- Auth: Marketplace publisher management access
- Purpose: create/update Deployment Stacks and mutate customer-managed resources

2. MAPPO runtime -> Blob artifact
- Auth: MAPPO Azure principal + `Storage Blob Data Reader`
- Purpose: allow MAPPO to read the published immutable deployment artifact and submit it inline
  to the Deployment Stack API

3. Customer runtime -> publisher ACR
- Auth: dedicated pull-only service principal with `AcrPull`
- Purpose: allow the deployed Container App to pull publisher-hosted images

The ACR pull credential should be stored in the customer environment:
- preferred: customer-side Key Vault reference
- acceptable: Container App secret directly

## Milestones

### Milestone 1: Release Publication Pipeline In `mappo-managed-app`
Status: implemented
**Goal**
- Make `mappo-managed-app` publish production-shaped release artifacts instead of demo-only
  Template Spec versions.

**Deliverables**
- Scripted release workflow in `mappo-managed-app`:
  - `scripts/create_release.mjs` creates draft releases and snapshots deployable artifacts
  - `scripts/publish_release.mjs` publishes immutable deployment artifacts to Blob
  - `scripts/publish_release.mjs` can import/publish workload images into publisher ACR
  - `releases/releases.manifest.json` now carries the production contract fields MAPPO needs

**Acceptance criteria**
- A new release can be created with one scripted workflow.
- The resulting manifest references Blob + ACR artifacts, not provider-tenant Template Specs.

### Milestone 2: GitHub-Triggered Release Ingest In MAPPO
Status: implemented
**Goal**
- Ingest release metadata automatically when `cvonderheid/mappo-managed-app` publishes a new
  release.

**Deliverables**
- Backend webhook endpoint for GitHub release/push notifications
- HMAC verification using `X-Hub-Signature-256`
- Repo/ref/path allowlisting for `cvonderheid/mappo-managed-app`
- Fetch-after-webhook ingest flow against GitHub contents API
- Delivery dedupe keyed by webhook delivery id + manifest/ref

**Acceptance criteria**
- MAPPO never trusts the raw webhook payload as the release definition.
- A pushed/published manifest can appear in MAPPO without manual ingest.

**Implemented path**
- `POST /api/v1/admin/releases/webhooks/github`
- verifies `X-Hub-Signature-256`
- accepts GitHub `push` events and ignores unrelated file changes
- refetches the manifest from GitHub after verification
- ingests published releases and ignores drafts

### Milestone 3: Deployment Stack Executor In MAPPO
Status: implemented and Azure validated
**Goal**
- Add real `deployment_stack` execution so MAPPO can update customer stacks from shared Blob
  artifacts.

**Deliverables**
- New `deployment_stack` executor strategy in the Java backend
- Deployment stack create/update path
- Parameter merge and release snapshot support
- Stack operation logging, correlation IDs, and normalized failures
- Retry/resume semantics aligned with existing run orchestration

**Acceptance criteria**
- MAPPO can roll out a deployment stack release across targets the same way it currently rolls
  out Template Spec releases.
- Per-target run detail includes stack operation visibility comparable to current ARM deployment
  logs.

**Current state**
- Java executor exists and is wired into the existing run orchestration seam.
- Targeted integration tests cover the deployment-stack execution path.
- Azure validation succeeded in the hosted two-subscription environment.
- Live findings that are now part of the design:
  - Deployment Stacks must be created at resource-group scope for the current
    managed-resource-group permission model.
  - The Azure Java SDK `DeploymentStackProperties` serializer emits `"template": null` when
    `templateLink` is used, so MAPPO currently reads Blob artifacts itself and submits the
    template inline.
  - Deployment Stacks require explicit `denySettings`; MAPPO now sets `mode = none`.

### Milestone 4: Customer Runtime Registry Auth
Status: implemented and Azure validated
**Goal**
- Make deployed customer workloads able to pull publisher-hosted images from ACR.

**Deliverables**
- Pull-only multitenant service principal for publisher ACR
- Customer-side secret/key-vault injection model
- Rotation runbook
- Template/stack parameters for registry credentials

**Acceptance criteria**
- A newly updated customer stack can successfully pull the new runtime image without any manual
  customer action.
- Rotation of the pull credential is a planned operator workflow, not tribal knowledge.

**Current state**
- MAPPO target registration metadata now models deployment-stack name and registry auth fields.
- Onboarding defaults seed shared publisher-ACR auth metadata into target registrations.
- The managed workload template accepts registry server/username/password secret parameters.
- The deployment-stack executor injects shared publisher-ACR registry parameters into stack
  updates.
- Live Azure validation succeeded: both target workloads pulled the new image from publisher ACR
  and reported the updated software/data-model versions after rollout.

### Milestone 5: Onboarding Model Alignment
Status: implemented and Azure validated
**Goal**
- Align MAPPO's onboarding records with the stack-based production model.

**Deliverables**
- Target metadata for:
  - customer stack scope/name
  - customer artifact strategy
  - registry auth strategy
  - managed resource group / shared environment references
- Validation that onboarding records contain enough information to execute a deployment stack run
  deterministically

**Acceptance criteria**
- A target can be registered once and then be deployable by release id without hidden manual
  environment assumptions.

**Current state**
- Target registrations now persist deployment stack name and registry auth metadata.
- Onboarding-generated registrations derive deterministic defaults for deployment-stack naming and
  shared publisher-ACR auth when global runtime config is present.
- Azure validation succeeded: the current metadata set was sufficient for end-to-end
  deployment-stack rollouts across both target subscriptions.

### Milestone 6: End-To-End Validation
Status: deployment-stack demo green; live GitHub webhook delivery setup pending
**Goal**
- Prove the full production-shaped path in the current two-subscription demo setup.

**Deliverables**
- Clean hosted demo using:
  - simulated Marketplace lifecycle events
  - GitHub-triggered release ingest
  - Deployment Stack execution from Blob artifacts
  - publisher ACR image pull auth
- Updated runbooks based on the actual successful flow

**Acceptance criteria**
- From a clean Azure state:
  - onboard targets,
  - publish a release from `mappo-managed-app`,
  - ingest it automatically,
  - run a rollout,
  - verify workload version and data model in both target subscriptions.

**Current state**
- Simulated Marketplace onboarding is working against the hosted MAPPO runtime.
- `deployment_stack` release execution is green across both target subscriptions.
- Both deployed workloads report the expected `softwareVersion` and `dataModelVersion`.
- GitHub webhook-triggered ingest is implemented.
- The hosted MAPPO backend now has `MAPPO_MANAGED_APP_RELEASE_WEBHOOK_SECRET` configured.
- Live GitHub webhook delivery to the hosted environment still needs the repo-level webhook to be
  configured. Use `./scripts/github_release_webhook_bootstrap.sh` with a GitHub token to finish
  that step.

## Execution Order

Recommended order of implementation:
1. Release publication pipeline in `mappo-managed-app`
2. GitHub webhook-triggered ingest in MAPPO
3. Deployment Stack executor in MAPPO
4. ACR pull-auth path for customer runtime
5. Onboarding metadata alignment
6. End-to-end validation

Reason:
- MAPPO should not implement the final deployment executor until the release artifact contract is
  stable.
- GitHub-triggered ingest should point at the same manifest shape the executor will consume.

## Risks And Decisions

### Keep current Template Spec demo path while building the new path
Do not remove the working demo executor until the Deployment Stack path is validated in Azure.

### Use Blob, not Template Specs, as the shared common artifact location
Blob + immutable versioned URIs remain the shared release-artifact location. The current Java
implementation has MAPPO read Blob artifacts directly and submit inline Deployment Stack templates
because the Azure Java SDK `templateLink` path is not currently reliable for this use case.

### Use publisher ACR for images, but do not assume Marketplace grants image-pull access
Marketplace publisher management access covers control-plane mutation of customer resources. It
does not automatically grant the deployed customer workload rights to pull from publisher ACR.

## Immediate Next Actions

1. Configure live GitHub webhook delivery from `cvonderheid/mappo-managed-app` into the hosted
   MAPPO environment with `./scripts/github_release_webhook_bootstrap.sh --github-token <token>`.
2. Decide whether to keep the current inline-template Deployment Stack implementation as the
   production path or invest in a lower-level Azure REST path for direct `templateLink` support.
3. Keep the mirrored-Template-Spec path only as a fallback demo path, not the primary rollout
   model.
