# MAPPO Implementation Plan

## Status
- Phase: Planning in progress.
- Constraint: Process/gate baseline is required before implementation milestones.
- Current baseline: governance artifacts and phase gates are established.

## Delivery Principles
- Control-plane first: MAPPO orchestrates cross-tenant upgrades; customer subscriptions are targets.
- Determinism over convenience: run state transitions, wave ordering, and status events must be reproducible.
- Durable execution semantics: every per-target stage transition is persisted and auditable.
- Scope correctness: no cross-target contamination of parameters, logs, or status.
- Root cause over bandaids: bug fixes include regression tests where practical.

## Confirmed Product Decisions
- UI + backend from day one.
- Runtime stack: Java backend + TypeScript frontend.
- Hosting direction: Azure Container Apps.
- Target inventory source: marketplace/onboarding registration with admin correction paths.
- Rollout strategy v1: tag-based waves.
- Stop policies: threshold by percent or absolute failure count.
- Verify stage: health checks (CodeDeploy-like).
- Recovery model: resume from failed.
- Retention: 3 months logs/history.
- Approvals/gates: out of scope for v1.
- Initial scale target: 100 tenants.

## V1 Scope
1. Target inventory + tagging + current version tracking.
2. Release registry for Template Spec versions and notes.
3. Deployment runs with all-at-once and basic waves.
4. Per-target stage/status/logs with correlation IDs.
5. Retry/resume failed executions.

## V1 Non-goals
- Auto-promotion across canary rings.
- Approval workflows/manual gates.
- Autonomous rollback orchestration.
- Multi-region active-active control plane.

## Global Milestone Definition of Done
- Acceptance criteria met.
- `./mvnw -pl backend verify` passes.
- Frontend contract commands (`client-gen`, `typecheck`, `test`, `build`) pass via the frontend Maven module lifecycle.
- Relevant module tests pass.
- Docs/plans updated for behavior changes.
- Determinism and legibility invariants preserved.

## Proposed Repo Shape (target)
- `backend/`
  - `src/main/java/com/mappo/controlplane/api/*`
  - `src/main/java/com/mappo/controlplane/service/*`
  - `src/main/java/com/mappo/controlplane/repository/*`
  - `src/main/resources/db/migration/*`
  - `src/test/*`
- `frontend/`
  - `src/main.tsx`
  - `src/App.tsx`
  - `src/features/{fleet,releases,runs}/*`
- `docs/`
  - `architecture.md`
  - `documentation.md`
  - `engineering-playbook.md`
  - `golden-principles.md`
  - `implement.md`
- `tasks/`
  - `todo.md`
  - `lessons.md`

## Milestone Plan
### Milestone 1: Governance + gate baseline
- Scope: process artifacts, check scripts, and phase gate targets.
- Verify: backend verify plus frontend Maven module checks.

### Milestone 2: Backend skeleton + API contract baseline
- Scope: Spring Boot app, health/router/error model, OpenAPI generation.
- Verify: `./mvnw -pl backend test`, `./mvnw -pl backend verify`.

### Milestone 3: Domain model + storage for targets/releases/runs
- Scope: persistence models, repositories, service contracts.
- Verify: migration/repository/service tests.

### Milestone 4: Deployment orchestrator v1 (waves + thresholds + resume)
- Scope: queue/executor logic, per-target state machine, stop policies.
- Verify: deterministic execution tests.

### Milestone 5: Frontend fleet/release/run surfaces
- Scope: list/filter targets, register releases, start and monitor runs.
- Verify: frontend unit/e2e flow coverage.

### Milestone 6: ACA/Lighthouse integration boundary hardening
- Scope: delegated execution adapters, correlation/log capture, health checks.
- Verify: adapter contract tests and failure handling tests.

### Milestone 7: Operational hardening
- Scope: retention policy, observability, failure taxonomy, regression workflow.
- Verify: gate + regression intake checks.

## Ongoing Decision Log
- Decision 001: Java/TypeScript stack chosen for the current control plane implementation.
- Decision 002: Phase gates are mandatory before implementation work is accepted as complete.
- Decision 003: Resume-from-failed semantics are first-class in v1 rollout orchestration.

## Review Checklist Before Coding
- [ ] Plan and milestone acceptance criteria are current.
- [ ] Gate targets and verification commands are executable.
- [ ] Risks and non-goals are explicit.
- [ ] Docs terminology is consistent (Target, Release, Deployment Run).
