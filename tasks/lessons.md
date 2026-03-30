# MAPPO Lessons Log

Purpose: capture recurring correction patterns and preventative guardrails that still apply to the current Java/Maven codebase.

## Template
- Date:
- Pattern:
- Preventative rule:
- Detection signal:
- Enforcement (test/lint/checklist):

## Entries
- Date: 2026-03-14
- Pattern: Azure/Entra terminology confusion (App registration vs Enterprise app vs Managed Application resource) leads to incorrect cleanup and support assumptions.
- Preventative rule: In every identity-related change, record object type + tenant + object ID + owning system (ADO/GitHub/MAPPO/Azure resource) in one place before deleting or updating anything.
- Detection signal: statements such as “managed app in Entra” where the object is actually a service principal/app registration.
- Enforcement (test/lint/checklist): require a pre-change identity inventory note in `docs/` for auth/integration work and verify references in cleanup scripts.

- Date: 2026-03-14
- Pattern: Script-first onboarding hides source-of-truth changes from operators and creates architecture drift between UI state and operational reality.
- Preventative rule: Any script that mutates project config, target registration, or release-ingest wiring must either be temporary with a planned UI replacement milestone or blocked from production runbooks.
- Detection signal: operators cannot explain how a project/target was configured without inspecting shell history.
- Enforcement (test/lint/checklist): every setup mutation path must map to an explicit UI/API endpoint and audit record before the slice is marked complete.

- Date: 2026-03-14
- Pattern: Updating historical Flyway migrations to reflect new intent causes false confidence because existing environments will not apply those edits.
- Preventative rule: Never change behavior in already-applied migrations; introduce a new migration version for live environments and update baseline files only for fresh installs with explicit note.
- Detection signal: a schema/config change appears in `V1`/older migration but is missing in live DB `flyway_schema_history`.
- Enforcement (test/lint/checklist): verify migration version progression against live `flyway_schema_history` whenever schema/config behavior changes.

- Date: 2026-03-14
- Pattern: Multi-tenant demos degrade into confusion when provider/customer boundaries are implicit in scripts and ad hoc docs.
- Preventative rule: Keep provider vs customer-mimic boundaries explicit in project setup UX, runbooks, and audit snapshots using concrete tenant/subscription IDs.
- Detection signal: operators ask “which tenant owns this identity/resource?” during normal setup/deploy flows.
- Enforcement (test/lint/checklist): each sprint touching identity/integration must update an environment topology doc and include tenant/subscription ownership annotations.

- Date: 2026-03-06
- Pattern: Command-surface cutovers regress when active docs keep referencing removed workflows after the underlying build/deploy model changes.
- Preventative rule: When replacing a primary workflow surface, rewrite the active README/runbooks/checklists in the same slice and add a negative docs check for the retired command form.
- Detection signal: active docs mention removed command forms such as `make ...` or deleted backend paths.
- Enforcement (test/lint/checklist): `./mvnw -pl tooling exec:java@docs-consistency-check` must fail if active docs reference removed workflow surfaces.

- Date: 2026-03-06
- Pattern: Contract changes can look complete in backend code while downstream generated clients and frontend types are still stale.
- Preventative rule: Any backend contract change must include client regeneration plus frontend typecheck, test, and build in the same slice.
- Detection signal: DTO names/property casing change in backend, but frontend breaks on generated schema names or path signatures.
- Enforcement (test/lint/checklist): run backend verify plus the frontend Maven lifecycle before closing contract work.

- Date: 2026-03-06
- Pattern: Runtime JSON casing and exported OpenAPI can silently diverge if serializer settings and generated contract assumptions are not aligned.
- Preventative rule: Runtime JSON naming, OpenAPI export, and integration-test assertions must use one casing convention.
- Detection signal: integration tests assert different property names than the exported OpenAPI artifact.
- Enforcement (test/lint/checklist): compare one real integration response against the generated OpenAPI before regenerating clients.

- Date: 2026-03-06
- Pattern: Integration tests become nondeterministic when they inherit auth-related environment variables from the developer shell.
- Preventative rule: Shared integration-test bootstrap must explicitly neutralize or set auth-sensitive properties.
- Detection signal: the same MockMvc test flips between `200` and `401` depending on local env.
- Enforcement (test/lint/checklist): base test configuration must override ambient ingest/auth env vars unless a test is explicitly exercising auth.

- Date: 2026-03-06
- Pattern: Broadly neutralizing Azure execution properties in the shared integration-test base breaks tests that intentionally opt into Azure-enabled execution with explicit test properties.
- Preventative rule: Shared test bootstrap should only neutralize truly global auth state; tests that need simulator mode must pin simulator mode themselves, and tests that need Azure mode must opt in explicitly.
- Detection signal: fixing one env-sensitive test causes another execution-path test to stop exercising the intended branch.
- Enforcement (test/lint/checklist): run at least one simulator-path test and one Azure-enabled execution test together under forced `MAPPO_AZURE_*` env vars.

- Date: 2026-03-06
- Pattern: Frameworks emit avoidable warnings when behavior is left implicit, which trains people to ignore build output.
- Preventative rule: If a feature is intentionally enabled, set it explicitly; if a library emits known non-actionable support warnings, suppress the narrow logger rather than accepting noisy builds globally.
- Detection signal: repeated warning lines appear in every green test/build run without indicating a real defect.
- Enforcement (test/lint/checklist): after enabling a new framework or tool, run one representative test/build command and trim any warning that is purely configuration noise.

- Date: 2026-03-06
- Pattern: Failure-path unit tests that intentionally trigger logged exceptions can leave green builds looking broken.
- Preventative rule: If a test is validating a handled error path, mute the test logger unless the log output itself is the assertion target.
- Detection signal: green unit tests still print `SEVERE` stack traces for expected failures.
- Enforcement (test/lint/checklist): after adding negative-path tests, run the module test task once and trim expected-error log noise.

- Date: 2026-03-06
- Pattern: Tests that assert full environment-sensitive warning strings fail on one machine and pass on another.
- Preventative rule: Assert stable contract fragments unless the test explicitly controls every input that shapes the full message.
- Detection signal: warning/error assertions fail only when runtime feature flags or env differ.
- Enforcement (test/lint/checklist): prefer substring/assertion-of-invariants for env-derived warnings.

- Date: 2026-03-06
- Pattern: Live Azure execution wired directly into orchestration becomes hard to test and pushes the repo back toward simulator-only confidence.
- Preventative rule: Keep cloud execution behind narrow strategy interfaces so orchestration can be tested with stubs.
- Detection signal: a new execution branch can only be validated with live Azure credentials.
- Enforcement (test/lint/checklist): every new cloud execution mode needs one orchestration-level test that uses a stubbed strategy bean.

- Date: 2026-03-06
- Pattern: Maven lifecycle ordering is easy to wire incorrectly when a new child module installs its own toolchain.
- Preventative rule: Tool bootstrap must happen in `initialize` or earlier before any source-generation or compile-time executions invoke that toolchain.
- Detection signal: `clean install` fails because a later phase installs Node/npm after an earlier phase already tried to use it.
- Enforcement (test/lint/checklist): after adding phase-bound plugin executions, run the full requested lifecycle (`./mvnw clean install`).

- Date: 2026-03-06
- Pattern: Plugin-managed toolchains can dirty the repo if they install into the source tree by default.
- Preventative rule: Maven-installed toolchains and generated caches should live under module `target/` directories unless there is a strong reason otherwise.
- Detection signal: `git status --short` shows new toolchain directories after a green build.
- Enforcement (test/lint/checklist): after adding a new plugin-managed toolchain, verify the build leaves no untracked toolchain directories outside `target/`.

- Date: 2026-03-06
- Pattern: Shell wrappers around Maven `exec:java` fail when they invoke the goal from the reactor root instead of the owning module.
- Preventative rule: Wrapper scripts that call Java tooling commands should target the owning module with `-f <module>/pom.xml`, not rely on the root aggregator to resolve the runtime classpath.
- Detection signal: `exec-maven-plugin` reports it cannot execute the Java main class even though the module compiles.
- Enforcement (test/lint/checklist): whenever a shell wrapper delegates into a Maven module, run the wrapper once with `--help` or `--dry-run`.

- Date: 2026-03-07
- Pattern: Passing raw JSON through Maven `-Dexec.args` is brittle and breaks as soon as shell escaping and plugin parsing disagree.
- Preventative rule: For tooling commands that accept arbitrary JSON or other structured payloads, pass arguments via an args file and decode them inside Java instead of relying on command-line escaping.
- Detection signal: shell wrappers work for simple flags but fail on JSON with quotes/backslashes/newlines.
- Enforcement (test/lint/checklist): keep at least one smoke test that invokes the shell bridge with a JSON-bearing argument.

- Date: 2026-03-07
- Pattern: Parent POM deploy configuration can look correct while child modules still execute the default Maven deploy goal.
- Preventative rule: If the repo repurposes `deploy` as an operational workflow, explicitly neutralize `maven-deploy-plugin` in each child module or prove inheritance with an effective-POM check.
- Detection signal: `mvn deploy` still fails in an early child module with `repository element was not specified`.
- Enforcement (test/lint/checklist): after changing deploy semantics, run a full root `deploy` dry pass and inspect the first failing child before assuming the parent POM is sufficient.

- Date: 2026-03-07
- Pattern: Spring Boot 4 on this stack wires the JSON mapper bean from the `tools.jackson` package, so new services that inject `com.fasterxml.jackson.databind.ObjectMapper` fail at context startup even though JSON annotations still compile.
- Preventative rule: For Spring-managed JSON parsing in the backend, reuse the existing app-level mapper/utility types instead of assuming the classic `com.fasterxml` bean is present.
- Detection signal: context startup fails with `No qualifying bean of type 'com.fasterxml.jackson.databind.ObjectMapper' available`.
- Enforcement (test/lint/checklist): after adding any JSON-heavy service bean, run at least one Spring integration test before moving on to frontend wiring.

- Date: 2026-03-06
- Pattern: Persistence fixes that only address one duplicated field leave the real single-source-of-truth problem unresolved.
- Preventative rule: When fixing duplicated data ownership, audit and correct the full duplicated field set in one slice.
- Detection signal: Fleet and Admin views show different values for the same registered target after an edit.
- Enforcement (test/lint/checklist): keep one regression test that tampers duplicated registration-owned fields and verifies projection consistency.

- Date: 2026-03-08
- Pattern: Azure control-plane features that look straightforward in SDK examples can still fail at live runtime because scope rules and SDK serialization quirks differ from the intended architecture.
- Preventative rule: Treat every new Azure execution mode as unproven until it completes one live end-to-end rollout, and document the exact scope/auth/SDK constraints discovered there.
- Detection signal: integration tests pass, but Azure returns schema/scope errors such as missing `denySettings`, invalid `template: null`, or authorization failures at subscription-root scope.
- Enforcement (test/lint/checklist): after implementing an Azure executor, run one real deployment against the hosted demo targets and write the live findings back into the runbook/plan before calling the milestone complete.

- Date: 2026-03-08
- Pattern: Operator-facing Azure failures become useless when the backend stores SDK object `toString()` output or only the top-level `DeploymentFailed` wrapper message.
- Preventative rule: Normalize Azure deployment failures down to the most specific failed operation or failed resource message, and always preserve correlation, deployment, operation, and resource identifiers alongside the summary.
- Detection signal: the UI shows values like `DefaultErrorResponseError@...` or only `At least one resource deployment operation failed` with no actionable context.
- Enforcement (test/lint/checklist): failure-path tests should assert that Azure error summaries include the deepest available message plus deployment metadata instead of raw SDK object text.

- Date: 2026-03-08
- Pattern: Azure Deployment Stack create/update failures can return shallow SDK errors even when the stack resource itself contains a deeper failed-resource message.
- Preventative rule: After any stack failure, read back the stack resource and prefer its failed-resource/operation detail over the original SDK wrapper before finalizing operator-visible errors.
- Detection signal: the initial exception only says `DeploymentFailed` or prints a generic SDK error object, but `az stack group show` reveals a specific resource-level reason such as image-pull authorization failure.
- Enforcement (test/lint/checklist): every live stack failure investigation should compare the immediate SDK error to a follow-up stack read and keep the richer path in the executor.

- Date: 2026-03-08
- Pattern: Deployment Stack updates do not expose native `what-if`, so operators lose preview confidence unless MAPPO provides an explicit ARM-level approximation.
- Preventative rule: For stack-backed releases, implement preview as ARM `what-if` against the exact resolved template artifact and parameters, and label it clearly as an approximation of the next stack update rather than a full stack-semantic preview.
- Detection signal: operators ask for rollout preview and the only answer is “Deployment Stack what-if doesn’t exist.”
- Enforcement (test/lint/checklist): any new stack-backed execution surface should include a preview endpoint or UI action plus a caveat explaining the scope of the preview.

- Date: 2026-03-08
- Pattern: Dashboard KPIs that cannot be acted on or reset train operators to ignore them.
- Preventative rule: Only surface top-level counters that support a clear operational action or state transition; otherwise remove them and keep the detail in the list/detail views.
- Detection signal: users ask what a badge/counter means or why it never clears.
- Enforcement (test/lint/checklist): when adding a top-level KPI, document the operator action it drives and remove it if there is no answer.

- Date: 2026-03-08
- Pattern: Stable external integration URLs matter more than the specific Azure edge product; subscription constraints can invalidate an otherwise cleaner design.
- Preventative rule: Choose the simplest stable public URL strategy the current account can actually support, and fall back to direct ACA custom domains when Azure Front Door is unavailable on the subscription.
- Detection signal: infrastructure rollout fails with subscription-level SKU restrictions even though the desired URL strategy is sound in principle.
- Enforcement (test/lint/checklist): verify edge-service subscription eligibility before committing the public URL design, and document the fallback path in the runbook.

- Date: 2026-03-08
- Pattern: Real webhook transport can be healthy while operators still cannot prove what happened if a delivery results in a no-op ingest.
- Preventative rule: Persist webhook delivery audit records with repo, ref, delivery id, and created/skipped/failed outcome instead of relying on secondary effects like release-count changes.
- Detection signal: a push reaches the hosted endpoint, but there is no UI/API surface showing whether MAPPO fetched the manifest and skipped or failed ingest.
- Enforcement (test/lint/checklist): every external webhook integration should expose an operator-visible delivery log before it is considered production-ready.

- Date: 2026-03-09
- Pattern: A dashboard label can become misleading when the UI meaning changes faster than the underlying data model.
- Preventative rule: Do not label a field as runtime health unless it comes from an actual health probe or an explicitly refreshed runtime check; historical deployment state must stay in a separate field.
- Detection signal: the service endpoint is healthy, but Fleet still shows a degraded runtime badge because the stored health field was inherited from older deployment semantics.
- Enforcement (test/lint/checklist): when splitting operational concepts in the UI, verify the backing data source represents the same concept or rename the field until the data model catches up.

- Date: 2026-03-09
- Pattern: Internal tracing identifiers confuse operators when surfaced in the normal run timeline alongside actionable Azure request metadata.
- Preventative rule: Keep MAPPO-only correlation keys in storage/debug views, but reserve the default operator UI for Azure-facing request, correlation, deployment, and resource identifiers.
- Detection signal: users ask what `corr-run-...` means or whether they should care about it during troubleshooting.
- Enforcement (test/lint/checklist): operator-facing run-detail tests should assert Azure error metadata remains visible while internal stage correlation labels stay hidden.

- Date: 2026-03-09
- Pattern: Page-level action buttons and sticky inline result banners create ambiguity when the action really belongs to one sub-tab and the feedback is transient.
- Preventative rule: Place refresh actions inside the data context they mutate, and use Sonner-style transient notifications for short-lived action outcomes instead of persistent summary banners.
- Detection signal: operators ask what a button is refreshing, or a one-time result message remains on screen after the action is complete and competes with durable tables/logs.
- Enforcement (test/lint/checklist): for every new operator action, decide whether the feedback is durable or transient; if transient, send it to the toast system and keep the page reserved for persistent state.

- Date: 2026-03-22
- Pattern: Operator walkthroughs stall when setup forms expose internal contract keys or legacy fallback modes (for example literal secrets, adapter keys, payload-mapping internals).
- Preventative rule: Before shipping a setup screen, classify every field as required/optional/auto/internal/legacy and remove auto/internal + legacy fields from operator input.
- Detection signal: the first-time operator asks “where do I get this value?” or “why does this field exist?” for fields that are defaults or backend wiring details.
- Enforcement (test/lint/checklist): maintain `/docs/operator-field-inventory.md` and require UX sign-off that every editable field has an explicit source and purpose tooltip.

- Date: 2026-03-22
- Pattern: Deployment-driver discovery fails in walkthroughs when PAT auth depends on backend env vars that are not discoverable/configurable from the admin UX.
- Preventative rule: Credential source must be admin-managed and linkable from project config; project tabs should consume selected connection metadata, not rely on hidden runtime defaults.
- Detection signal: operators hit “PAT could not be resolved” while all visible project fields appear complete.
- Enforcement (test/lint/checklist): for any external driver discovery action, require a visible admin-configured credential source and show the resolved reference in project config before allowing discovery.

- Date: 2026-03-22
- Pattern: JSONB config values appeared to “not persist” because the query layer re-serialized JSON text before parsing, collapsing valid objects to `{}` in API responses.
- Preventative rule: For JSONB columns read as text (`jsonbColumn.data()`), parse with `readMap` directly; reserve `toMap` for object instances, not raw JSON strings.
- Detection signal: DB row contains expected JSON keys while corresponding API response always returns empty config objects.
- Enforcement (test/lint/checklist): add integration assertions that patching JSONB config fields returns the same key/value in response payloads.

- Date: 2026-03-09
- Pattern: Execution strategy controls in the API/UI are misleading if the backend stores them but still runs synchronously and serially.
- Preventative rule: When a rollout control such as `all_at_once`, `waves`, or `concurrency` is exposed to operators, backend execution and integration tests must prove the semantics are actually honored.
- Detection signal: the run-creation request blocks until completion, the deployment drawer never closes, or “all at once” rollouts visibly execute one target at a time.
- Enforcement (test/lint/checklist): keep integration coverage that asserts immediate `running` run creation, bounded parallel execution, and wave-order execution before shipping rollout UX changes.

- Date: 2026-03-09
- Pattern: Large operator tables become hard to scale and impossible to stream cleanly when the backend still returns full snapshots and the frontend layers client-only filters on top.
- Preventative rule: Introduce backend pagination/query contracts before adding live-update mechanisms like SSE, and make the generated OpenAPI/client surface the source of truth for those collection shapes.
- Detection signal: polling endpoints return full lists, table state cannot be linked to server-side filters, or SSE design discussions stall because there is no stable paginated contract to invalidate/refetch.
- Enforcement (test/lint/checklist): every new high-volume table should land with backend pagination/filtering tests plus frontend generated-type verification in the same slice.

- Date: 2026-03-09
- Pattern: Partial pagination rollouts leave the app shell juggling incompatible fetch models and delay the SSE work they were supposed to enable.
- Preventative rule: When a dashboard area starts the move to backend pagination, finish the entire operator surface cluster in the same program phase so state management can converge on one model before introducing live updates.
- Detection signal: sibling tables in the same UI area mix paginated server queries with legacy full-snapshot refreshes.
- Enforcement (test/lint/checklist): before starting SSE work, confirm each high-volume operator table in scope has backend pagination, generated-client coverage, and shared pagination controls.

- Date: 2026-03-09
- Pattern: Generated OpenAPI can look healthy while the frontend quietly drifts if collection query shapes are duplicated by hand in API wrappers.
- Preventative rule: For paginated/filterable collection endpoints, export the query shape once from Springdoc and have frontend wrappers consume the generated operation query type directly.
- Detection signal: backend query parameters change, but only handwritten wrapper types need updates while the generated schema remains unused.
- Enforcement (test/lint/checklist): when adding or changing a collection query contract, update the controller/query DTOs, regenerate the client, and fail tests if the exported OpenAPI drops `page`/`size` or key filters.

- Date: 2026-03-09
- Pattern: `@Qualifier` on a Lombok-generated final field is not enough when Spring has multiple beans of the same type; constructor injection can still become ambiguous.
- Preventative rule: When a bean type has multiple candidates, put the qualifier on the explicit constructor parameter or the `@Bean` injection point instead of assuming field-level Lombok annotations will propagate correctly.
- Detection signal: context startup fails with `expected single matching bean but found 2` even though the field carries `@Qualifier`.
- Enforcement (test/lint/checklist): after adding a second bean of a shared framework type like `Executor`, run at least one Spring integration test before considering the slice complete.

- Date: 2026-03-09
- Pattern: Date-time assertions become brittle when tests compare exact serialized strings instead of the underlying instant.
- Preventative rule: For API fields backed by `OffsetDateTime`, assert semantic equality on the parsed instant unless the contract explicitly guarantees one offset representation.
- Detection signal: the same timestamp serializes as `Z` in one environment and as a local-offset string in another, while representing the same moment.
- Enforcement (test/lint/checklist): when adding date-time fields to contract tests, parse the returned value and compare instants rather than hardcoded timezone-specific strings.

- Date: 2026-03-09
- Pattern: OpenAPI regressions can be masked by brittle JSONPath/content assertions that overfit field ordering instead of validating the actual contract semantics.
- Preventative rule: For Springdoc contract tests, assert parameter presence, required schema markers, and key enum/value fragments without depending on exact object serialization order.
- Detection signal: the generated OpenAPI clearly contains the expected enum/query shape, but the regression test still fails on nested JSONPath indexing or exact raw-JSON substring matches.
- Enforcement (test/lint/checklist): whenever a contract test fails unexpectedly, inspect the generated `backend/target/openapi/openapi.json` before changing the implementation and tighten the test around semantic contract guarantees only.

- Date: 2026-03-09
- Pattern: Frontend route shells can remain deceptively heavy even after feature work is done if every major page is imported eagerly into the app root.
- Preventative rule: When a dashboard grows past a few major route surfaces, lazy-load route modules before accepting bundle-size warnings as normal.
- Detection signal: Vite warns about a large main chunk and the root app imports every page component directly.
- Enforcement (test/lint/checklist): after adding a major route or panel cluster, run a production build once and split obvious route boundaries if the main chunk regresses badly.

- Date: 2026-03-09
- Pattern: Pagination alone does not prevent operator tables from degrading if completed runs and audit rows accumulate forever.
- Preventative rule: Add explicit retention and hot-path indexes as soon as paginated operator tables become the default access path for run/admin history.
- Detection signal: paginated queries rely on descending timestamp scans over ever-growing terminal/audit tables with no cleanup policy.
- Enforcement (test/lint/checklist): when promoting a table to backend pagination, verify it has a retention story and at least one supporting index for its dominant filter/order path.

- Date: 2026-03-10
- Pattern: Cleanup slices often remove deprecated endpoints while leaving contract tests asserting that still-supported sibling routes disappeared too.
- Preventative rule: When deleting deprecated routes, update contract tests to assert the exact removed verb/path combination rather than broad path absence if any current operation still lives under that path.
- Detection signal: OpenAPI regression tests fail after cleanup because a surviving POST or GET operation still exists under a partially deprecated path prefix.
- Enforcement (test/lint/checklist): after deleting compatibility endpoints, inspect the generated OpenAPI and verify tests assert per-operation removal where needed.

- Date: 2026-03-11
- Pattern: Architecture docs drift quickly after an implementation seam changes shape, especially when a temporary provider/registry design is later replaced by persisted configuration.
- Preventative rule: When a design milestone changes the real control path or persistence model, update the architecture note and roadmap in the same slice instead of leaving the intermediate story behind.
- Detection signal: the code no longer contains classes named in the architecture doc, or the roadmap still lists a completed migration as open.
- Enforcement (test/lint/checklist): after landing a structural milestone, grep the docs/roadmap for the replaced classes or old design phrases before closing the slice.

- Date: 2026-03-22
- Pattern: "Flexible" fallback paths in credentials/config wiring keep confusing operators because the UI shows one model while runtime still accepts hidden legacy inputs.
- Preventative rule: For setup flows still in active development, prefer one strict contract with explicit required fields and remove fallback wiring in the same slice.
- Detection signal: operator walkthrough blocks on a field that appears optional in UI but is actually inferred from hidden legacy rules, or vice-versa.
- Enforcement (test/lint/checklist): when enforcing a new contract, add migration/backfill for existing rows, delete old resolver paths, and add validation checks that reject legacy values.
