# MAPPO Lessons Log

Purpose: capture recurring correction patterns and preventative guardrails.

## Template
- Date:
- Pattern:
- Preventative rule:
- Detection signal:
- Enforcement (test/lint/checklist):

## Entries
- Date: 2026-03-06
- Pattern: Type-hardening initially stopped at request DTOs while response records and repository projections still leaked `Map<String, Object>`, leaving the Java contract half-typed.
- Preventative rule: When hardening an API contract, cover both ingress and egress in the same slice; repositories and response records should never rebuild anonymous maps for structured data.
- Detection signal: `rg -n "Map<String, Object>" backend-java/src/main/java/com/mappo/controlplane/{model,repository,api/request}` returns hits outside generic error handlers.
- Enforcement (test/lint/checklist): add a contract-hardening grep check plus integration assertions for nested JSON fields before closing the slice.

- Date: 2026-03-01
- Pattern: Intermediate refactor state left compatibility shim modules (`control_plane_storage*`) in place, which obscured actual ownership boundaries.
- Preventative rule: Once repositories are domain-scoped and stable, delete compatibility shims in the same slice; do not carry dead adapter layers into subsequent work.
- Detection signal: runtime code compiles without shim imports, but shim files still exist and suggest obsolete architecture.
- Enforcement (test/lint/checklist): add an architecture grep check for forbidden module names (`control_plane_storage`) under `backend/app`.

- Date: 2026-03-01
- Pattern: Initial repository extraction used a single cross-domain repository, preserving monolith coupling and unclear ownership.
- Preventative rule: Repositories must be domain-scoped (`admin`, `runs`, `targets`, `releases`) and should not aggregate unrelated use-cases.
- Detection signal: repository class exposes mixed CRUD methods across multiple bounded contexts.
- Enforcement (test/lint/checklist): architecture checklist requires one repository per domain plus router imports restricted to services only.

- Date: 2026-03-01
- Pattern: DB session factory was created in multiple places (module-global and runtime), making lifecycle ownership ambiguous.
- Preventative rule: App lifespan is the single owner of engine/session-factory for API runtime; dependencies resolve sessions from app state.
- Detection signal: `create_engine_and_session_factory()` invoked both at import-time and app-startup for the same runtime process.
- Enforcement (test/lint/checklist): grep check for module-global session factory creation in runtime modules; require app-state session-factory in startup flow.

- Date: 2026-03-01
- Pattern: Router logic drifted into persistence access (forwarder logs), bypassing service boundaries and making protocol handlers responsible for data concerns.
- Preventative rule: Routers should only map HTTP protocol to service calls; no router should import repository/storage modules.
- Detection signal: `rg -n "control_plane_storage|repositories" backend/app/api/routers` returns matches.
- Enforcement (test/lint/checklist): add layering grep check to phase-close checklist and require router dependency injection from `app/services/*` only.

- Date: 2026-03-01
- Pattern: Mixin refactor introduced shadowed helper methods (`NotImplementedError` stubs) due inheritance order, causing runtime regressions despite typecheck passing.
- Preventative rule: Avoid stub methods in mixins that duplicate concrete method names from sibling domains; prefer attribute annotations or explicit integration tests before merge.
- Detection signal: core API flows fail with `NotImplementedError` from domain mixin methods after class hierarchy changes.
- Enforcement (test/lint/checklist): after domain refactors, run full backend tests (not just lint/mypy) and verify MRO-sensitive methods in `ControlPlaneStore`.

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

- Date: 2026-02-28
- Pattern: Demo stack defaults (`targetProfile=demo10`, single global principal object ID) caused accidental 10-target provisioning and managed app authorization failures in customer tenant.
- Preventative rule: Keep Pulumi defaults non-destructive (`targetProfile=empty`) and require deterministic stack configuration scripts that resolve tenant-local principal object IDs per subscription.
- Detection signal: `pulumi up` plans/creates unexpected `managed-app-target-03..10` resources or returns `Principal ... does not exist in the directory ...`.
- Enforcement (test/lint/checklist): require running `make iac-configure-marketplace-demo ...` before live demo stack updates and keep docs/checklist aligned to that command.

- Date: 2026-02-28
- Pattern: Azure Database for PostgreSQL Flexible Server integration drifted on provider-specific details (admin username format, extension allowlist, firewall access) causing runtime/migration failures.
- Preventative rule: Treat managed DB provider behavior as first-class infra contract: emit provider-correct connection username, configure required server parameters (`azure.extensions`), and include explicit local access firewall strategy for demo workflows.
- Detection signal: local DB connection fails after successful provisioning, or Flyway fails with extension allowlist errors (`pgcrypto` not allow-listed).
- Enforcement (test/lint/checklist): after IaC apply, run managed DB smoke sequence (`iac-export-db-env` -> connect test -> `make db-migrate`) before declaring demo stack ready.

- Date: 2026-02-28
- Pattern: Preflight used a hardcoded target-count expectation (`~10`) that generated noise during intentional 2-target demo phases.
- Preventative rule: Operational readiness checks must use configurable thresholds with phase-appropriate defaults rather than fixed assumptions.
- Detection signal: preflight warns about target count even when current planned demo topology is intentionally smaller.
- Enforcement (test/lint/checklist): expose target-count threshold via env/config and verify preflight output for both 2-target and 10-target modes.

- Date: 2026-03-01
- Pattern: UX cleanup request to remove Azure Portal links was only partially implemented, leaving one "Open in Azure Portal" button in run-stage cards.
- Preventative rule: When removing a UI affordance globally, run a project-wide grep for all label variants and remove every render path before close.
- Detection signal: `rg -n "Open in Azure Portal|portal_link" frontend/src` still returns clickable UI usage after cleanup.
- Enforcement (test/lint/checklist): add a UI cleanup checklist step requiring label grep + one click-through verification in Deployments view.

- Date: 2026-03-01
- Pattern: Azure deployment failures were captured in structured payloads but surfaced in UI as generic stage text, forcing operators to open portal links for actionable details.
- Preventative rule: For external API failures, always emit a concise operator-facing diagnostic summary line plus key request identifiers directly into in-app logs.
- Detection signal: failed run cards only show generic messages (for example, "update failed") while `stage.error.details` contains richer provider error metadata.
- Enforcement (test/lint/checklist): add regression assertions that failed-stage logs include provider error summary + request/correlation IDs when available.

- Date: 2026-03-01
- Pattern: Route-level polling rerenders can make local page UI state (like drawer open/closed) flaky and break interaction tests.
- Preventative rule: Keep transient UI state that must survive route rerenders at the shell level (or URL state), not only inside route element components.
- Detection signal: Playwright shows repeated "element not stable/intercepts pointer events" while controls appear/disappear on periodic refresh ticks.
- Enforcement (test/lint/checklist): add a POM flow that opens the control surface, performs 2+ interactions, and starts the action while periodic refresh is active.

- Date: 2026-03-01
- Pattern: UI speed patch introduced non-standard local shims for shadcn primitives, causing visual drift and violating project component standards.
- Preventative rule: Prefer official shadcn primitives first; only use local fallback shims when a concrete blocker is documented and tracked.
- Detection signal: `src/components/ui/*` diverges from shadcn patterns while matching package dependencies (`vaul`, Radix primitives) are available in repo conventions.
- Enforcement (test/lint/checklist): for new UI primitives, confirm implementation source against shadcn docs and existing txero primitive patterns before merge.

- Date: 2026-03-01
- Pattern: Deployment form accumulated overlapping controls (`Target Scope` + specific target picker), which increased cognitive load without adding distinct operator value.
- Preventative rule: Prefer a single primary selector with optional refinement (group -> specific subset) instead of parallel mode selectors for the same outcome.
- Detection signal: users ask whether one of two adjacent controls is necessary or where to click for the same targeting behavior.
- Enforcement (test/lint/checklist): for control-plane UX updates, include one “operator path simplification” review checkpoint before closing the slice.

- Date: 2026-03-01
- Pattern: Fleet-level global filters and table-level filtering responsibilities were split across separate UI surfaces, making filtering feel inconsistent.
- Preventative rule: Keep filtering controls close to the data they affect (column filters in table) and avoid duplicate filter surfaces for the same dataset.
- Detection signal: users ask to move/merge filters into table columns or report uncertainty about where filtering is applied.
- Enforcement (test/lint/checklist): for table-heavy views, include an IA check ensuring filters are colocated with table columns unless a clear cross-view dependency exists.

- Date: 2026-03-01
- Pattern: Marketplace workflow messaging drifted back to inventory/import-first onboarding, conflicting with production intent that webhook events are the source of target registration.
- Preventative rule: Keep one explicit production onboarding path in commands/docs (event ingestion), and label inventory import as legacy fallback only.
- Detection signal: quick-start/checklist requires `import-targets` before testing onboarding events.
- Enforcement (test/lint/checklist): docs + make-help review each slice to ensure `marketplace-ingest-events` is primary and `azure-preflight` defaults to marketplace mode.

- Date: 2026-03-01
- Pattern: Teardown steps relied on manual/partial cleanup, leaving Entra app/SP artifacts and making demo resets non-repeatable.
- Preventative rule: Every provisioning workflow must ship with a symmetrical scripted teardown path (IaC resources + identity artifacts + local state).
- Detection signal: user must ask how to remove lingering identity objects after `pulumi destroy`.
- Enforcement (test/lint/checklist): include teardown commands in live-demo checklist and add make target for identity cleanup.

- Date: 2026-03-01
- Pattern: Azure managed Postgres provisioning can fail with transient `ServerIsBusy` when configuration/database operations run concurrently.
- Preventative rule: Explicitly serialize dependent Postgres control-plane operations (`dependsOn`) and set practical timeouts for configuration resources.
- Detection signal: Pulumi apply fails on `azure-native:dbforpostgresql:Configuration` with `ServerIsBusy` while server is being created/updated.
- Enforcement (test/lint/checklist): for managed DB IaC changes, require a build check plus at least one fresh-stack `pulumi up` smoke run.

- Date: 2026-03-01
- Pattern: Operator actions lacked immediate UI feedback (refresh looked inert, submit allowed invalid payloads, no global toast signaling), reducing trust in control-plane state.
- Preventative rule: Every mutation or refresh action must have visible pending/success/error feedback and client-side required-field validation.
- Detection signal: users ask whether an action did anything, or can click submit with incomplete required input.
- Enforcement (test/lint/checklist): require UX assertions for disabled-state gating and success/error feedback on Admin and Deployment primary actions.

- Date: 2026-03-01
- Pattern: Target release version was updated on deploy success, but target health state stayed at onboarding default (`registered`), causing fleet status drift from actual runtime outcome.
- Preventative rule: Any terminal deployment state change that mutates release/check-in must also mutate health state in the same persistence transaction.
- Detection signal: `last_deployed_release` changes while `health_status` remains `registered` after successful run.
- Enforcement (test/lint/checklist): add onboarding-to-deploy regression test asserting `registered -> healthy` transition on success.

- Date: 2026-03-01
- Pattern: Historical deployment cards rendered disabled action buttons for succeeded runs, creating unnecessary vertical clutter and obscuring primary navigation.
- Preventative rule: For terminal-success states, hide non-actionable controls and keep only the primary next action visible.
- Detection signal: succeeded run cards show disabled Resume/Retry controls even though no further action can be taken.
- Enforcement (test/lint/checklist): include a succeeded-run UI assertion in e2e coverage that Resume/Retry controls are absent.

- Date: 2026-03-01
- Pattern: Clone action initially triggered immediate execution, but operators expected a safe preflight step where configuration is reviewed and edited before launch.
- Preventative rule: Any "clone/re-run" affordance in deployment tooling should default to pre-populating controls, not auto-submitting, unless explicitly labeled "Run now".
- Detection signal: user feedback requests "open and pre-populate" rather than immediate run after clicking clone.

- Date: 2026-03-01
- Pattern: Backend files grew large without a line-count guardrail, while frontend already enforced a max-lines policy.
- Preventative rule: Mirror critical maintainability guardrails across backend and frontend lint workflows, including explicit file-size limits.
- Detection signal: modules exceed 750 lines with no automated lint failure in backend checks.
- Enforcement (test/lint/checklist): add `make lint-backend-file-size` and include it under `lint-backend`.
- Enforcement (test/lint/checklist): include an e2e assertion that clone opens controls with expected prefilled values and does not create a run until `Start Run` is clicked.

- Date: 2026-03-01
- Pattern: Introducing shadcn dropdown primitives without required theme tokens (`--popover`, `--popover-foreground`) caused transparent menus and apparent non-interactive actions.
- Preventative rule: Any new shadcn primitive using semantic color tokens must be validated against current CSS variable coverage before UI merge.
- Detection signal: overlay/menu surface appears transparent or unreadable despite rendering and test selectors existing.
- Enforcement (test/lint/checklist): add a UI token checklist step for newly added primitives and include one manual visual smoke pass for menu/popover surfaces.

- Date: 2026-03-01
- Pattern: Persisting selection highlight in historical tables can imply active state and confuse operators after navigation.
- Preventative rule: Use row highlighting only for real in-place selection workflows; avoid sticky highlight for navigation-only click targets.
- Detection signal: users report confusion that a previously opened row still looks selected.
- Enforcement (test/lint/checklist): include a UX pass for selection affordances on navigation tables and remove highlight if no multi-select/active-mode behavior exists.

- Date: 2026-03-01
- Pattern: Fast polling on a list view can invalidate ephemeral UI overlays (dropdown menus), making actions appear broken even when markup/tests pass.
- Preventative rule: When a transient overlay is open (menu, popover, drawer), suspend or debounce background polling that rerenders the owning list.
- Detection signal: action menu opens then disappears around the polling interval, and users report clicks not registering.
- Enforcement (test/lint/checklist): include one e2e check that actions menu remains open for at least one polling interval and action item click succeeds.

- Date: 2026-03-01
- Pattern: shadcn semantic classes can silently render as transparent when Tailwind theme keys are missing even if CSS variables exist.
- Preventative rule: For any newly introduced semantic utility classes (for example `bg-popover`, `focus:bg-accent`), verify both Tailwind theme mappings and CSS variables are present.
- Detection signal: component renders with visible structure but transparent surface/background and low-contrast text.
- Enforcement (test/lint/checklist): add a UI-theme checklist item requiring theme-key + CSS-variable parity for each semantic class family used by new primitives.

- Date: 2026-03-01
- Pattern: Admin workflows diverged from the established page IA (inline CRUD plus plain text lists), increasing cognitive switching vs Fleet/Deployments.
- Preventative rule: Keep control-plane pages consistent: top action CTA + drawer for mutations, tabular snapshot views in tabs for read-heavy data.
- Detection signal: users ask to move admin mutation forms into drawers and replace inline lists with datatables/tabs.
- Enforcement (test/lint/checklist): include an IA consistency review item before closing UI slices across Fleet/Deployments/Admin.

- Date: 2026-03-01
- Pattern: Marketplace-realistic demos drift when webhook ingress is simulated only by direct backend calls.
- Preventative rule: Keep a first-class webhook forwarder path (deployable Function App + replay script) so validation runs the same ingress boundary used in production.
- Detection signal: onboarding works only via direct `POST /api/v1/admin/onboarding/events` and there is no tested endpoint that mirrors marketplace callback shape.
- Enforcement (test/lint/checklist): live-demo checklist must include forwarder deploy + replay verification before signoff.

- Date: 2026-03-01
- Pattern: Network-restriction decisions for marketplace webhooks can over-index on service tags that may not exist for the specific producer.
- Preventative rule: Treat marketplace webhook ingress as auth-first (function key + token validation + gateway controls) and use service-tag allowlists only when a producer-specific tag is documented.
- Detection signal: architecture proposal assumes "marketplace service tag" without explicit doc confirmation.
- Enforcement (test/lint/checklist): security design review must include documented producer identity/auth controls and explicit source for any service-tag dependency.

- Date: 2026-03-01
- Pattern: Saying “targets configured” without explicitly separating Azure target infrastructure from DB registration state causes operator confusion and trust loss.
- Preventative rule: Every status summary must separately report (1) target surface provisioned in Azure and (2) targets registered in MAPPO via onboarding events.
- Detection signal: user asks whether setup is production-like after hearing stack targets exist while DB remains empty.
- Enforcement (test/lint/checklist): include dual-state checkpoints in runbooks (`az resource`/Pulumi outputs for infra, API/SQL counts for registration).

- Date: 2026-03-01
- Pattern: Retrying cloud runtime deploy with random default resource names causes orphan sprawl (multiple ACRs/workspaces) after partial failures.
- Preventative rule: Default runtime deploy resources must be deterministic/reused, and environment quota fallbacks should happen before attempting new environment creation.
- Detection signal: runtime RG accumulates many similarly prefixed registries/workspaces across consecutive retries.
- Enforcement (test/lint/checklist): add post-deploy hygiene check that runtime RG has one active ACR and no orphan Log Analytics workspaces.

- Date: 2026-03-01
- Pattern: Fixing one duplicated metadata field in target projections (for example `customer_name`) left other duplicated fields (`tenant_id`, `subscription_id`, `managed_app_id`, `tags`) drifting between target rows and registration rows.
- Preventative rule: When enforcing single-source-of-truth projection, audit and project all duplicated fields in one pass, and block partial field-by-field fixes.
- Detection signal: Admin registration table and Fleet table show different values for the same target after edits.
- Enforcement (test/lint/checklist): keep one regression test that tampers every duplicated target field and asserts list/read paths project all of them from registration data.

- Date: 2026-03-05
- Pattern: Domain records and repository/service seams used free-form `String` status values where DB-backed enums already exist, weakening type-safety and allowing invalid lifecycle values.
- Preventative rule: For any field backed by a Postgres enum, use the generated jOOQ enum type end-to-end (record models, repository signatures, service calls) instead of string literals.
- Detection signal: `rg -n "String\s+.*(status|mode|stage|scope|health|level|strategy|failure)" backend-java/src/main/java/com/mappo/controlplane/{model,service,repository}` returns matches that are not business text fields.
- Enforcement (test/lint/checklist): add enum-surface grep check to backend architecture lint and require `./mvnw -pl backend-java test` on enum-related refactors.

- Date: 2026-03-06
- Pattern: Promoting a new OpenAPI generator to source-of-truth without immediately regenerating and validating downstream clients leaves the frontend broken on schema names, field casing, and path parameters.
- Preventative rule: Any backend contract-generator change must include same-slice regeneration of checked-in clients plus frontend `typecheck`, `test`, and `build` before the slice can be called complete.
- Detection signal: generated schema names drift from existing aliases (`RunCreateRequest` vs `CreateRunRequest`, camelCase vs snake_case) and TypeScript errors cluster around path strings and property names.
- Enforcement (test/lint/checklist): require `./mvnw -pl backend-java verify`, `./mvnw -N exec:exec@frontend-client-gen`, `./mvnw -N exec:exec@frontend-typecheck`, `./mvnw -N exec:exec@frontend-test`, and `./mvnw -N exec:exec@frontend-build` after contract changes.

- Date: 2026-03-06
- Pattern: Keeping backend runtime JSON in snake_case while Springdoc exports camelCase creates a silent contract split where integration tests pass but generated clients target a different API than production serves.
- Preventative rule: Runtime JSON naming, OpenAPI export, and integration-test assertions must use the same casing convention; do not preserve serializer compatibility modes once Java OpenAPI is the contract source of truth.
- Detection signal: MockMvc assertions use snake_case paths while `backend-java/target/openapi/openapi.json` exposes camelCase properties for the same DTOs.
- Enforcement (test/lint/checklist): after any serializer or DTO rename, verify one backend integration test response body and the exported OpenAPI schema use identical property names before regenerating frontend clients.

- Date: 2026-03-06
- Pattern: Integration tests that hit authenticated webhook-style endpoints can accidentally depend on developer shell env such as `MAPPO_MARKETPLACE_INGEST_TOKEN`, causing nondeterministic `401` failures in CI or local `clean install`.
- Preventative rule: Shared integration-test bootstrap must explicitly neutralize or set all auth-related properties needed for deterministic test behavior; never inherit webhook auth state from ambient env unless the test is specifically exercising auth.
- Detection signal: MockMvc onboarding/forwarder POST tests fail with `401` only on machines where marketplace ingest token env vars are exported.
- Enforcement (test/lint/checklist): base integration test setup should override auth-sensitive properties (`MAPPO_MARKETPLACE_INGEST_TOKEN`, similar secrets) and auth-specific tests must set them explicitly per test class.

- Date: 2026-03-06
- Pattern: Integration tests asserted full warning/error strings that vary with runtime feature flags (`azureExecutionEnabled`), causing `clean install` to pass on one machine and fail on another.
- Preventative rule: For environment-sensitive messages, assert the invariant contract fragments or explicitly pin the feature flag in test configuration; do not assert full strings unless the test controls every input that shapes them.
- Detection signal: the same test flips between two semantically valid warning messages depending on local env or app config.
- Enforcement (test/lint/checklist): when a response includes warnings derived from runtime config, either override that config in the test class or assert only stable substrings that define the contract.
