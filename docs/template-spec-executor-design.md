# MAPPO Template Spec Deployment Mode

Date: 2026-03-02
Status: Implemented (Phase 1 + Phase 2)

## Why This Exists
Current Azure execution mode updates a single Container App resource directly. That is good for image/config rollout, but it does not update additional resources inside the managed resource group (for example Container Jobs, storage, Key Vault wiring, network resources).

This design adds a second deployment mode where MAPPO deploys a full Template Spec version per target while preserving MAPPO's rollout orchestration (waves, stop policies, retries, resume, logs).

## Goals
- Use Azure's deployment engine for desired-state convergence (do not re-implement Pulumi behavior in MAPPO).
- Support multi-resource upgrades per target managed resource group.
- Preserve existing run UX and state machine.
- Keep retries/resume idempotent and safe.
- Capture ARM deployment operations and errors in MAPPO logs.

## Non-Goals
- Replacing Pulumi as authoring/publishing tool.
- Supporting destructive `Complete` mode by default.
- Automatically inferring safe rollback of stateful data migrations.

## Role Split
- Pulumi (or existing IaC pipeline): authors infra and publishes Template Spec versions.
- MAPPO: orchestrates which version goes to which targets and when.
- Azure ARM deployment engine: performs actual resource-level state reconciliation.

## Proposed Execution Modes
- `container_patch` (existing behavior): patch Container App directly.
- `template_spec` (new): deploy Template Spec version via ARM deployment at RG scope.

## Data Model Changes

## Release
Add fields to `Release`:
- `deployment_mode`: enum `container_patch | template_spec` (default `container_patch` for backward compatibility).
- `template_spec_version_id`: optional full version resource ID; if null, derive from `(template_spec_id, template_spec_version)`.
- `deployment_scope`: enum `resource_group | subscription` (default `resource_group`).
- `deployment_mode_settings`: JSON object for mode-specific settings:
  - `arm_mode`: `Incremental` default.
  - `what_if_on_canary`: bool default false.
  - `verify_after_deploy`: bool default true.

Rationale:
- Keep release self-contained and deterministic.
- Snapshot execution intent at release creation time.

## DeploymentRun
Add field:
- `execution_mode`: copy of release `deployment_mode` at run creation (immutable run snapshot).

Rationale:
- Historical runs remain interpretable even if release defaults change later.

## API Changes

## Create Release
Extend `CreateReleaseRequest` with optional:
- `deployment_mode`
- `template_spec_version_id`
- `deployment_scope`
- `deployment_mode_settings`

Defaults preserve current behavior.

## Read APIs
`GET /releases`, `GET /runs`, `GET /runs/{id}` include the above fields.

## No change to Create Run contract required
Runs still reference `release_id`; execution mode is resolved from release.

## Executor Contract

Keep current orchestration shape in `RunsDomainMixin` and extend runtime strategy behind `TargetExecutor`:

```python
class TargetExecutor(Protocol):
    async def prepare_run(self, *, targets: list[Target], requested_concurrency: int) -> RunGuardrailPlan: ...

    def execute_target(
        self,
        *,
        run: DeploymentRun,
        target: Target,
        release: Release,
        attempt: int,
    ) -> AsyncIterator[TargetExecutionEvent]: ...
```

Implementation:
- Existing `AzureTargetExecutor` remains.
- It dispatches DEPLOYING behavior by `release.deployment_mode`.

Internal strategy split:
- `AzureContainerPatchStrategy` (existing flow).
- `AzureTemplateSpecStrategy` (new flow).

## AzureTemplateSpecStrategy Flow (per target)
1. VALIDATING
- Resolve tenant authority for target subscription.
- Validate target has managed resource group context:
  - preferred source: target registration `managed_resource_group_id`.
  - fallback: parse from managed app metadata if present.
- Validate release has template spec version reference.

2. DEPLOYING
- Build deployment name:
  - `mappo-{run_short}-{target_short}-a{attempt}` (<=64 chars).
- Merge parameters:
  - base: `release.parameter_defaults`
  - plus optional target metadata-derived parameters (future hook).
- Submit ARM deployment:
  - scope: managed resource group (default).
  - template source: template spec version ID.
  - mode: Incremental.
- Poll until terminal state.
- Stream deployment-operation summaries to target logs:
  - resource type/name
  - operation state
  - status code/message
  - request/correlation IDs

3. VERIFYING
- Reuse current verification path:
  - health probe hints/logic.
  - staged failures with structured error details.

4. SUCCEEDED/FAILED
- Existing run orchestration rules unchanged.

## Reliability and Idempotency
- Use deterministic deployment names per run/target/attempt.
- Retry policy:
  - retry transient submission failures before poller accepted.
  - if deployment accepted, poll to terminal and do not duplicate blindly.
- Resume/retry failed:
  - new attempt uses incremented attempt suffix.
- Safe default:
  - ARM `Incremental` mode only.
- Optional what-if:
  - run before first canary wave when `what_if_on_canary=true`.

## Logging and Operator Visibility

Target stage logs should include:
- `deployment_name`
- `deployment_id`
- `template_spec_version_id`
- ARM correlation/request IDs
- operation-level failures
- normalized Azure error payload summary

Structured error details on failure:
- `code`, `message`
- `arm_status`
- `operation_id`
- `failed_resource_id`
- `inner_errors` subset

## Permissions Model

Minimum for template-spec mode (per target):
- Contributor on target managed resource group for deployment and resource writes.
- Read access to template spec version (if in provider subscription and not publicly readable, ensure identity can read that resource).

Optional:
- Reader on subscription only for quota preflight and broader validation calls.

Notes:
- This is still publisher identity auth (cross-tenant SP model), not workload managed identity.

## Migration Plan

Phase 1: Schema/API prep
- Add release/run fields and migrations.
- Keep default `deployment_mode=container_patch`.
- Regenerate ORM/OpenAPI/client.
- Status: complete.

Phase 2: Strategy implementation
- Implement `template_spec` deployment path in Azure executor with per-release mode switch.
- Add mocked integration tests for mode selection and run snapshot behavior.
- Status: complete.

Phase 3: Logging enrichment
- Add ARM deployment operation log normalization.
- Surface operation summaries in run detail UI.

Phase 4: Controlled rollout
- Create canary release with `template_spec` mode.
- Validate on 1-2 targets, then waves.
- Keep fallback release in `container_patch` mode.

Phase 5: Promote default
- After stable runs, set release creation default to `template_spec`.
- Keep `container_patch` as compatibility mode for simple workloads.

## Testing Strategy

Unit:
- parameter merge behavior.
- deployment name determinism and length limits.
- retry behavior for transient ARM errors.
- error normalization from deployment operations.

Integration (mock Azure runtime):
- run with mixed success/failure targets.
- halt policy triggers.
- resume/retry semantics with attempt increments.

Live smoke:
- canary run against 2 targets with template change affecting multiple resources.
- verify operation logs and final resource states in both targets.

## Open Questions
- Should we support subscription-scope deployments in v1 or only RG scope?
- Should MAPPO persist per-target parameter overrides as first-class data?
- Do we need an explicit pre-deploy approval gate between canary and broad waves?

## Recommendation
- Implement `template_spec` mode now as the default path for real customer rollouts.
- Keep `container_patch` mode for dev/demo simplicity and emergency compatibility.
