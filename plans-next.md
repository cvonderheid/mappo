# MAPPO Next Plan (`plans-next.md`)

Date: 2026-03-06

## Theme
Java-first completion and production-like Azure runtime:
- finish the real Azure execution paths,
- keep build/package workflows Maven-native,
- keep runtime operations explicit through scripts and Pulumi,
- rebuild the cloud demo from the cleaned Java baseline.

## Verification Checklist
- [x] `./mvnw clean install` passes from repo root.
- [x] Active docs use Maven + direct scripts/Pulumi, not removed Make or UV workflows.
- [x] Frontend client generation is driven from the Java backend OpenAPI artifact.
- [x] Legacy backend files and UV workspace metadata are removed from the active runtime path.
- [x] Real `template_spec` resource-group execution exists behind a testable strategy seam.

## Status Snapshot (2026-03-06)
- [x] Java backend cutover completed.
- [x] Frontend Maven lifecycle wiring completed.
- [x] Active docs/runbook command surface cleaned.
- [ ] `template_spec` subscription-scope execution
- [ ] `deployment_stack` execution
- [ ] `bicep` execution
- [ ] Pulumi-managed runtime/forwarder lifecycle
- [ ] Clean Azure demo rebuild from current baseline

## Phase A: Execution Completeness
**Scope**
- Implement real `template_spec` execution for subscription scope.
- Implement real `deployment_stack` execution.
- Implement real `bicep` execution.
- Keep run orchestration, retries, and halt/resume semantics consistent across strategies.

**Acceptance criteria**
- Unsupported source/scope combinations are reduced to only intentionally deferred cases.
- Each strategy records normalized stage logs, deployment metadata, and Azure identifiers.
- Orchestration-level tests cover every strategy branch through interfaces/stubs where live Azure is not required.

**Verification commands**
- `./mvnw -pl backend test`
- `./mvnw -pl backend verify`

## Phase B: Runtime and Deploy Model Alignment
**Scope**
- Move ACA runtime resource lifecycle and marketplace forwarder infrastructure under Pulumi where appropriate.
- Keep scripts for auth/bootstrap, validation, packaging, and manual operator actions.
- Preserve the split between artifact publish and infrastructure rollout.

**Acceptance criteria**
- Resource creation/update/destroy for steady-state cloud runtime is Pulumi-managed.
- Script surface is reduced to non-IaC concerns.
- Docs describe one authoritative deploy/update path.

**Verification commands**
- `./mvnw -pl infra/pulumi -DskipTests compile`
- `cd infra/pulumi && pulumi preview --stack <stack>`

## Phase C: Demo Rebuild From Clean Baseline
**Scope**
- Recreate the provider runtime and two-target demo environment from the cleaned Java repo.
- Use webhook-style onboarding and demo-fleet event simulation as the default validation path.
- Keep DB/runtime state empty until onboarding events or explicit release registration occur.

**Acceptance criteria**
- Local and cloud demos start from a clean slate with no hidden seed data.
- Demo runbooks are executable end-to-end without referencing removed Python or Make workflows.
- Fleet state is populated only through onboarding/registration flows.

**Verification commands**
- `python3 scripts/docs_consistency_check.py`
- `python3 scripts/check_no_demo_leak.py`
- `./mvnw clean install`

## Phase D: Marketplace Validation Readiness
**Scope**
- Keep the private-offer/Partner Center path documented and scriptable where Microsoft allows.
- Preserve simulated webhook ingress until publisher account prerequisites are available.
- Ensure the current demo mirrors the production auth and onboarding model as closely as possible.

**Acceptance criteria**
- Forwarder path remains the primary onboarding ingress.
- Portal-only steps are isolated to the playbook.
- No target registration path depends on direct inventory import as the default flow.

**Verification commands**
- Run the live demo checklist against a fresh environment.
- Validate forwarder replay and one canary deployment end-to-end.
