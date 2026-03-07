# MAPPO Next Plan (`plans-next.md`)

Date: 2026-03-07

## Phase
Demo readiness

## Theme
Production-shaped Azure demo with explicit repo boundaries:
- this repo deploys MAPPO itself,
- `/Users/cvonderheid/workspace/mappo-managed-app` defines the customer workload releases,
- Maven owns build and artifact publish,
- Azure rollout stays explicit and deterministic.

## Status Snapshot
- [x] Java backend cutover completed.
- [x] Frontend Maven lifecycle wiring completed.
- [x] Repo is Java-only; no `.py` files or embedded Python shell blocks remain.
- [x] `./mvnw clean install` passes from repo root.
- [x] Real `template_spec` resource-group execution exists behind a testable strategy seam.
- [x] Maven deploy contract implemented and dry-verified (`deploy` vs `deploy -Pazure`)
- [x] Release ingest wired cleanly against `/Users/cvonderheid/workspace/mappo-managed-app`
- [ ] Azure full demo rebuilt from clean baseline
- [ ] End-to-end demo validation completed

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

## Milestone C: Azure Demo Rebuild
**Scope**
- Recreate runtime, forwarder, DB, and demo-fleet from the cleaned Java baseline.
- Use simulated Marketplace events through the real forwarder/onboarding path.
- Start from a clean DB/runtime state.

**Acceptance criteria**
- Clean bring-up works without hidden seed data.
- Targets appear from onboarding events, not manual import as the default path.
- Hosted MAPPO UI is fully usable against Azure-backed state.

**Verification**
- `./scripts/azure_preflight.sh`
- runtime/forwarder deploy
- demo-fleet up
- simulated onboarding import through the forwarder/backend path

## Milestone D: Full Demo Validation
**Scope**
- Execute the full demo the current account model supports.
- Validate the same control-plane behavior we would rely on in production, minus Partner Center delivery.

**Acceptance criteria**
- Demo proves:
  - onboarding events,
  - target registration,
  - release ingest,
  - canary rollout,
  - broader rollout,
  - logs/status/health/version updates.
- Remaining gaps are recorded as product backlog, not implicit operator knowledge.

**Verification**
- Full walkthrough from clean Azure state
- Runbook updated from actual execution

## Deferred Until After Demo
- Real `template_spec` execution at subscription scope.
- Real `deployment_stack` execution.
- Real `bicep` execution.
- Real Partner Center/private-offer validation once publisher prerequisites exist.

## Verification Checklist
- `./mvnw clean install`
- `./mvnw deploy -Ddocker.image.prefix="$MAPPO_IMAGE_PREFIX" -Dmappo.image.tag="<image-tag>"`
- `./mvnw -Pazure deploy -Ddocker.image.prefix="$MAPPO_IMAGE_PREFIX" -Dmappo.image.tag="<image-tag>" -Dpulumi.stack="<stack>"`
- Azure demo smoke: onboarding -> release ingest -> canary rollout -> broader rollout
