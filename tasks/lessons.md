# MAPPO Lessons Log

Purpose: capture recurring correction patterns and preventative guardrails that still apply to the current Java/Maven codebase.

## Template
- Date:
- Pattern:
- Preventative rule:
- Detection signal:
- Enforcement (test/lint/checklist):

## Entries
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
