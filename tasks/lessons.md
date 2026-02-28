# MAPPO Lessons Log

Purpose: capture recurring correction patterns and preventative guardrails.

## Template
- Date:
- Pattern:
- Preventative rule:
- Detection signal:
- Enforcement (test/lint/checklist):

## Entries
- Date: 2026-02-25
- Pattern: Starting implementation before governance/process contract is explicitly established.
- Preventative rule: For non-trivial work, update `tasks/todo.md` and relevant `plans*.md` before writing implementation code.
- Detection signal: Code changes appear without corresponding plan/checklist updates.
- Enforcement (test/lint/checklist): `make workflow-discipline-check` in phase gates.

- Date: 2026-02-25
- Pattern: UI implementation started without first applying inherited shadcn/ui standard from project conventions.
- Preventative rule: Before frontend implementation, explicitly verify design-system requirements from inherited standards and scaffold required UI primitives first.
- Detection signal: New UI files appear without shadcn primitives/config (`components.json`, `ui/*`, Tailwind integration).
- Enforcement (test/lint/checklist): frontend kickoff checklist in `tasks/todo.md` plus lint/typecheck pass after shadcn integration.

- Date: 2026-02-26
- Pattern: Leaving legacy naming/style remnants after refactors (relative imports when alias standard exists, `txero` identifiers in MAPPO compose config).
- Preventative rule: Run project-wide grep for old naming and non-standard import paths immediately after large integration changes.
- Detection signal: `rg -n "txero|from \"\\./|from \"\\.\\./"` returns hits in active project code/config where MAPPO conventions require `mappo` and `@/...`.
- Enforcement (test/lint/checklist): add grep sweep to phase-close checklist before final verification commands.

- Date: 2026-02-26
- Pattern: UI behavior regressions slipped because frontend tests only asserted broad render smoke and did not assert critical operator controls (release selector presence, group member preview, action enable/disable states).
- Preventative rule: For UI changes affecting deployment controls, add explicit assertions for control presence, disabled/enabled behavior, and screen-level visibility in both component tests and browser-flow checks.
- Detection signal: UI change lands with no test asserting expected control labels/state transitions; only snapshot/smoke checks pass.
- Enforcement (test/lint/checklist): require at least one frontend behavior test update per UX change and add Playwright smoke checks for Deployments critical path in phase gate.

- Date: 2026-02-26
- Pattern: Progress UI represented terminal completion as all-success (single-color 100%) and hid failed-target composition.
- Preventative rule: For rollout status UI, always visualize outcome composition with distinct segments/colors and assert failed-segment rendering in tests.
- Detection signal: Halted/partial runs display a fully-success color bar or labels that imply all-success despite non-zero failed count.
- Enforcement (test/lint/checklist): require progress-component tests (or Playwright checks) that validate presence of failed segments when `failed_targets > 0`.

- Date: 2026-02-28
- Pattern: Live-demo guidance drifted toward Lighthouse-specific setup even though product intent is Marketplace-style Managed Application onboarding.
- Preventative rule: For Azure architecture changes, update preflight checks, setup scripts, docs, and UI terminology in the same slice so the default path matches the real-world deployment model.
- Detection signal: `rg -n "Lighthouse" README.md docs/ scripts/ frontend/src` shows Lighthouse as the primary path without explicitly labeling it optional.
- Enforcement (test/lint/checklist): phase-close grep + `make azure-preflight` + checklist update in `docs/live-demo-checklist.md`.

- Date: 2026-02-28
- Pattern: Ad-hoc shell copy/paste steps made Azure onboarding hard to reproduce.
- Preventative rule: Convert repetitive setup flows into checked-in scripts and Make targets before asking for reruns.
- Detection signal: onboarding instructions include multiline command snippets not wrapped by repo scripts.
- Enforcement (test/lint/checklist): ensure each onboarding step is invokable by `make <target>` and documented in README.

- Date: 2026-02-28
- Pattern: Discovery script wrote output before enforcing validity checks, which allowed a failed run to overwrite a known-good inventory with an empty file.
- Preventative rule: For file-producing scripts, enforce validation gates before any write, or write to temp + atomic replace only on success.
- Detection signal: command exits non-zero but output artifact still changes.
- Enforcement (test/lint/checklist): add regression check for “no-data failure does not modify existing file” and verify by rerunning the failing command path.

- Date: 2026-02-28
- Pattern: Managed app discovery used the generic `az resource list` path and missed fields/objects returned by the managed app control-plane API.
- Preventative rule: For Azure service-specific resources, prefer service-specific CLI (`az managedapp ...`) over generic resource listing.
- Detection signal: known resources exist in portal/`az managedapp list` but discovery returns zero rows.
- Enforcement (test/lint/checklist): smoke-check discovery against one known managed app and assert `managed_resource_group_id` is captured.

- Date: 2026-02-28
- Pattern: Provider registration checks were too strict and blocked progress while namespace state remained `Registering`.
- Preventative rule: Treat long-running provider registration as eventually consistent; continue with explicit warning when downstream commands are already viable.
- Detection signal: repeated registration polling times out even though create calls succeed manually.
- Enforcement (test/lint/checklist): registration helper should accept `Registering` after timeout and emit warning, not hard fail.

- Date: 2026-02-28
- Pattern: Primary operator workflow became cluttered with alternative setup paths (Lighthouse/Pulumi/simulation), which slowed demo execution.
- Preventative rule: Keep a single default command path in README/Make help for active demo mode; move alternatives out of primary surface.
- Detection signal: top-level docs list multiple mutually exclusive setup tracks before the first successful demo path.
- Enforcement (test/lint/checklist): include a “primary demo path” section with <=5 commands and verify it works end-to-end.

- Date: 2026-02-28
- Pattern: Deleting resource groups that host ACA environments can silently fail when any container app is still attached to the environment.
- Preventative rule: Before deleting an environment RG, explicitly query for apps bound to that environment and migrate/delete them first.
- Detection signal: `ResourceGroupDeletionBlocked` with `ManagedEnvironmentHasContainerApps`.
- Enforcement (test/lint/checklist): add cleanup checklist step to check `az containerapp list` for `managedEnvironmentId` references before RG delete.

- Date: 2026-02-28
- Pattern: Demo data bootstrap was embedded in production runtime initialization, hiding missing inventory/release setup and causing confusion when validating live behavior.
- Preventative rule: Keep sample/demo bootstrapping in explicit scripts/tests only; production modules must never auto-seed targets/releases on startup.
- Detection signal: backend runtime constructors contain `seed` methods or default to demo execution behavior without explicit configuration.
- Enforcement (test/lint/checklist): `make check-no-demo-leak` includes patterns for runtime seed/default-demo markers; phase-close review verifies live startup with empty DB remains empty until import/bootstrap commands run.

- Date: 2026-02-28
- Pattern: Cross-project remnants (`txero` identifiers/credentials) persisted in MAPPO defaults after migration work, creating confusion and trust issues.
- Preventative rule: MAPPO code/config must never embed another project name or credentials; all defaults and examples must be `mappo`-scoped.
- Detection signal: `rg -n "txero" backend/ frontend/ scripts/ infra/` returns hits outside historical task notes.
- Enforcement (test/lint/checklist): add cross-project grep sweep to phase-close checklist and fail review if runtime/test config contains non-MAPPO identifiers.

- Date: 2026-02-28
- Pattern: Demo orchestration drifted into mixed tracks (simulation/Lighthouse/legacy IaC) that obscured the marketplace-realistic path.
- Preventative rule: Keep exactly one primary demo workflow and enforce a hard boundary map: Pulumi IaC for deployable resources, scripts for API-only tasks, and documented portal playbook for manual-only steps.
- Detection signal: `make help` and README list multiple conflicting setup tracks before a single successful end-to-end flow.
- Enforcement (test/lint/checklist): each phase close must include a workflow surface review (`make help`) and docs boundary section update (`README` + playbook).

- Date: 2026-02-28
- Pattern: Seeded release metadata used image tags that do not exist in MCR, causing Azure deployment failures that looked like runtime/orchestration issues.
- Preventative rule: Default/seed release images must use registry-validated tags (or full image refs) before being promoted into demo or production bootstrap paths.
- Detection signal: Deployment fails in `DEPLOYING` with `MANIFEST_UNKNOWN` / image-not-found while validation passes.
- Enforcement (test/lint/checklist): add a release-bootstrap smoke check that performs one real-target canary deployment and fails if image pull fails.

- Date: 2026-02-28
- Pattern: Azure Portal deep links used `BrowseResource` blade URLs that can fail with portal-side `browsePrereqs` errors in some tenant/resource contexts.
- Preventative rule: Use direct resource overview links (`#resource/.../overview`) for operator deep links instead of browse blade links.
- Detection signal: Clicking a stage portal link shows a portal error with `browsePrereqs` and suggests simplified view.
- Enforcement (test/lint/checklist): run-detail smoke check must verify generated `portal_link` format uses direct resource links.

- Date: 2026-02-28
- Pattern: Admin discovery relied on generic ARM resource listing for managed applications, which returned false-empty results in live subscriptions.
- Preventative rule: Use service-specific ARM endpoints (`Microsoft.Solutions/applications`) for managed app inventory; do not depend on generic `resources.list(filter=resourceType ...)` for discovery correctness.
- Detection signal: `az managedapp list` or ARM Solutions endpoint returns apps while backend discovery reports zero targets.
- Enforcement (test/lint/checklist): discovery smoke must compare managed-app count from backend path against known managed app instances in at least one subscription.

- Date: 2026-02-28
- Pattern: Assuming one global tenant authority for all subscriptions caused cross-tenant discovery/deploy failures (`InvalidAuthenticationTokenTenant`) despite valid managed app target IDs.
- Preventative rule: Resolve tenant authority per subscription (mapping + target metadata), and instantiate Azure credentials per tenant instead of per process.
- Detection signal: run/discovery fails only for subscriptions in non-default tenants while same-tenant targets succeed.
- Enforcement (test/lint/checklist): add tenant-resolution unit tests, require `MAPPO_AZURE_TENANT_BY_SUBSCRIPTION` coverage in `azure-preflight` for non-GUID inventory tenants, and verify one cross-tenant deployment smoke before demo signoff.
