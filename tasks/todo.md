# MAPPO Task Plan

Date: 2026-03-06
Owner: Codex

## Current State
- Canonical backend module is `/Users/cvonderheid/workspace/mappo/backend`.
- Frontend build/test/codegen lifecycle is owned by `/Users/cvonderheid/workspace/mappo/frontend/pom.xml`.
- Active operator docs use Maven for build lifecycles and direct scripts/Pulumi for runtime operations.
- The legacy backend implementation has been removed from the active repo surface.
- Real Azure execution currently exists for `template_spec` at resource-group scope.

## Scope (Current Slice)
Repository cleanup after Java cutover:
- remove stale Python-backend references from docs, plans, tasks, and scripts where they no longer describe the real system,
- prune migration-diary clutter from planning/task files,
- keep only current Java/Maven-era workflow guidance.

## Plan (Current Slice)
- [x] Audit docs, scripts, tasks, and plans for stale Python-backend references.
- [x] Rewrite active docs and planning/task files so they describe the Java/Maven repo as it exists now.
- [x] Remove obsolete Python-only workspace metadata that is no longer used.
- [x] Run verification checks after the cleanup and capture results.

## Verification Commands (Current Slice)
- [x] `python3 scripts/docs_consistency_check.py`
- [x] `python3 scripts/check_no_demo_leak.py`
- [x] `./mvnw -q -DskipTests clean install`

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

## Active Backlog

### 1. Execution completeness
- Implement real `template_spec` execution at subscription scope.
- Implement real `deployment_stack` execution.
- Implement real `bicep` execution.
- Keep the staged run state machine and logging contract consistent across strategies.

### 2. Deploy model alignment
- Move steady-state runtime and forwarder infrastructure under Pulumi where feasible.
- Keep scripts for auth/bootstrap, validation, packaging, and explicit operator actions.
- Preserve the split between artifact publish and infrastructure rollout.

### 3. Demo rebuild from clean baseline
- Recreate the Azure demo environment from the cleaned Java repo.
- Keep onboarding event-driven by default.
- Ensure no hidden seed data is required for startup.

### 4. Marketplace validation readiness
- Keep the Partner Center/private-offer path documented.
- Use the forwarder/webhook path as the primary onboarding boundary.
- Treat real marketplace validation as blocked only by publisher-account prerequisites, not by repo workflow confusion.
