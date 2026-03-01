# MAPPO Task Plan

Date: 2026-02-26
Owner: Codex

## Scope
Phase 4 database foundation:
- Replace SQLite backend persistence with Postgres.
- Use Flyway for schema migrations.
- Use sqlacodegen workflow for generated ORM models (same practice as TXero).
- Keep existing API/UI contract and seeded 10-tenant demo behavior stable.

## Plan
- [x] Align local automation with TXero DB workflow (`db-migrate`, `db-validate`, `db-info`, `db-clean`, `db-reset`, `models-gen`).
- [x] Complete Flyway baseline + generated ORM model output committed in `backend/app/db/generated/models.py`.
- [x] Refactor control-plane store to Postgres persistence using SQLAlchemy sessions and generated models.
- [x] Replace SQLite settings/wiring with `DATABASE_URL`/`MAPPO_DATABASE_URL` configuration.
- [x] Update demo/ops scripts to use Postgres-backed store.
- [x] Adapt backend tests to Postgres workflow and keep deterministic seed/reset behavior.
- [x] Run verification suite and capture outcomes.

## Verification Commands
- [x] `make db-migrate`
- [x] `make models-gen`
- [x] `make demo-reset`
- [x] `make retention-prune RETENTION_DAYS=90`
- [x] `make lint`
- [x] `make typecheck`
- [x] `make test`
- [x] `make phase1-gate-full`

## Results Log
- 2026-02-26: Started Phase 4 migration by adding Flyway scaffold and Postgres dependencies.
- 2026-02-26: Generated SQLAlchemy models from Flyway schema at `backend/app/db/generated/models.py`.
- 2026-02-26: Reworked `ControlPlaneStore` persistence to Postgres JSONB via SQLAlchemy sessions.
- 2026-02-26: Added DB workflow Make targets (`db-migrate`, `db-validate`, `db-info`, `db-clean`, `db-reset`, `models-gen`).
- 2026-02-26: Updated scripts/settings/tests from SQLite path config to `MAPPO_DATABASE_URL` / `DATABASE_URL`.
- 2026-02-26: Verification green (`db-migrate`, `models-gen`, `demo-reset`, `retention-prune`, `lint`, `typecheck`, `test`, `phase1-gate-full`).

## Review Notes
- Phase 4 preserves existing domain payload contract by storing canonical JSON payloads in Postgres JSONB tables while moving schema lifecycle to Flyway.

---

## Scope (Next Slice)
Phase 4 extension: API contract automation + local stack orchestration.
- Add TXero-style OpenAPI generation and frontend client generation flow.
- Wire frontend API layer to generated OpenAPI client/types.
- Add docker-compose stack with non-conflicting default ports vs TXero.

## Plan (Next Slice)
- [x] Add backend OpenAPI generation script/artifact path (`backend/openapi/openapi.json`).
- [x] Add frontend client generation tooling (`openapi-typescript` + generated schema output).
- [x] Replace handwritten frontend API contract types with generated schema types.
- [x] Add docker-compose stack for Postgres + Flyway migrate + backend + frontend.
- [x] Set mappo stack defaults to non-conflicting ports (`8010` API, `5174` UI, `5433` Postgres host).
- [x] Update Make targets/docs for new commands and workflow.
- [x] Run verification suite and capture outcomes.

## Verification Commands (Next Slice)
- [x] `make openapi`
- [x] `make client-gen`
- [x] `make dev-up`
- [x] `make dev-down`
- [x] `make lint`
- [x] `make typecheck`
- [x] `make test`
- [x] `make phase1-gate-full`

## Results Log (Next Slice)
- 2026-02-26: Added backend OpenAPI generator script and checked-in artifact path at `backend/openapi/openapi.json`.
- 2026-02-26: Added `openapi` and `client-gen` commands to the Make workflow; phase fast gate now enforces client generation.
- 2026-02-26: Introduced generated frontend API schema (`frontend/src/lib/api/generated/schema.ts`) and `openapi-fetch` client wiring.
- 2026-02-26: Replaced handwritten TS API contract shapes with generated OpenAPI schema aliases in `frontend/src/lib/types.ts`.
- 2026-02-26: Added docker-compose stack at `infra/docker-compose.yml` with non-conflicting host ports (`8010`, `5174`, `5433`).
- 2026-02-26: Verified compose file renders successfully (`docker compose -f infra/docker-compose.yml config`).
- 2026-02-26: Verified compose stack boot/shutdown (`make dev-up`, `make dev-down`) after setting explicit compose project name `mappo`.

---

## Scope (Current Slice)
Run UX + control-plane correctness:
- Ensure Fleet Targets reflects latest successful deployed release after runs complete.
- Support explicit target-scoped deployment starts from UI (specific targets, not only tag filters).
- Add CodeDeploy-style overall progress visualization for long-running runs.

## Plan (Current Slice)
- [x] Add backend state update on per-target success (`last_deployed_release` sync).
- [x] Add backend tests covering fleet version update + target-id scoping.
- [x] Extend frontend start-run form to pick specific targets and send `target_ids`.
- [x] Add overall run progress bar + counts in deployment run detail/list area.
- [x] Regenerate OpenAPI/client artifacts if contract shape changes.
- [x] Run verification suite and capture outcomes.

## Verification Commands (Current Slice)
- [x] `make openapi`
- [x] `make client-gen`
- [x] `make lint`
- [x] `make typecheck`
- [x] `make test`
- [x] `make phase1-gate-full`

## Results Log (Current Slice)
- 2026-02-26: Began implementation for fleet-version sync, target-scoped runs, and run progress UX.
- 2026-02-26: Updated backend execution finalization to persist target `last_deployed_release` + `last_check_in_at` on successful deploys.
- 2026-02-26: Added backend tests for target-id run scoping and fleet version synchronization after successful single-target runs.
- 2026-02-26: Extended Start Deployment form with target scope mode (`current fleet filter` vs `specific targets`) and multi-select target picker.
- 2026-02-26: Added run-level progress bars and completion metrics in both run list cards and run detail panel.
- 2026-02-26: Verified `openapi`, `client-gen`, `lint`, `typecheck`, `test`, and `phase1-gate-full` all pass (frontend lint retains prior 2 warnings in shadcn ui primitives).

---

## Scope (UX Slice)
Information architecture + terminology:
- Split UI into two top-level screens: Fleet and Deployments.
- Keep fleet/target monitoring separate from deployment execution controls.
- Replace end-user "ring" wording with "target group" labels.

## Plan (UX Slice)
- [x] Add top-level Fleet/Deployments screen switch and move panels accordingly.
- [x] Update fleet/deployment forms and table labels from ring to target group.
- [x] Update frontend tests for the new view behavior.
- [x] Run frontend/backend verification and capture outcomes.

## Verification Commands (UX Slice)
- [x] `make lint`
- [x] `make typecheck`
- [x] `make test`

## Results Log (UX Slice)
- 2026-02-26: Began UI split + terminology cleanup per product direction.
- 2026-02-26: Split UI into top-level Fleet and Deployments screens with explicit screen toggle controls.
- 2026-02-26: Moved deployment execution controls (start run, releases, run list, run detail) to Deployments screen; Fleet screen now focuses on target state only.
- 2026-02-26: Replaced user-facing "ring" wording with "target group" labels in filters/table/form strategy text while preserving backend tag key compatibility.
- 2026-02-26: Updated `App` UI test to validate Fleet default view and Deployments screen transition.
- 2026-02-26: Verified `make lint`, `make typecheck`, and `make test` pass (same existing 2 frontend lint warnings in shadcn ui primitives).

---

## Scope (UI Refinement Slice)
Deployment UI simplification and action gating:
- Remove confusing release-selection surfaces (panel + dropdown).
- Disable run actions when they do not apply (e.g., resume/retry on fully succeeded runs).
- Remove user-facing "Waves" wording in strategy labels.

## Plan (UI Refinement Slice)
- [x] Remove release list panel and release dropdown; use latest release automatically.
- [x] Add run-action enable/disable rules for Resume and Retry Failed.
- [x] Update strategy option labels to avoid "Waves" wording.
- [x] Run frontend/backend verification and capture outcomes.

## Verification Commands (UI Refinement Slice)
- [x] `make lint`
- [x] `make typecheck`
- [x] `make test`

## Results Log (UI Refinement Slice)
- 2026-02-26: Began deployment UI simplification and run-action gating updates.
- 2026-02-26: Removed release-selection surfaces from Deployments UI (no release panel, no release dropdown); run start now targets latest available release.
- 2026-02-26: Added run action gating so `Resume` and `Retry Failed` buttons are disabled when not applicable (including fully succeeded runs).
- 2026-02-26: Reworded strategy option label from "Waves..." to "Grouped rollout (target group order)" and mapped run detail strategy text to user-friendly labels.
- 2026-02-26: Verified `make lint`, `make typecheck`, and `make test` pass (same existing 2 frontend lint warnings in shadcn ui primitives).

---

## Scope (UI Follow-up Slice)
Deployment selection clarity:
- Show the target members for the selected target group in Deployments (read-only list).
- Restore explicit release-version selection in a simpler UI shape (without the old side panel).

## Plan (UI Follow-up Slice)
- [x] Add a release-version picker in the Start Deployment Run card.
- [x] Add a read-only target-group member preview list when target-scope is filtered/group-based.
- [x] Run frontend/backend verification and capture outcomes.

## Verification Commands (UI Follow-up Slice)
- [x] `make lint`
- [x] `make typecheck`
- [x] `make test`

## Results Log (UI Follow-up Slice)
- 2026-02-26: Began release selection + target-group membership preview refinements.
- 2026-02-26: Added a release-version selector in Start Deployment Run (no side release panel), finalized as a standard dropdown.
- 2026-02-26: Added a read-only target-group membership list (checked + disabled) when deployment scope is group/filter-based.
- 2026-02-26: Verified `make lint`, `make typecheck`, and `make test` pass (same existing 2 frontend lint warnings in shadcn ui primitives).

---

## Scope (E2E Coverage Slice)
Playwright page-object and click-through coverage for operator-critical UX:
- Add page object models for Fleet and Deployments navigation/actions.
- Add Playwright tests that verify release selector visibility/selection, target-group member preview, specific-target flow, and run action enable/disable states.
- Wire E2E checks into phase gate so UI regressions fail before completion.

## Plan (E2E Coverage Slice)
- [x] Add Playwright test tooling + scripts in frontend.
- [x] Implement page object model classes for app shell and deployments screen.
- [x] Implement core click-through tests with deterministic API stubs.
- [x] Add Makefile target and include E2E in `phase1-gate-full`.
- [x] Run verification commands and capture outcomes.

## Verification Commands (E2E Coverage Slice)
- [x] `make lint`
- [x] `make typecheck`
- [x] `make test`
- [x] `make phase1-gate-full`

## Results Log (E2E Coverage Slice)
- 2026-02-26: Began Playwright POM + core click-through coverage implementation.
- 2026-02-26: Added Playwright tooling (`@playwright/test`) and scripts (`test:e2e`, `test:e2e:ci`) in frontend package workflow.
- 2026-02-26: Implemented page object models for app shell and deployments interactions under `frontend/e2e/pages`.
- 2026-02-26: Added deterministic API mocking helper and core click-through specs for release selection, target-group preview, specific-target run start, and run action gating.
- 2026-02-26: Added stable test hooks (`id`/`data-testid`) to critical deployment controls for resilient behavior assertions.
- 2026-02-26: Added `test-frontend-e2e` Make target and enforced it in `phase1-gate-full`.
- 2026-02-26: Updated Vitest include pattern to prevent Playwright specs from being executed as unit tests.
- 2026-02-26: Strengthened `src/App.test.tsx` to assert release selector visibility and run-action disabled states in Deployments view.
- 2026-02-26: Verified `make lint`, `make typecheck`, `make test`, and `make phase1-gate-full` pass (same existing 2 frontend lint warnings in shadcn ui primitives).

---

## Scope (Status UX Slice)
Deployment form and progress accuracy refinements:
- Keep release selector inline with the other deployment selectors.
- Ensure progress bars represent outcome composition (succeeded vs failed), not only terminal completion.

## Plan (Status UX Slice)
- [x] Move release selector into the main deployment form row.
- [x] Replace single-color progress bars with segmented status bars by outcome.
- [x] Extend Playwright coverage to assert failed segment visibility for failed runs.
- [x] Run verification commands and capture outcomes.

## Verification Commands (Status UX Slice)
- [x] `make lint`
- [x] `make typecheck`
- [x] `make test`
- [x] `make phase1-gate-full`

## Results Log (Status UX Slice)
- 2026-02-26: Moved release selector into the primary Start Deployment Run selector row.
- 2026-02-26: Changed run list/detail status bars to segmented composition (`succeeded`, `failed`, `in-progress`, `queued`) and adjusted labels to avoid conflating completion with success.
- 2026-02-26: Added stable progress segment test IDs and Playwright assertions for failed-segment visibility.
- 2026-02-26: Verified `make lint`, `make typecheck`, `make test`, and `make phase1-gate-full` pass (same existing 2 frontend lint warnings in shadcn ui primitives).

---

## Scope (Admin Target CRUD Slice)
Target registration operations in Admin:
- Add operator edit/delete capabilities for registered targets.
- Keep onboarding-event create flow as the source of registration.
- Ensure generated API contracts and docs are updated.

## Plan (Admin Target CRUD Slice)
- [x] Add backend update/delete registration routes and store operations.
- [x] Add frontend API client wrappers for registration update/delete.
- [x] Add Admin registrations table actions (Edit/Delete) with an edit drawer.
- [x] Regenerate OpenAPI + frontend generated schema.
- [x] Update docs for new onboarding registration APIs.
- [x] Run verification commands and capture outcomes.

## Verification Commands (Admin Target CRUD Slice)
- [x] `make openapi`
- [x] `make client-gen`
- [x] `make typecheck-backend`
- [x] `make test-backend`
- [x] `make typecheck-frontend`
- [x] `make lint-frontend`
- [x] `make test-frontend`

## Results Log (Admin Target CRUD Slice)
- 2026-03-01: Added backend onboarding registration update/delete routes and persistence wiring.
- 2026-03-01: Added frontend API wrappers for `PATCH/DELETE /api/v1/admin/onboarding/registrations/{target_id}`.
- 2026-03-01: Added Admin Registered Targets table row actions (`Edit`, `Delete`) and top-drawer edit form.
- 2026-03-01: Split Admin datatable rendering into `frontend/src/components/AdminTables.tsx` to satisfy frontend file-size guardrails.
- 2026-03-01: Updated onboarding API docs in `README.md` and `docs/architecture.md`.
- 2026-03-01: Verified `openapi`, `client-gen`, backend tests/typecheck, frontend typecheck/test, and frontend lint (existing 3 warnings only).

---

## Scope (Backend Size Guardrail Ratchet Slice)
Backend maintainability guardrails:
- Enforce frontend-like backend file-size limits.
- Start shrinking oversized backend modules and tighten temporary exception caps.

## Plan (Backend Size Guardrail Ratchet Slice)
- [x] Extract execution utility helpers out of `execution.py` into a dedicated module.
- [x] Keep backend tests/lint/type checks green after extraction.
- [x] Ratchet backend file-size exception caps downward to current practical limits.

## Verification Commands (Backend Size Guardrail Ratchet Slice)
- [x] `make lint-backend`
- [x] `make typecheck-backend`
- [x] `make test-backend`

## Results Log (Backend Size Guardrail Ratchet Slice)
- 2026-03-01: Extracted common tenant/retry/error-parsing/image/env/correlation utilities from `backend/app/modules/execution.py` into `backend/app/modules/execution_utils.py`.
- 2026-03-01: Reduced `execution.py` from 1696 lines to 1222 lines while preserving runtime behavior and test coverage.
- 2026-03-01: Tightened backend file-size exception caps in `scripts/backend_file_size_check.py`:
  - `control_plane.py`: `1700 -> 1650`
  - `execution.py`: `1750 -> 1250`
- 2026-03-01: Verified backend lint/typecheck/tests pass after refactor + cap ratchet.

- 2026-03-01: Extracted `control_plane.py` helper/storage logic into:
  - `backend/app/modules/control_plane_helpers.py`
  - `backend/app/modules/control_plane_storage.py`
- 2026-03-01: Reduced `control_plane.py` from 1649 lines to 1387 lines.
- 2026-03-01: Tightened `control_plane.py` exception cap from `1650 -> 1400` in `scripts/backend_file_size_check.py`.
- 2026-03-01: Re-verified `make lint-backend`, `make typecheck-backend`, and `make test-backend` all pass after control-plane split.

---

## Scope (Script Sweep + Pulumi Boundary Slice)
Production-alignment cleanup:
- Inventory all scripts and classify keep/delete/migrate-to-pulumi.
- Remove dead/deprecated scripts not used by current workflow.
- Establish migration boundary for runtime ACA + Function App into Pulumi-managed lifecycle.

## Plan (Script Sweep + Pulumi Boundary Slice)
- [x] Audit `scripts/` and `backend/scripts/` against Make/docs usage.
- [x] Remove unused/deprecated backend scripts.
- [x] Publish script disposition + migration priorities in docs.

## Verification Commands (Script Sweep + Pulumi Boundary Slice)
- [x] `rg -n "scripts/|backend/scripts/" Makefile README.md docs -S`
- [x] `find scripts backend/scripts -maxdepth 1 -type f | sort`

## Results Log (Script Sweep + Pulumi Boundary Slice)
- 2026-03-01: Added script sweep report at `/Users/cvonderheid/workspace/mappo/docs/script-sweep.md` with per-script disposition and Pulumi migration priorities.
- 2026-03-01: Removed dead/deprecated backend scripts:
  - `/Users/cvonderheid/workspace/mappo/backend/scripts/demo_reset.py`
  - `/Users/cvonderheid/workspace/mappo/backend/scripts/import_pulumi_targets.py`
- 2026-03-01: Confirmed highest-priority Pulumi migration targets are `runtime_aca_deploy.sh` and `marketplace_forwarder_deploy.sh`.

---

## Scope (Phase 5 Slice)
Live execution boundary hardening:
- Introduce execution-mode abstraction so run orchestration can target demo or Azure execution backends.
- Keep demo behavior deterministic for local/dev and existing test suite.
- Add stricter resume/retry applicability rules so invalid actions fail consistently.

## Plan (Phase 5 Slice)
- [x] Add execution mode settings and bootstrap wiring (`demo` default, `azure` optional).
- [x] Extract per-target stage execution behavior into executor adapters (`demo` + `azure` boundary adapter).
- [x] Wire control-plane run execution to use the configured executor without changing API contract.
- [x] Add backend regression tests for action applicability and execution mode behavior.
- [x] Run verification commands and capture outcomes.

## Verification Commands (Phase 5 Slice)
- [x] `make openapi`
- [x] `make client-gen`
- [x] `make lint`
- [x] `make typecheck`
- [x] `make test`
- [x] `make phase1-gate-full`

## Results Log (Phase 5 Slice)
- 2026-02-27: Started Phase 5 implementation for execution-mode abstraction and live-boundary hardening.
- 2026-02-27: Added execution adapter module (`demo` + `azure`) and moved per-target stage simulation out of `ControlPlaneStore`.
- 2026-02-27: Added `MAPPO_EXECUTION_MODE` and Azure credential settings wiring through app bootstrap/scripts.
- 2026-02-27: Added backend regression tests for Azure execution boundary behavior and completed-run resume rejection.
- 2026-02-27: Verified `make openapi`, `make client-gen`, `make lint`, `make typecheck`, `make test`, and `make phase1-gate-full` pass (same existing 2 frontend lint warnings in shadcn ui primitives).

---

## Scope (Phase 5.2 Slice)
Pulumi baseline for live demo tenant provisioning:
- Add Pulumi IaC project for deploying target ACA baseline resources across selected subscriptions.
- Keep this slice demo-first: focus on repeatable 10-target scaffolding and outputs MAPPO can consume.
- Align command workflow with existing Make-driven standards.

## Plan (Phase 5.2 Slice)
- [x] Scaffold `infra/pulumi` TypeScript project with Azure Native provider dependencies.
- [x] Implement target loop deployment model (resource group + ACA environment + target app per configured target).
- [x] Add stack config template for a 10-target demo dataset and clear variable contract.
- [x] Add Make targets and docs for install/preview/up/down/export workflows.
- [x] Run verification commands and capture outcomes.

## Verification Commands (Phase 5.2 Slice)
- [x] `make iac-install`
- [x] `make iac-preview`
- [x] `make lint`
- [x] `make typecheck`
- [x] `make test`

## Results Log (Phase 5.2 Slice)
- 2026-02-27: Started Pulumi IaC baseline implementation for live multi-tenant demo provisioning.
- 2026-02-27: Added `infra/pulumi` TypeScript Pulumi project with Azure Native target-loop deployment model.
- 2026-02-27: Added stack files (`Pulumi.dev.yaml`, `Pulumi.demo-10tenants.yaml.example`) and exported `mappoTargetInventory` outputs for MAPPO ingestion.
- 2026-02-27: Added root Make targets (`iac-install`, `iac-stack-init`, `iac-preview`, `iac-up`, `iac-destroy`, `iac-export-targets`) with local backend defaults.
- 2026-02-27: Updated README and docs with Pulumi demo workflow and config references.
- 2026-02-27: Verified `make iac-install`, `make iac-preview`, `make lint`, `make typecheck`, and `make test` pass (same existing 2 frontend lint warnings in shadcn ui primitives).

---

## Scope (Phase 5.2 Follow-up)
Pulumi config ergonomics:
- Move 10-target demo definitions from stack YAML to TypeScript source files.
- Keep stack YAML minimal and use profile-based target generation in TS.

## Plan (Phase 5.2 Follow-up)
- [x] Add TypeScript target profile module(s) for demo tenant definitions.
- [x] Update Pulumi program to load targets from TS profiles (with optional config overrides).
- [x] Simplify `Pulumi.dev.yaml` to minimal settings and refresh docs.
- [x] Run IaC preview and core verification commands.

## Verification Commands (Phase 5.2 Follow-up)
- [x] `make iac-preview`
- [x] `make lint`
- [x] `make typecheck`
- [x] `make test`

## Results Log (Phase 5.2 Follow-up)
- 2026-02-27: Started TypeScript target-profile conversion for Pulumi demo stack config.
- 2026-02-27: Added TypeScript target profile modules (`targets.ts`, `targets.demo10.ts`) and removed YAML-based 10-target template.
- 2026-02-27: Updated Pulumi program to default to `mappo:targetProfile=demo10` with automatic subscription resolution from config/env/active Azure account.
- 2026-02-27: Reduced `Pulumi.dev.yaml` to minimal config and updated IaC docs for TS profile workflow.
- 2026-02-27: Verified `make iac-preview`, `make lint`, `make typecheck`, and `make test` pass (same existing 2 frontend lint warnings in shadcn ui primitives).
- 2026-02-27: Addressed Azure ACA environment quota failures by introducing shared environment mode (`mappo:environmentMode=shared_per_subscription`) and redeploying after stack cleanup.
- 2026-02-27: Confirmed live deployment success with `make iac-destroy` + `make iac-up` + `make iac-export-targets` (10 target apps created, inventory exported to `.data/mappo-target-inventory.json`).

---

## Scope (Phase 5.3 Slice)
Fleet sync from live IaC output:
- Add a deterministic import path from Pulumi target inventory JSON into MAPPO target store.
- Support reset of run history during import to avoid stale references after fleet replacement.

## Plan (Phase 5.3 Slice)
- [x] Add control-plane method to replace target inventory atomically.
- [x] Add backend script + Make target to import `.data/mappo-target-inventory.json`.
- [x] Add backend test coverage for target-replacement behavior and run reset option.
- [x] Run verification commands and execute live import.

## Verification Commands (Phase 5.3 Slice)
- [x] `make lint`
- [x] `make typecheck`
- [x] `make test`
- [x] `make import-targets`

## Results Log (Phase 5.3 Slice)
- 2026-02-27: Started live fleet-sync implementation from Pulumi inventory output.
- 2026-02-27: Added `ControlPlaneStore.replace_targets(...)` for atomic fleet replacement with optional run-history reset.
- 2026-02-27: Added import script `backend/scripts/import_pulumi_targets.py` and Make target `import-targets`.
- 2026-02-27: Added backend regression test for target replacement with run clearing behavior.
- 2026-02-27: Executed live import from `.data/mappo-target-inventory.json`; verified 10 targets loaded with Azure subscription ID `c0d51042-7d0a-41f7-b270-151e4c4ea263`.

---

## Scope (Phase 5.4 Slice)
Live Azure execution adapter implementation:
- Replace Azure executor scaffold with real Container Apps rollout operations in Azure mode.
- Keep orchestration/state model unchanged while wiring per-target validate/deploy/verify behavior.
- Persist operator-visible logs/errors with actionable failure codes for Azure auth/resource/deploy/health failures.

## Plan (Phase 5.4 Slice)
- [x] Implement Azure SDK executor path for target validation and Container App deployment updates.
- [x] Implement verification checks (revision readiness + HTTP health probe via ingress FQDN).
- [x] Add deterministic regression tests for Azure success and failure paths using runtime stubs (no live Azure dependency in tests).
- [x] Update README/docs for Azure mode auth/runtime expectations.
- [x] Run verification commands and capture outcomes.

## Verification Commands (Phase 5.4 Slice)
- [x] `make lint`
- [x] `make typecheck`
- [x] `make test`
- [x] `make phase1-gate-full`

## Results Log (Phase 5.4 Slice)
- 2026-02-28: Replaced Azure executor scaffold with SDK-based runtime using `azure-identity` + `azure-mgmt-appcontainers`.
- 2026-02-28: Added real Azure target validation, deploy update, and verify-stage health probing with structured failure codes.
- 2026-02-28: Added deterministic backend tests for Azure mode missing credentials, successful runtime flow, and deploy failure surfacing via stubbed runtime factory.
- 2026-02-28: Updated README execution-mode docs to reflect SDK-based Azure behavior and health-path configuration.
- 2026-02-28: Verified `make lint`, `make typecheck`, `make test`, and `make phase1-gate-full` pass (same existing 2 frontend lint warnings in shadcn ui primitives).

---

## Scope (Phase 5.5 Slice)
Production-like Azure demo readiness:
- Add deterministic preflight checks so MAPPO can validate whether environment topology matches real-world multi-tenant expectations.
- Document the exact setup sequence for provider tenant + customer tenants + Lighthouse delegation + MAPPO runtime wiring.
- Keep this slice focused on readiness workflow; no orchestration contract changes.

## Plan (Phase 5.5 Slice)
- [x] Add `make azure-preflight` command with actionable pass/fail checks for login, tenant/subscription count, Azure creds, and target inventory.
- [x] Add `docs/live-demo-checklist.md` with real-world topology and onboarding sequence.
- [x] Update README to reference the checklist and preflight command.
- [x] Run verification commands and capture outcomes.

## Verification Commands (Phase 5.5 Slice)
- [x] `make azure-preflight`
- [x] `make lint`

## Results Log (Phase 5.5 Slice)
- 2026-02-28: Added `scripts/azure_preflight.sh` and `make azure-preflight` to report production-like Azure readiness with explicit pass/fail checks.
- 2026-02-28: Added `/Users/cvonderheid/workspace/mappo/docs/live-demo-checklist.md` covering provider tenant, customer tenants, Lighthouse onboarding, and MAPPO runtime wiring.
- 2026-02-28: Updated README to surface preflight and live-demo checklist entry points.
- 2026-02-28: Ran `make azure-preflight` and confirmed current blockers: single tenant and missing MAPPO Azure service principal env vars.
- 2026-02-28: Verified `make lint` passes (same existing 2 frontend lint warnings in shadcn ui primitives).

---

## Scope (Phase 5.6 Slice)
Reproducible Azure auth scripting:
- Replace ad-hoc copy/paste credential setup with repeatable scripts and Make targets.
- Provide a file-based local env workflow so commands can be rerun deterministically.

## Plan (Phase 5.6 Slice)
- [x] Add script to create Azure SP credentials and write `.data/mappo-azure.env`.
- [x] Add script wrapper to run commands with the local Azure env file loaded.
- [x] Add Make targets for bootstrap + backend startup in Azure mode.
- [x] Update preflight/docs to prefer script-based workflow.
- [x] Run verification commands and capture outcomes.

## Verification Commands (Phase 5.6 Slice)
- [x] `make azure-auth-bootstrap`
- [x] `make azure-preflight`
- [x] `make lint`

## Results Log (Phase 5.6 Slice)
- 2026-02-28: Added `scripts/azure_auth_bootstrap.sh` to create scoped Azure SP credentials and write `.data/mappo-azure.env`.
- 2026-02-28: Added `scripts/with_mappo_azure_env.sh` wrapper to load the env file before running backend commands.
- 2026-02-28: Added Make targets `azure-auth-bootstrap` and `dev-backend-azure` for reproducible auth/bootstrap runtime flow.
- 2026-02-28: Updated `azure_preflight` to auto-load `.data/mappo-azure.env` when present.
- 2026-02-28: Updated README and live-demo checklist to prefer script-driven workflow over manual copy/paste exports.
- 2026-02-28: Verified `make azure-auth-bootstrap`, `make azure-preflight`, and `make lint` (preflight now passes credentials check; remaining blocker is single-tenant topology).

---

## Scope (Phase 5.7 Slice)
Scripted Lighthouse delegation:
- Add reproducible customer-side delegation script to onboard MAPPO provider principal to customer subscriptions via Azure Lighthouse.
- Integrate into Make workflow and docs so onboarding can be rerun without manual portal steps.

## Plan (Phase 5.7 Slice)
- [x] Add `scripts/lighthouse_delegate_customer.sh` with deterministic definition/assignment IDs and idempotent create behavior.
- [x] Add Make target for customer delegation command.
- [x] Update docs/checklist with scripted delegation usage.
- [x] Run verification commands and capture outcomes.

## Verification Commands (Phase 5.7 Slice)
- [x] `make lighthouse-delegate-customer CUSTOMER_SUBSCRIPTION_ID=<id>`
- [x] `make azure-preflight`
- [x] `make lint`

## Results Log (Phase 5.7 Slice)
- 2026-02-28: Added `scripts/lighthouse_delegate_customer.sh` to create Azure Lighthouse registration definition/assignment for customer subscriptions using deterministic IDs.
- 2026-02-28: Added `make lighthouse-delegate-customer CUSTOMER_SUBSCRIPTION_ID=<id>` wrapper target.
- 2026-02-28: Updated README and live-demo checklist with scripted customer delegation step.
- 2026-02-28: Executed delegation for customer subscription `1adaaa48-139a-477b-a8c8-0e6289d6d199`; created definition `afd6cb7b-ca30-51a7-88a8-43ed371728f4` and assignment `bb7eee0f-ad00-5b5e-9691-092ef53af5fc`.
- 2026-02-28: Verified `make azure-preflight` (0 failures) and `make lint` (same existing 2 frontend lint warnings in shadcn ui primitives).

---

## Scope (Phase 5.8 Slice)
Dual-subscription demo stack automation:
- Add script to generate Pulumi stack config with demo targets split across provider/customer subscriptions.
- Remove manual stack-YAML editing and make multi-subscription provisioning reproducible.

## Plan (Phase 5.8 Slice)
- [x] Add script to generate `infra/pulumi/Pulumi.<stack>.yaml` with dual-subscription target mapping.
- [x] Add Make target wrapper for the stack-prep script.
- [x] Run stack prep, preflight, and lint verification.

## Verification Commands (Phase 5.8 Slice)
- [x] `make iac-prepare-dual-stack CUSTOMER_SUBSCRIPTION_ID=<id>`
- [x] `make azure-preflight`
- [x] `make lint`

## Results Log (Phase 5.8 Slice)
- 2026-02-28: Added `scripts/iac_prepare_dual_stack.sh` to generate stack-specific dual-subscription Pulumi target config.
- 2026-02-28: Added `make iac-prepare-dual-stack CUSTOMER_SUBSCRIPTION_ID=<id> [PULUMI_STACK=...]`.
- 2026-02-28: Generated `infra/pulumi/Pulumi.dual-demo.yaml` with 10 targets split across provider/customer subscriptions.
- 2026-02-28: Updated README and live-demo checklist to include scripted dual-stack preparation.
- 2026-02-28: Verified `make azure-preflight` (0 failures, remaining warning expected until dual-stack resources are exported/imported) and `make lint` (same existing 2 frontend lint warnings in shadcn ui primitives).

---

## Scope (Phase 5.9 Slice)
Marketplace-accurate managed app workflow pivot:
- Make managed application onboarding the default live-demo path.
- Add script-driven discovery of MAPPO targets from `Microsoft.Solutions/applications`.
- Reframe readiness checks around managed app inventory signals instead of Lighthouse-specific assumptions.
- Keep Lighthouse tooling optional for delegated-ops scenarios.

## Plan (Phase 5.9 Slice)
- [x] Add managed-app discovery script + Make target for reproducible inventory generation.
- [x] Update `azure-preflight` to validate managed app metadata and container app resource ID shape.
- [x] Update docs/UI copy to represent managed-app-first architecture and optional Lighthouse usage.
- [x] Run verification commands and capture outcomes.

## Verification Commands (Phase 5.9 Slice)
- [x] `./scripts/managed_app_discover_targets.sh --help`
- [x] `./scripts/managed_app_discover_targets.sh --subscriptions "<provider-sub>,<customer-sub>" --output-file .data/mappo-target-inventory.managed-app-smoke.json --allow-empty`
- [x] `make azure-preflight`
- [x] `make lint`
- [x] `make typecheck`
- [x] `make test`

## Results Log (Phase 5.9 Slice)
- 2026-02-28: Added `scripts/managed_app_discover_targets.sh` and `make managed-app-discover-targets` for script-first target inventory discovery from managed applications.
- 2026-02-28: Updated `scripts/azure_preflight.sh` to prioritize managed-app-ready checks (managed metadata coverage + resource ID validation) and removed Lighthouse-specific hard failure messaging.
- 2026-02-28: Updated architecture/docs/UI wording to managed-app-first flow and moved Lighthouse to optional usage.
- 2026-02-28: Verified `./scripts/managed_app_discover_targets.sh --help`, managed-app discovery smoke (`--allow-empty`), `make azure-preflight`, `make lint`, `make typecheck`, and `make test` all pass (same existing 2 frontend lint warnings in shadcn ui primitives).

---

## Scope (Phase 5.10 Slice)
Optional Lighthouse cleanup automation:
- Add script-first teardown path to remove Lighthouse registration assignment/definition for a customer subscription.
- Keep managed-app-first path unchanged while providing explicit cleanup command for demos.

## Plan (Phase 5.10 Slice)
- [x] Add `scripts/lighthouse_undelegate_customer.sh` with deterministic ID derivation matching delegation script.
- [x] Add Make target for cleanup command.
- [x] Update docs/checklist/README optional delegation section with cleanup usage.
- [x] Run verification commands and capture outcomes.

## Verification Commands (Phase 5.10 Slice)
- [x] `./scripts/lighthouse_undelegate_customer.sh --help`
- [x] `bash -n scripts/lighthouse_undelegate_customer.sh`
- [x] `make help | rg "lighthouse-(un)?delegate-customer"`

## Results Log (Phase 5.10 Slice)
- 2026-02-28: Added `scripts/lighthouse_undelegate_customer.sh` to delete Lighthouse assignment and definition for a customer subscription.
- 2026-02-28: Added `make lighthouse-undelegate-customer CUSTOMER_SUBSCRIPTION_ID=<id>` wrapper target.
- 2026-02-28: Updated README and live-demo checklist to include explicit Lighthouse cleanup path while keeping Lighthouse optional.
- 2026-02-28: Verified script help, shell syntax, and Make target visibility checks pass.

---

## Scope (Phase 5.11 Slice)
Managed-app discovery safety fix:
- Prevent `managed-app-discover-targets` from clobbering inventory with empty output when no managed app targets are discoverable.
- Keep the command failing in that case, but preserve the previous known-good inventory file.

## Plan (Phase 5.11 Slice)
- [x] Reproduce the failure using the real command path.
- [x] Patch discovery script to validate zero-target condition before writing output (unless `--allow-empty`).
- [x] Restore inventory and rerun user command sequence.
- [x] Capture outcomes and update lessons.

## Verification Commands (Phase 5.11 Slice)
- [x] `make managed-app-discover-targets SUBSCRIPTION_IDS="<provider-sub>,<customer-sub>"`
- [x] `make iac-export-targets`
- [x] `make import-targets`
- [x] `make azure-preflight`
- [x] `bash -n scripts/managed_app_discover_targets.sh`

## Results Log (Phase 5.11 Slice)
- 2026-02-28: Reproduced failure where `managed-app-discover-targets` exited non-zero and still wrote empty inventory.
- 2026-02-28: Updated `scripts/managed_app_discover_targets.sh` to preserve existing output file when zero targets are discovered and `--allow-empty` is not set.
- 2026-02-28: Restored `.data/mappo-target-inventory.json` from Pulumi stack output and re-imported 10 targets.
- 2026-02-28: Reran sequence (`managed-app-discover-targets`, `import-targets`, `azure-preflight`) and confirmed non-destructive behavior plus successful import/preflight.

---

## Scope (Phase 5.12 Slice)
Managed app simulation orchestration:
- Add scriptable managed-app simulation bootstrap/teardown for marketplace-style flows without requiring a published marketplace offer.
- Create `make managed-app-sim-up` / `make managed-app-sim-down` commands and persist teardown state.

## Plan (Phase 5.12 Slice)
- [x] Add simulation bootstrap script to create managed app definitions + managed app instances + container apps in managed RGs.
- [x] Add simulation teardown script to remove managed apps/managed RGs/definitions based on state file.
- [x] Wire new scripts into Makefile and docs.
- [x] Verify with a real 2-target cross-subscription smoke run.

## Verification Commands (Phase 5.12 Slice)
- [x] `bash -n scripts/managed_app_sim_up.sh scripts/managed_app_sim_down.sh`
- [x] `./scripts/managed_app_sim_up.sh --help`
- [x] `./scripts/managed_app_sim_down.sh --help`
- [x] `make help | rg "managed-app-sim-(up|down)"`
- [x] `make managed-app-sim-up TARGET_FILE=.data/mappo-target-inventory.sim-seed.json SUBSCRIPTION_IDS="<provider-sub>,<customer-sub>" MAX_TARGETS=2`

## Results Log (Phase 5.12 Slice)
- 2026-02-28: Added `scripts/managed_app_sim_up.sh` to provision service-catalog managed app simulation targets and write `.data/mappo-managedapp-sim-state.json`.
- 2026-02-28: Added `scripts/managed_app_sim_down.sh` to cleanly tear down simulation resources from state.
- 2026-02-28: Added Make targets `managed-app-sim-up` and `managed-app-sim-down`.
- 2026-02-28: Updated README/live checklist with managed-app simulation bootstrap/teardown commands.
- 2026-02-28: Executed real smoke run and created 2 managed app targets across both subscriptions.

---

## Scope (Phase 5.13 Slice)
Live discovery reliability fixes:
- Resolve live failures observed during managed-app simulation smoke run.
- Ensure discovery uses the correct managed app API surface and captures managed RG metadata.

## Plan (Phase 5.13 Slice)
- [x] Fix `managed-app-sim-up` container app create args for current Azure CLI contract.
- [x] Relax provider registration gate to tolerate long `Registering` windows.
- [x] Update managed-app discovery to use `az managedapp list` and top-level `managedResourceGroupId`.
- [x] Re-run discovery/import/preflight and capture outcomes.

## Verification Commands (Phase 5.13 Slice)
- [x] `make managed-app-sim-up TARGET_FILE=.data/mappo-target-inventory.sim-seed.json SUBSCRIPTION_IDS="<provider-sub>,<customer-sub>" MAX_TARGETS=2`
- [x] `make managed-app-discover-targets SUBSCRIPTION_IDS="<provider-sub>,<customer-sub>" MANAGED_APP_NAME_PREFIX="mappo-ma"`
- [x] `make import-targets`
- [x] `make azure-preflight`
- [x] `make lint`

## Results Log (Phase 5.13 Slice)
- 2026-02-28: Fixed `managed-app-sim-up` failure by removing unsupported `--location` argument from `az containerapp create`.
- 2026-02-28: Updated provider registration checks to continue when provider state remains `Registering`.
- 2026-02-28: Updated managed app discovery to use `az managedapp list` and to read top-level `managedResourceGroupId`.
- 2026-02-28: Verified end-to-end sequence succeeds (`managed-app-sim-up`, discover, import, preflight) with 2 cross-subscription managed app targets and managed metadata present.

---

## Scope (Phase 5.14 Slice)
Script-first seed generation:
- Remove manual target-seed JSON editing from simulation workflow.
- Add deterministic generator for cross-subscription simulation target inventories.

## Plan (Phase 5.14 Slice)
- [x] Add simulation seed generator script.
- [x] Add Make target wrapper and docs updates.
- [x] Verify seed command surface.

## Verification Commands (Phase 5.14 Slice)
- [x] `bash -n scripts/managed_app_sim_seed_targets.sh`
- [x] `./scripts/managed_app_sim_seed_targets.sh --help`
- [x] `make help | rg "managed-app-sim-seed"`

## Results Log (Phase 5.14 Slice)
- 2026-02-28: Added `scripts/managed_app_sim_seed_targets.sh` for deterministic 10-target (or N-target) cross-subscription seed generation.
- 2026-02-28: Added `make managed-app-sim-seed` wrapper target and updated README/live checklist examples.
- 2026-02-28: Verified script syntax/help and Make target visibility.

---

## Scope (Phase 5.15 Slice)
Managed-app-only demo surface:
- Strip non-essential Lighthouse/Pulumi/simulation paths from the primary operator workflow.
- Keep a single command path for live demos: discover -> import -> preflight.

## Plan (Phase 5.15 Slice)
- [x] Simplify Makefile target surface to managed-app demo essentials.
- [x] Add `managed-demo-refresh` orchestration target.
- [x] Rewrite README/live docs around the managed-app-only path.
- [x] Run managed demo refresh and lint verification.

## Verification Commands (Phase 5.15 Slice)
- [x] `make help`
- [x] `make managed-demo-refresh SUBSCRIPTION_IDS="<provider-sub>,<customer-sub>" MANAGED_APP_NAME_PREFIX="mappo-ma"`
- [x] `make lint`

## Results Log (Phase 5.15 Slice)
- 2026-02-28: Removed Lighthouse, Pulumi, and simulation targets from primary Makefile surface; retained managed-app workflow commands.
- 2026-02-28: Added `make managed-demo-refresh` to chain discovery/import/preflight.
- 2026-02-28: Updated README, architecture notes, live checklist, and docs command examples to managed-app demo focus.
- 2026-02-28: Verified managed demo refresh succeeds with current live managed apps (2 targets across 2 subscriptions).

---

## Scope (Phase 5.16 Slice)
Azure resource naming cleanup:
- Remove legacy resource groups from earlier demo tracks to reduce operator confusion.
- Preserve active managed-app demo resources and verify inventory/runtime still healthy.

## Plan (Phase 5.16 Slice)
- [x] Delete legacy `rg-mappo-target-*` groups from provider subscription.
- [x] Delete legacy `mappo-sim-*` apps/definitions and `rg-mappo-ma-sim-*` groups across both subscriptions.
- [x] Delete legacy `rg-mappo-dual-demo-*` groups across both subscriptions.
- [x] Migrate customer managed-app target app to cleanly named shared environment and remove final blocked dual-demo environment group.
- [x] Re-run managed demo refresh and preflight.

## Verification Commands (Phase 5.16 Slice)
- [x] Azure CLI group/app inventory queries for `rg-mappo-target-*`, `rg-mappo-ma-sim-*`, `rg-mappo-dual-demo-*`, `mappo-sim-*`, `mappo-ma-*`
- [x] `make managed-demo-refresh SUBSCRIPTION_IDS="<provider-sub>,<customer-sub>" MANAGED_APP_NAME_PREFIX="mappo-ma"`

## Results Log (Phase 5.16 Slice)
- 2026-02-28: Removed all `rg-mappo-target-*` groups from provider subscription.
- 2026-02-28: Removed all `mappo-sim-*` managed apps/definitions and `rg-mappo-ma-sim-*` groups in both subscriptions.
- 2026-02-28: Removed all `rg-mappo-dual-demo-*` groups; last customer shared-env RG required migration because active app was still bound.
- 2026-02-28: Migrated `ca-mappo-ma-target-02` from `cae-mappo-dual-demo-shared-1adaaa48` to `cae-mappo-ma-shared-1adaaa48`, then deleted remaining dual-demo shared-env group.
- 2026-02-28: Verified final state includes only active `mappo-ma-*` managed-app demo resources and managed-demo refresh/preflight remains healthy.

---

## Scope (Phase 5.17 Slice)
Production-path cleanup + 2-target demo baseline:
- Remove implicit runtime seeding from backend production modules.
- Make execution-mode defaults production-safe (`azure`) instead of `demo`.
- Keep demo/sample seeding explicit in scripts/tests only.
- Keep 2-target managed-app flow runnable via script-first commands.
- Remove remaining MAPPO/TXero DB default drift (`txero` creds/port 5432 remnants).

## Plan (Phase 5.17 Slice)
- [x] Remove constructor auto-seeding and demo reset behavior from `ControlPlaneStore`.
- [x] Add explicit public release replacement API for script/test provisioning.
- [x] Move sample/demo data usage to scripts/tests (not `backend/app` runtime path).
- [x] Replace legacy target import script naming (`import_pulumi_targets.py`) with generic inventory import path.
- [x] Add explicit release bootstrap command for runnable demo state after target import.
- [x] Align backend DB defaults/scripts/tests to MAPPO (`mappo:mappo`, `localhost:5433`).
- [x] Run verification commands and capture outcomes.

## Verification Commands (Phase 5.17 Slice)
- [x] `make check-no-demo-leak`
- [x] `make lint`
- [x] `make typecheck`
- [x] `make test`
- [x] `make managed-demo-refresh SUBSCRIPTION_IDS="<provider-sub>,<customer-sub>" MANAGED_APP_NAME_PREFIX="mappo-ma"`

## Results Log (Phase 5.17 Slice)
- 2026-02-28: Removed runtime auto-seeding defaults from `backend/app/modules/control_plane.py` and changed default execution mode to `azure`.
- 2026-02-28: Added `replace_releases(...)` API on store and rewired demo reset to explicit script-level provisioning.
- 2026-02-28: Added `backend/scripts/import_targets.py` and `backend/scripts/bootstrap_releases.py`; `managed-demo-refresh` now chains discover -> import -> bootstrap releases -> preflight.
- 2026-02-28: Updated managed-app discovery fallback tags (`environment`) and docs/checklists for explicit release bootstrap step.
- 2026-02-28: Aligned DB defaults to MAPPO (`mappo:mappo@localhost:5433`) across settings, DB session, Flyway, model-generation, and backend tests.
- 2026-02-28: Added safe local DB fallback (`5433` preferred, fallback to `5432`) for settings/tests and Flyway invocation to keep local workflows operable when MAPPO compose DB is not running.
- 2026-02-28: Verified `check-no-demo-leak`, `lint`, `typecheck`, `test`, and `managed-demo-refresh` all pass (azure-preflight emits expected warning for 2-target demo size vs 10-target full demo).

---

## Scope (Phase 5.18 Slice)
Frontend route/navigation refinement:
- Replace screen-toggle state with route-based pages for Fleet, Deployments, and Admin.
- Keep shadcn UI components as the widget/layout system.
- Keep existing deployment UX behavior intact while adding proper top navigation links.
- Update UI tests and Playwright page objects for route-based nav interactions.

## Plan (Phase 5.18 Slice)
- [x] Add `react-router-dom` and refactor app shell to BrowserRouter + Routes.
- [x] Implement `/fleet`, `/deployments`, `/admin` pages with top navigation.
- [x] Keep deployment run creation/detail controls on Deployments route.
- [x] Add Admin page placeholder content for discovery/identity operations.
- [x] Update unit test + Playwright page object nav selectors (`link` instead of button).
- [x] Run frontend lint/typecheck/test/e2e verification.

## Verification Commands (Phase 5.18 Slice)
- [x] `cd frontend && npm run lint`
- [x] `cd frontend && npm run typecheck`
- [x] `cd frontend && npm run test`
- [x] `cd frontend && npm run test:e2e -- --reporter=line`

## Results Log (Phase 5.18 Slice)
- 2026-02-28: Added route-based app shell with `/fleet`, `/deployments`, and `/admin` pages plus top navigation links.
- 2026-02-28: Preserved shadcn-based cards/buttons/forms and existing fleet/deployment panel behavior.
- 2026-02-28: Added Admin route placeholder explaining Managed Identity + discovery direction.
- 2026-02-28: Updated `App.test.tsx` and Playwright `AppShellPage` for route-link navigation.
- 2026-02-28: Verified frontend lint/typecheck/unit/e2e all pass (same existing 2 shadcn lint warnings in ui primitives).

---

## Scope (Phase 5.19 Slice)
Cross-project naming cleanup:
- Remove lingering `txero` references from MAPPO runtime/test DB settings.
- Preserve functionality while keeping MAPPO defaults self-contained.

## Plan (Phase 5.19 Slice)
- [x] Remove `txero` fallback URLs from backend runtime config.
- [x] Remove `txero` fallback URL from backend test bootstrap config.
- [x] Re-run backend verification checks.

## Verification Commands (Phase 5.19 Slice)
- [x] `rg -n "txero" backend/app/core/settings.py backend/app/db/session.py backend/tests/conftest.py`
- [x] `make lint-backend`
- [x] `make typecheck-backend`
- [x] `MAPPO_DATABASE_URL='postgresql+psycopg://txero:txero@localhost:5432/mappo' make test-backend`

## Results Log (Phase 5.19 Slice)
- 2026-02-28: Removed `txero` references from `backend/app/core/settings.py`, `backend/app/db/session.py`, and `backend/tests/conftest.py`.
- 2026-02-28: Verified no `txero` matches remain in those files.
- 2026-02-28: Verified backend lint/typecheck pass and backend tests pass with explicit local DB override.

---

## Scope (Phase 5.20 Slice)
Admin discovery/import wiring (SDK-backed):
- Add backend admin endpoint to discover managed app targets via Azure SDK and import directly into MAPPO target inventory.
- Wire `/admin` UI to call the backend endpoint and display import summary/warnings/errors.
- Keep OpenAPI + frontend generated client flow current.

## Plan (Phase 5.20 Slice)
- [x] Add backend SDK discovery module and admin API route (`/api/v1/admin/discover-import`).
- [x] Add backend tests for admin route behavior (happy path + validation failure).
- [x] Regenerate OpenAPI and frontend generated schema/client types.
- [x] Wire `/admin` page form/actions to invoke backend discovery/import and refresh fleet state.
- [x] Update frontend tests/page objects for route-nav compatibility.
- [x] Run backend + frontend verification (lint/typecheck/test + e2e).

## Verification Commands (Phase 5.20 Slice)
- [x] `make openapi`
- [x] `make client-gen`
- [x] `make lint`
- [x] `make typecheck`
- [x] `make test`
- [x] `cd frontend && npm run test:e2e -- --reporter=line`

## Results Log (Phase 5.20 Slice)
- 2026-02-28: Added SDK discovery module at `backend/app/modules/discovery.py` and new admin router `POST /api/v1/admin/discover-import`.
- 2026-02-28: Added request/response schemas for admin discovery/import in `backend/app/modules/schemas.py`.
- 2026-02-28: Added backend coverage in `backend/tests/test_admin.py` for import replacement flow and missing-subscription validation.
- 2026-02-28: Added frontend Admin panel form at `/admin` to trigger discovery/import and display summary + warnings.
- 2026-02-28: Regenerated OpenAPI/client artifacts and verified backend/frontend lint/typecheck/tests pass; Playwright e2e suite passes.

---

## Scope (Phase 5.21 Slice)
Admin discovery flexibility + operator diagnostics:
- Support both explicit subscription input and auto-enumeration in one admin workflow.
- Surface blocked enumeration scopes (where/why) so operators know what to add manually.
- Keep API contract generation and frontend client wiring aligned.

## Plan (Phase 5.21 Slice)
- [x] Extend backend admin discovery request/response schema for auto-enumeration and blocked-scope diagnostics.
- [x] Update SDK discovery pipeline to merge explicit + enumerated subscriptions and capture blocked scope metadata.
- [x] Update `/api/v1/admin/discover-import` routing/validation and add backend tests for new behavior.
- [x] Update `/admin` UI form to expose both options and display blocked scope diagnostics.
- [x] Regenerate OpenAPI/frontend client artifacts and run full verification gate.

## Verification Commands (Phase 5.21 Slice)
- [x] `make openapi`
- [x] `make client-gen`
- [x] `make lint`
- [x] `make typecheck`
- [x] `make test`
- [x] `make phase1-gate-full`

## Results Log (Phase 5.21 Slice)
- 2026-02-28: Added `auto_enumerate_subscriptions` on admin discovery request and new response diagnostics (`scanned_subscription_ids`, `auto_discovered_subscription_ids`, `blocked_enumeration`).
- 2026-02-28: Updated SDK discovery logic to optionally enumerate accessible subscriptions, merge with explicit IDs, and collect blocked scopes at tenant/subscription/resource-group boundaries.
- 2026-02-28: Updated admin API validation to allow either explicit IDs or auto-enumeration and return blocked-scope diagnostics in structured form.
- 2026-02-28: Extended backend tests for blocked-enumeration payload flow and dual-mode validation.
- 2026-02-28: Updated Admin page to support both discovery modes and show blocked scope `type/id/reason` in results.
- 2026-02-28: Regenerated OpenAPI + frontend schema and verified `lint`, `typecheck`, `test`, and `phase1-gate-full` pass (same existing 2 frontend lint warnings in shadcn ui primitives).

---

## Scope (Phase 5.22 Slice)
Azure rate-limit and quota guardrails:
- Add pre-run concurrency guardrails so Azure runs are bounded and safer by default.
- Add per-subscription batching so execution does not burst ARM writes in one subscription.
- Add transient-fault retry/backoff handling with `Retry-After` support.
- Add optional ACA quota preflight to detect low headroom and reduce concurrency.
- Surface guardrail decisions in run payloads/UI and document operations knobs.

## Plan (Phase 5.22 Slice)
- [x] Extend Azure executor settings with rate-limit/quota guardrail controls.
- [x] Add executor `prepare_run` guardrail planning and apply effective concurrency in run creation.
- [x] Add per-subscription execution batching in control-plane scheduler.
- [x] Add Azure SDK retry/backoff wrapper for retryable failures (`408`, `409`, `429`, `5xx`) with `Retry-After`.
- [x] Add ACA quota preflight checks (`usages.list`) and concurrency-lowering recommendations.
- [x] Persist guardrail warnings and subscription-concurrency in run summary/detail payloads.
- [x] Update Deployments UI to display guardrail warnings and effective per-subscription concurrency.
- [x] Add/extend backend tests for concurrency capping and per-subscription batching behavior.
- [x] Update docs (`architecture`, `live-demo-checklist`, `documentation`) with guardrail behavior and env vars.
- [x] Regenerate OpenAPI/client and run full verification gate.

## Verification Commands (Phase 5.22 Slice)
- [x] `make openapi`
- [x] `make client-gen`
- [x] `make lint`
- [x] `make typecheck`
- [x] `make test`
- [x] `make phase1-gate-full`

## Results Log (Phase 5.22 Slice)
- 2026-02-28: Added Azure guardrail settings for concurrency limits, retry policy, and quota preflight controls in backend settings and runtime wiring.
- 2026-02-28: Added executor-level run preflight (`prepare_run`) that caps run/per-subscription concurrency and records guardrail warnings.
- 2026-02-28: Updated control-plane scheduler to batch by subscription respecting run `subscription_concurrency`.
- 2026-02-28: Added retry/backoff wrapper in Azure runtime with `Retry-After` handling and retryable status support (`408`, `409`, `429`, `500`, `502`, `503`, `504`).
- 2026-02-28: Added ACA quota preflight checks using `client.usages.list(location)` and automatic concurrency downgrades when quota headroom is low.
- 2026-02-28: Extended run payloads with `subscription_concurrency` and `guardrail_warnings`; surfaced warnings in Deployments run cards/details.
- 2026-02-28: Added backend regression tests for guardrail concurrency caps and per-subscription batching enforcement.
- 2026-02-28: Updated operational docs and checklist with guardrail env vars and validation expectations.
- 2026-02-28: Verified `openapi`, `client-gen`, `lint`, `typecheck`, `test`, and `phase1-gate-full` pass (same existing 2 frontend lint warnings in shadcn ui primitives).

---

## Scope (Phase 5.23 Slice)
Make workflow ergonomics:
- Make `make install` perform full bootstrap + verification + build.
- Preserve a lightweight dependency-only install path.
- Update docs to reflect new command behavior.

## Plan (Phase 5.23 Slice)
- [x] Update Makefile target graph so `install` executes full bootstrap sequence.
- [x] Add `install-deps` for dependency-only setup.
- [x] Update README/docs command guidance.
- [x] Verify Make target graph/output (`make help`, dry-run `make -n install`).

## Verification Commands (Phase 5.23 Slice)
- [x] `make help`
- [x] `make -n install`

## Results Log (Phase 5.23 Slice)
- 2026-02-28: Updated `make install` to run dependency install, DB migrate, lint/typecheck/test, full phase gate, and build.
- 2026-02-28: Added `make install-deps` as a dependency-only setup path.
- 2026-02-28: Updated README and docs with new install semantics.
- 2026-02-28: Verified target surface and install sequence via `make help` and dry-run `make -n install`.
- 2026-02-28: Hardened `backend/scripts/ensure_db.sh` so `db-migrate` auto-starts/waits for compose Postgres (`localhost:5433`) and falls back to container-side `psql/createdb` when host CLI tools are unavailable.
- 2026-02-28: Verified DB bootstrap behavior with `make db-migrate` on a clean local compose Postgres startup.
- 2026-02-28: Updated `infra/docker-compose.yml` backend command to source `.data/mappo-azure.env` inside container startup (supports `export ...` format used by bootstrap script), fixing missing Azure creds during admin discovery in compose mode.

---

## Scope (Phase 5.24 Slice)
Admin discovery runtime compatibility fix:
- Resolve false "Azure SDK dependencies unavailable" error in compose/runtime discovery path.
- Keep auto-enumeration behavior without relying on unavailable `SubscriptionClient` symbols in current `azure-mgmt-resource`.

## Plan (Phase 5.24 Slice)
- [x] Replace brittle `SubscriptionClient` import path with ARM REST subscription enumeration using `ClientSecretCredential`.
- [x] Keep managed app discovery via `ResourceManagementClient` unchanged.
- [x] Re-run backend lint/typecheck/tests.
- [x] Validate discovery endpoint behavior in compose backend after restart.

## Verification Commands (Phase 5.24 Slice)
- [x] `make lint-backend`
- [x] `make typecheck-backend`
- [x] `make test-backend`
- [x] `docker compose -f infra/docker-compose.yml restart backend`
- [x] `curl -X POST http://localhost:8010/api/v1/admin/discover-import ...`

## Results Log (Phase 5.24 Slice)
- 2026-02-28: Switched subscription auto-enumeration to ARM REST (`/subscriptions?api-version=2022-12-01`) using the existing Azure credential token.
- 2026-02-28: Removed dependency on `azure.mgmt.resource.SubscriptionClient` import paths that are not present in current runtime package layout.
- 2026-02-28: Verified backend checks pass and compose admin discovery no longer fails with SDK dependency error (endpoint now returns discovery results/errors instead of dependency fault).

---

## Scope (Phase 5.25 Slice)
Admin discovery hardening for Azure paging/auth failures:
- Prevent unhandled exceptions from Azure pager iteration from surfacing as HTTP 500.
- Return controlled discovery errors/warnings (400) with blocked-scope context.

## Plan (Phase 5.25 Slice)
- [x] Wrap managed app resource paging in guarded list conversion with exception handling.
- [x] Wrap container app resource-group paging in guarded list conversion with exception handling.
- [x] Re-run backend checks and validate compose endpoint behavior.

## Verification Commands (Phase 5.25 Slice)
- [x] `make lint-backend`
- [x] `make typecheck-backend`
- [x] `make test-backend`
- [x] `docker compose -f infra/docker-compose.yml restart backend`
- [x] `curl -X POST http://localhost:8010/api/v1/admin/discover-import ...`

## Results Log (Phase 5.25 Slice)
- 2026-02-28: Fixed admin discovery crash path where Azure paging/auth exceptions during iterator traversal bypassed existing `try/except` and caused HTTP 500.
- 2026-02-28: Discovery now returns controlled non-500 responses for subscription/resource-group auth scope failures (including tenant mismatch cases).

---

## Scope (Phase 5.26 Slice)
Marketplace-realistic demo consolidation:
- Remove non-marketplace demo tracks from active workflow surface.
- Make Pulumi the primary IaC path for managed app definitions + managed app instances.
- Add explicit API/script boundary for Partner Center operations and a portal playbook for manual-only steps.

## Plan (Phase 5.26 Slice)
- [x] Replace Pulumi direct-ACA target model with managed-app definition + managed-app instance model and inventory exports.
- [x] Restore Pulumi Make targets and dual-stack generator aligned to the managed-app IaC model.
- [x] Remove obsolete Lighthouse/simulation scripts from active repo surface.
- [x] Add Partner Center API helper scripts and Make wrappers.
- [x] Update README/docs/checklists with clear IaC vs API vs portal boundaries.
- [x] Run verification commands and capture outcomes.

## Verification Commands (Phase 5.26 Slice)
- [x] `cd infra/pulumi && npm run build`
- [x] `bash -n scripts/iac_prepare_dual_stack.sh scripts/partner_center_get_token.sh scripts/partner_center_api.sh`
- [x] `make help`
- [x] `make lint`
- [x] `make typecheck`
- [x] `docker compose -f infra/docker-compose.yml up -d postgres migrate`
- [x] `make test`

## Results Log (Phase 5.26 Slice)
- 2026-02-28: Replaced `infra/pulumi/index.ts` provisioning model to deploy service-catalog managed app definitions and managed app instances (with ACA deployment defined inside managed app template), then export MAPPO-ready target inventory.
- 2026-02-28: Updated Pulumi config contract/files/docs (`Pulumi.yaml`, `Pulumi.dev.yaml`, `Pulumi.dual-demo.yaml`, `infra/pulumi/README.md`) for managed-app-first IaC and explicit publisher principal authorization.
- 2026-02-28: Reintroduced IaC command surface in Makefile (`iac-install`, `iac-stack-init`, `iac-preview`, `iac-up`, `iac-destroy`, `iac-export-targets`, `iac-prepare-dual-stack`).
- 2026-02-28: Replaced dual-stack generator script to emit managed-app-oriented target config and require publisher principal object ID input.
- 2026-02-28: Removed obsolete scripts not part of current marketplace demo track: Lighthouse delegation/undelegation and managed-app simulation bootstrap/teardown/seed scripts.
- 2026-02-28: Added Partner Center API helpers (`scripts/partner_center_get_token.sh`, `scripts/partner_center_api.sh`) and Make wrappers (`partner-center-token`, `partner-center-api`).
- 2026-02-28: Added portal/manual playbook at `/Users/cvonderheid/workspace/mappo/docs/marketplace-portal-playbook.md` and updated README/checklist/docs to show automation boundaries.
- 2026-02-28: Verified lint/typecheck/tests after starting local compose Postgres migration services; `make test` is green.

---

## Scope (Phase 5.27 Slice)
Operator error visibility in deployment run detail:
- Surface per-stage structured Azure error payloads (code/message/details) directly in the Deployments UI.
- Keep correlation IDs and Azure portal deep links visible per stage.
- Add Playwright coverage for failed-run error visibility.

## Plan (Phase 5.27 Slice)
- [x] Extend `Run Detail` stage cards to render stage message, correlation ID, portal link, and structured error block.
- [x] Add stable test IDs for run selection and stage error code rendering.
- [x] Update Playwright page object + mock API failed-run payload for structured error assertions.
- [x] Run frontend lint/typecheck/test/e2e checks.

## Verification Commands (Phase 5.27 Slice)
- [x] `cd frontend && npm run lint`
- [x] `cd frontend && npm run typecheck`
- [x] `cd frontend && npm run test -- --run`
- [x] `cd frontend && npm run test:e2e:ci`

## Results Log (Phase 5.27 Slice)
- 2026-02-28: Updated `/Users/cvonderheid/workspace/mappo/frontend/src/components/RunPanels.tsx` so each target stage now shows stage message, start/end timestamps, correlation ID, Azure portal link, and a structured error section with expandable JSON details.
- 2026-02-28: Added deterministic run-card selection hook (`data-testid="select-run-<id>"`) and stage error hook (`data-testid="stage-error-code-<target>-<stage>"`) for robust automation.
- 2026-02-28: Updated Playwright support fixtures to include realistic failed Azure deployment payload details (`MANIFEST_UNKNOWN`) and added coverage asserting structured error rendering in run detail.
- 2026-02-28: Verified frontend lint/typecheck/unit/e2e are green (with existing two non-blocking shadcn fast-refresh warnings).

---

## Scope (Phase 5.28 Slice)
Portal deep-link reliability in run-stage diagnostics:
- Replace brittle Azure Portal browse blade links with direct resource overview links.
- Ensure historical runs with legacy browse links are normalized at read time.

## Plan (Phase 5.28 Slice)
- [x] Update backend portal link builder to generate `#resource/.../overview` links (tenant-aware when tenant is a GUID).
- [x] Add run-detail normalization so legacy `BrowseResource` links are rewritten automatically.
- [x] Run backend lint/typecheck/tests and smoke-check emitted links.

## Verification Commands (Phase 5.28 Slice)
- [x] `uv run --package mappo-backend -- ruff check backend/app/modules/control_plane.py`
- [x] `uv run --package mappo-backend -- mypy app/modules/control_plane.py`
- [x] `uv run --package mappo-backend -- pytest -q`
- [x] `curl http://localhost:8010/api/v1/runs/<id>` (verify `portal_link` format)

## Results Log (Phase 5.28 Slice)
- 2026-02-28: Replaced `HubsExtension/BrowseResource` links in `/Users/cvonderheid/workspace/mappo/backend/app/modules/control_plane.py` with direct resource overview links to avoid portal `browsePrereqs` errors.
- 2026-02-28: Added portal-link normalization for historical run records so existing data is upgraded on read without DB migration.
- 2026-02-28: Verified emitted stage links now follow `https://portal.azure.com/#resource/<resourceId>/overview` format.

---

## Scope (Phase 5.29 Slice)
Admin discovery reliability + operator clarity:
- Fix managed application discovery to use the Microsoft.Solutions API path (not generic resource listing).
- Improve no-target error context so operators can see scanned subscriptions and active prefix filter.

## Plan (Phase 5.29 Slice)
- [x] Switch backend discovery from `ResourceManagementClient.resources.list(filter=...)` to paged ARM calls on `/providers/Microsoft.Solutions/applications`.
- [x] Preserve blocked-scope warnings for cross-tenant token mismatch cases.
- [x] Enrich no-target failure message with scan scope and prefix context.
- [x] Run backend lint/typecheck/tests and smoke the admin endpoint.

## Verification Commands (Phase 5.29 Slice)
- [x] `uv run --package mappo-backend -- ruff check backend/app/modules/discovery.py`
- [x] `uv run --package mappo-backend -- mypy app/modules/discovery.py`
- [x] `uv run --package mappo-backend -- pytest -q`
- [x] `curl -X POST http://localhost:8010/api/v1/admin/discover-import ...`

## Results Log (Phase 5.29 Slice)
- 2026-02-28: Updated `/Users/cvonderheid/workspace/mappo/backend/app/modules/discovery.py` to discover managed apps via ARM `Microsoft.Solutions/applications` endpoint with paging support.
- 2026-02-28: Admin discovery now succeeds for accessible subscriptions and returns precise blocked-scope detail for inaccessible subscriptions (tenant token mismatch).
- 2026-02-28: No-target failures now include actionable context (managed-app count, active prefix filter, scanned subscriptions).

---

## Scope (Phase 5.30 Slice)
Cross-tenant execution/discovery correctness:
- Replace single-tenant Azure credential assumptions with tenant-aware auth per subscription.
- Support explicit subscription-to-tenant mapping for live multi-tenant runs/discovery.
- Add preflight and docs clarity so operators can configure cross-tenant authority deterministically.

## Plan (Phase 5.30 Slice)
- [x] Extend backend Azure settings/runtime to resolve tenant authority per subscription and cache credentials per tenant.
- [x] Update admin discovery flow to enumerate and scan subscriptions using tenant-aware credential selection.
- [x] Add regression tests for tenant resolution and settings parsing.
- [x] Update preflight/docs/checklists to include `MAPPO_AZURE_TENANT_BY_SUBSCRIPTION`.
- [x] Run backend lint/typecheck/test and shell syntax verification.

## Verification Commands (Phase 5.30 Slice)
- [x] `uv run --package mappo-backend -- ruff check backend/app/modules/execution.py backend/app/modules/discovery.py backend/app/core/settings.py backend/app/main.py backend/app/api/routers/admin.py backend/tests/test_tenant_resolution.py backend/scripts/import_targets.py backend/scripts/bootstrap_releases.py backend/scripts/prune_retention.py backend/scripts/demo_reset.py`
- [x] `uv run --package mappo-backend -- mypy app/modules/execution.py app/modules/discovery.py app/core/settings.py app/main.py app/api/routers/admin.py`
- [x] `uv run --package mappo-backend -- pytest -q`
- [x] `bash -n scripts/azure_preflight.sh`

## Results Log (Phase 5.30 Slice)
- 2026-02-28: Added tenant resolution helpers and per-subscription tenant authority support in `/Users/cvonderheid/workspace/mappo/backend/app/modules/execution.py`; runtime now caches Azure credentials per tenant and binds subscription clients to resolved tenant authority.
- 2026-02-28: Updated `/Users/cvonderheid/workspace/mappo/backend/app/modules/discovery.py` to build credentials per tenant, enumerate subscriptions across candidate tenants, and resolve scan credentials per subscription instead of assuming one global tenant.
- 2026-02-28: Added settings support for `MAPPO_AZURE_TENANT_BY_SUBSCRIPTION` in `/Users/cvonderheid/workspace/mappo/backend/app/core/settings.py` and threaded it through runtime/admin entry points.
- 2026-02-28: Added regression coverage at `/Users/cvonderheid/workspace/mappo/backend/tests/test_tenant_resolution.py` for tenant resolution precedence and env-map parsing behavior.
- 2026-02-28: Hardened `/Users/cvonderheid/workspace/mappo/scripts/azure_preflight.sh` to detect non-authoritative inventory tenant IDs and fail when required subscription-to-tenant mappings are missing.
- 2026-02-28: Added `/Users/cvonderheid/workspace/mappo/scripts/azure_tenant_map.sh` + `make azure-tenant-map` to generate and persist `MAPPO_AZURE_TENANT_BY_SUBSCRIPTION` from Azure CLI subscription context.
- 2026-02-28: Added `/Users/cvonderheid/workspace/mappo/scripts/azure_onboard_multitenant_runtime.sh` + `make azure-onboard-multitenant-runtime` to automate cross-tenant app onboarding (multi-tenant audience, tenant-local service principal creation, and RBAC at subscription/resource-group scopes).
- 2026-02-28: Updated docs/checklists (`README.md`, `docs/documentation.md`, `docs/live-demo-checklist.md`, `docs/architecture.md`) with explicit cross-tenant tenant-mapping setup.

---

## Scope (Phase 5.31 Slice)
Marketplace registration-driven onboarding (remove auto-discovery path):
- Remove runtime/admin auto-discovery endpoint and UI flow.
- Add event-driven onboarding APIs for target registration (`/admin/onboarding`, `/admin/onboarding/events`).
- Persist onboarding registrations + event history in Postgres with Flyway migration.
- Keep MAPPO deploy/fleet behavior unchanged after target registration.

## Plan (Phase 5.31 Slice)
- [x] Replace admin discovery schemas/router with marketplace onboarding ingest + snapshot APIs.
- [x] Add Flyway migration + ORM models for `target_registrations` and `marketplace_events`.
- [x] Add backend ingestion logic with idempotency (`event_id`) and token-gated endpoint support.
- [x] Remove discovery module/script from active code surface.
- [x] Replace Admin UI form/workflow to register targets from event payloads and display registration/event state.
- [x] Regenerate OpenAPI + frontend client and update tests/docs/Makefile references.
- [x] Run verification and capture outcomes.

## Verification Commands (Phase 5.31 Slice)
- [x] `make db-migrate`
- [x] `make lint-backend`
- [x] `make typecheck-backend`
- [x] `make test-backend`
- [x] `make openapi`
- [x] `make client-gen`
- [x] `make lint-frontend`
- [x] `make typecheck-frontend`
- [x] `make test-frontend`
- [x] `make test`

## Results Log (Phase 5.31 Slice)
- 2026-02-28: Added onboarding schema/API model set in `/Users/cvonderheid/workspace/mappo/backend/app/modules/schemas.py` (`MarketplaceEventIngest*`, `TargetRegistrationRecord`, onboarding snapshot).
- 2026-02-28: Replaced admin router with onboarding endpoints in `/Users/cvonderheid/workspace/mappo/backend/app/api/routers/admin.py`.
- 2026-02-28: Added `MAPPO_MARKETPLACE_INGEST_TOKEN` runtime setting in `/Users/cvonderheid/workspace/mappo/backend/app/core/settings.py`.
- 2026-02-28: Added Flyway migration `/Users/cvonderheid/workspace/mappo/backend/flyway/sql/V2__marketplace_onboarding.sql` and updated generated ORM classes in `/Users/cvonderheid/workspace/mappo/backend/app/db/generated/models.py`.
- 2026-02-28: Implemented registration/event persistence and ingest normalization in `/Users/cvonderheid/workspace/mappo/backend/app/modules/control_plane.py`.
- 2026-02-28: Replaced backend admin tests with onboarding coverage in `/Users/cvonderheid/workspace/mappo/backend/tests/test_admin.py`.
- 2026-02-28: Removed discovery implementation files from active repo surface (`/Users/cvonderheid/workspace/mappo/backend/app/modules/discovery.py`, `/Users/cvonderheid/workspace/mappo/scripts/managed_app_discover_targets.sh`).
- 2026-02-28: Replaced frontend admin workflow with onboarding registration UX in `/Users/cvonderheid/workspace/mappo/frontend/src/components/AdminPanel.tsx` and `/Users/cvonderheid/workspace/mappo/frontend/src/App.tsx`.
- 2026-02-28: Updated API client/types and regenerated contract artifacts (`/Users/cvonderheid/workspace/mappo/backend/openapi/openapi.json`, `/Users/cvonderheid/workspace/mappo/frontend/src/lib/api/generated/schema.ts`).
- 2026-02-28: Updated docs/ops references in `/Users/cvonderheid/workspace/mappo/README.md`, `/Users/cvonderheid/workspace/mappo/docs/marketplace-portal-playbook.md`, `/Users/cvonderheid/workspace/mappo/docs/live-demo-checklist.md`, `/Users/cvonderheid/workspace/mappo/docs/architecture.md`, `/Users/cvonderheid/workspace/mappo/scripts/azure_preflight.sh`, and `/Users/cvonderheid/workspace/mappo/Makefile`.

---

## Scope (Phase 5.32 Slice)
Cloud DB parity for production-like runtime:
- Provision an optional Azure Database for PostgreSQL Flexible Server via Pulumi.
- Keep local Docker Postgres as the default dev/test path.
- Add reproducible script/Make flow to export managed DB env settings.
- Keep Flyway + ORM generation workflow unchanged.

## Plan (Phase 5.32 Slice)
- [x] Add Pulumi resources/config for optional control-plane managed Postgres.
- [x] Export managed DB connection metadata and password output from Pulumi.
- [x] Add script/Make target to generate `.data/mappo-db.env` from stack outputs.
- [x] Update DB migration path so managed DB mode does not assume local compose bootstrap.
- [x] Update docs (`README.md`, `infra/pulumi/README.md`, `docs/live-demo-checklist.md`) for managed DB setup/run.
- [x] Run verification and capture outcomes.

## Verification Commands (Phase 5.32 Slice)
- [x] `make iac-install`
- [x] `cd infra/pulumi && npm run build`
- [x] `make lint`
- [x] `make typecheck`
- [x] `make test`

## Results Log (Phase 5.32 Slice)
- 2026-02-28: Added optional managed Postgres provisioning in `/Users/cvonderheid/workspace/mappo/infra/pulumi/index.ts` (Flexible Server, database, firewall rules, and stack outputs for runtime env export).
- 2026-02-28: Added reproducible env export script `/Users/cvonderheid/workspace/mappo/scripts/iac_export_db_env.sh` and Make target `make iac-export-db-env` to produce `/Users/cvonderheid/workspace/mappo/.data/mappo-db.env`.
- 2026-02-28: Updated DB bootstrap/migration helpers to support managed DB settings without forcing local compose bootstrap (`/Users/cvonderheid/workspace/mappo/backend/scripts/ensure_db.sh`, `/Users/cvonderheid/workspace/mappo/backend/scripts/flyway.sh`).
- 2026-02-28: Updated runtime startup env sourcing to include DB env exports (`/Users/cvonderheid/workspace/mappo/scripts/with_mappo_azure_env.sh`, `/Users/cvonderheid/workspace/mappo/infra/docker-compose.yml`).
- 2026-02-28: Updated docs/checklists for managed DB enablement and command flow (`/Users/cvonderheid/workspace/mappo/README.md`, `/Users/cvonderheid/workspace/mappo/infra/pulumi/README.md`, `/Users/cvonderheid/workspace/mappo/docs/live-demo-checklist.md`, `/Users/cvonderheid/workspace/mappo/docs/architecture.md`).
- 2026-02-28: Standardized local default DB port to `5433` across runtime/session/test bootstrapping (`/Users/cvonderheid/workspace/mappo/backend/app/core/settings.py`, `/Users/cvonderheid/workspace/mappo/backend/app/db/session.py`, `/Users/cvonderheid/workspace/mappo/backend/tests/conftest.py`) to avoid accidental fallback to unrelated `5432` instances.

---

## Scope (Phase 5.33 Slice)
Demo stack correctness + legacy demo default cleanup:
- Eliminate accidental `demo10` default behavior for active stacks.
- Add reproducible stack configurator for 2-target cross-tenant marketplace demo (tenant-local principal object IDs).
- Ensure Pulumi config path prevents principal mismatch failures in customer tenant definitions.
- Update docs/commands to use the new deterministic stack prep flow.

## Plan (Phase 5.33 Slice)
- [x] Add script + Make target to configure a 2-target marketplace demo stack with tenant-local principal mapping.
- [x] Switch Pulumi defaults away from `demo10` to prevent accidental 10-target provisioning.
- [x] Update docs/checklists with the new stack configuration flow.
- [x] Run verification (`bash -n`, lint/typecheck/test, checks) and capture outcomes.

## Verification Commands (Phase 5.33 Slice)
- [x] `bash -n scripts/iac_configure_marketplace_demo.sh`
- [x] `make lint`
- [x] `make typecheck`
- [x] `make test`
- [x] `make workflow-discipline-check`

## Results Log (Phase 5.33 Slice)
- 2026-02-28: Added deterministic stack prep script `/Users/cvonderheid/workspace/mappo/scripts/iac_configure_marketplace_demo.sh` that resolves tenant-local service principal object IDs per subscription and configures a 2-target cross-tenant stack (`mappo:targets` + `mappo:publisherPrincipalObjectIds`).
- 2026-02-28: Added Make target `make iac-configure-marketplace-demo` in `/Users/cvonderheid/workspace/mappo/Makefile` for reproducible stack configuration without manual Pulumi JSON editing.
- 2026-02-28: Switched Pulumi defaults to `mappo:targetProfile=empty` in `/Users/cvonderheid/workspace/mappo/infra/pulumi/Pulumi.yaml` and `/Users/cvonderheid/workspace/mappo/infra/pulumi/Pulumi.dev.yaml` to prevent accidental 10-target provisioning.
- 2026-02-28: Updated demo docs to use the new stack prep flow in `/Users/cvonderheid/workspace/mappo/README.md`, `/Users/cvonderheid/workspace/mappo/infra/pulumi/README.md`, and `/Users/cvonderheid/workspace/mappo/docs/live-demo-checklist.md`.
- 2026-02-28: Verified `bash -n`, `make lint`, `make typecheck`, `make test`, and discipline checks pass after the stack-config changes.
- 2026-02-28: Applied `make iac-configure-marketplace-demo ...` + `make iac-up PULUMI_STACK=demo`; stack now converges with 2 managed app targets and no cross-tenant principal mismatch failures.
- 2026-02-28: Added Postgres server create timeout hardening (`30m`) in `/Users/cvonderheid/workspace/mappo/infra/pulumi/index.ts` to avoid Azure long-running create timeouts.
- 2026-02-28: Added managed Postgres local-connectivity support by auto-configuring current public IP firewall allowlist via stack configurator and applying a custom firewall rule (`control-plane-postgres-fw-custom-*`).
- 2026-02-28: Corrected managed Postgres connection username output for Flexible Server (use `adminLogin`, not `adminLogin@server`) and confirmed DB connectivity with exported env settings.
- 2026-02-28: Added `azure.extensions=PGCRYPTO` server configuration in Pulumi so Flyway baseline migration succeeds on Azure Flexible Server.
- 2026-02-28: Verified managed DB path end-to-end: `make iac-export-db-env`, `source .data/mappo-db.env`, `make db-migrate`, `make import-targets`, `make bootstrap-releases` (DB now contains 2 targets + 2 releases).

---

## Scope (Phase 5.34 Slice)
Preflight signal cleanup for current 2-target demo phase:
- Remove hardcoded 10-target warning in `azure-preflight`.
- Make expected target count configurable for 2-target vs 10-target rehearsals.

## Plan (Phase 5.34 Slice)
- [x] Update preflight target-count expectation to be environment-driven with a safe default for current phase.
- [x] Update docs to describe the override knob for full-scale rehearsals.
- [x] Validate script syntax and run preflight to confirm warning removal.

## Verification Commands (Phase 5.34 Slice)
- [x] `bash -n scripts/azure_preflight.sh`
- [x] `make azure-preflight`

## Results Log (Phase 5.34 Slice)
- 2026-02-28: Updated `/Users/cvonderheid/workspace/mappo/scripts/azure_preflight.sh` to use `MAPPO_PREFLIGHT_EXPECTED_TARGET_COUNT` (default `2`) instead of hardcoded `~10` warning threshold.
- 2026-02-28: Updated docs in `/Users/cvonderheid/workspace/mappo/README.md` and `/Users/cvonderheid/workspace/mappo/docs/live-demo-checklist.md` to document the preflight target-count override.
- 2026-02-28: Verified `make azure-preflight` now reports `PASS: Inventory has 2 targets.` with `0 warning(s)` for the current demo profile.

---

## Scope (Phase 5.35 Slice)
Portal-link UX cleanup consistency:
- Remove remaining "Open in Azure Portal" links from deployment run stage cards.
- Keep operators focused on in-app logs/correlation IDs.

## Plan (Phase 5.35 Slice)
- [x] Remove residual portal-link anchor rendering from run detail stage UI.
- [x] Run frontend typecheck and tests.

## Verification Commands (Phase 5.35 Slice)
- [x] `cd frontend && npm run typecheck`
- [x] `cd frontend && npm run test`

## Results Log (Phase 5.35 Slice)
- 2026-03-01: Removed remaining "Open in Azure Portal" link from `/Users/cvonderheid/workspace/mappo/frontend/src/components/RunPanels.tsx` (`TargetRecordCard` stage metadata row).
- 2026-03-01: Verified frontend checks pass (`npm run typecheck`, `npm run test`).

---

## Scope (Phase 5.36 Slice)
Operator-visible Azure diagnostics in MAPPO run logs:
- Surface concrete Azure API error code/message/IDs in deployment failure events.
- Emit those diagnostics as first-class per-target log entries (no portal hop required).
- Improve run-detail log readability to show severity inline.

## Plan (Phase 5.36 Slice)
- [x] Enrich Azure HTTP exception translation with parsed response headers/body metadata.
- [x] Append normalized Azure diagnostic lines into per-target execution logs when stages fail.
- [x] Update run-detail UI to expose diagnostic summaries and log severity inline.
- [x] Add/extend backend regression coverage for diagnostic log emission.
- [x] Run backend/frontend verification and capture outcomes.

## Verification Commands (Phase 5.36 Slice)
- [x] `make lint-backend`
- [x] `make typecheck`
- [x] `make test-backend`
- [x] `make lint-frontend`
- [x] `make test-frontend`

## Results Log (Phase 5.36 Slice)
- 2026-03-01: Added Azure error-context extraction in `/Users/cvonderheid/workspace/mappo/backend/app/modules/execution.py` (response header IDs, parsed ARM error payload, and message enrichment).
- 2026-03-01: Updated `/Users/cvonderheid/workspace/mappo/backend/app/modules/control_plane.py` to append operator-facing Azure diagnostic lines (error code/message, HTTP status, request/correlation IDs, detail entries) to run logs on failed stages.
- 2026-03-01: Updated `/Users/cvonderheid/workspace/mappo/frontend/src/components/RunPanels.tsx` to show inline Azure error summary fields and log-level column, with a larger recent-log window.
- 2026-03-01: Extended deploy-failure regression in `/Users/cvonderheid/workspace/mappo/backend/tests/test_execution_modes.py` to assert emitted diagnostic log lines.
- 2026-03-01: Verified `make lint-backend`, `make typecheck`, `make test-backend`, `make lint-frontend`, and `make test-frontend` pass (frontend retains existing 2 baseline warnings in shadcn primitives).

---

## Scope (Phase 5.37 Slice)
Deployment UX split + clarity refinements:
- Move deployment controls into a top drawer so `/deployments` focuses on historical run management.
- Move run detail into a dedicated route (`/deployments/:runId`).
- Collapse guardrail warning blocks into accordion sections.
- Clarify success-stage and correlation-id log wording.

## Plan (Phase 5.37 Slice)
- [x] Add top-drawer deployment controls surface and wire existing start-run form + target filters into it.
- [x] Move run detail to dedicated route and navigation flow from run cards.
- [x] Replace expanded guardrail warnings with collapsible accordion sections.
- [x] Clarify success-stage start message in executor and correlation-id label in UI.
- [x] Update unit and Playwright POM tests for the new IA.
- [x] Run verification and capture outcomes.

## Verification Commands (Phase 5.37 Slice)
- [x] `make lint-backend`
- [x] `make test-backend`
- [x] `make typecheck`
- [x] `make lint-frontend`
- [x] `make test-frontend`
- [x] `make test-frontend-e2e`

## Results Log (Phase 5.37 Slice)
- 2026-03-01: Updated deployment navigation/IA in `/Users/cvonderheid/workspace/mappo/frontend/src/App.tsx` so controls live in a top drawer and run detail is a separate route (`/deployments/:runId`) with back navigation.
- 2026-03-01: Added reusable UI primitives `/Users/cvonderheid/workspace/mappo/frontend/src/components/ui/drawer.tsx` and `/Users/cvonderheid/workspace/mappo/frontend/src/components/ui/accordion.tsx`.
- 2026-03-01: Updated `/Users/cvonderheid/workspace/mappo/frontend/src/components/RunPanels.tsx` to render guardrail warnings as accordions, add explicit "View Run Details" actions, and label stage metadata as `correlation-id`.
- 2026-03-01: Updated executor success-start wording in `/Users/cvonderheid/workspace/mappo/backend/app/modules/execution.py` from "Succeeded started." to "Finalizing success state.".
- 2026-03-01: Updated browser-flow coverage (`/Users/cvonderheid/workspace/mappo/frontend/e2e/pages/deployments.page.ts`, `/Users/cvonderheid/workspace/mappo/frontend/e2e/tests/core-flows.spec.ts`) and app unit test (`/Users/cvonderheid/workspace/mappo/frontend/src/App.test.tsx`) for drawer and detail-route behavior.
- 2026-03-01: Verified `make lint-backend`, `make test-backend`, `make typecheck`, `make lint-frontend`, `make test-frontend`, and `make test-frontend-e2e` all pass.
- 2026-03-01: Replaced interim custom drawer/accordion shims with official shadcn-backed implementations (`vaul` + Radix Accordion) in `/Users/cvonderheid/workspace/mappo/frontend/src/components/ui/drawer.tsx` and `/Users/cvonderheid/workspace/mappo/frontend/src/components/ui/accordion.tsx`; added `window.matchMedia` test polyfill in `/Users/cvonderheid/workspace/mappo/frontend/src/testSetup.ts` for vaul in jsdom.

---

## Scope (Phase 5.38 Slice)
Deployment-control simplification:
- Remove the standalone `Target Scope` control.
- Make specific-target selection an optional refinement under `Target Group`.
- Keep run request semantics clear: no specific selection means full target-group deployment.

## Plan (Phase 5.38 Slice)
- [x] Remove `Target Scope` state/selector from deployment controls.
- [x] Always show optional specific-target picker under target-group filter.
- [x] Update run creation request logic for optional specific-target IDs.
- [x] Update unit and Playwright tests for the simplified control flow.
- [x] Run verification and capture outcomes.

## Verification Commands (Phase 5.38 Slice)
- [x] `make lint-frontend`
- [x] `make typecheck`
- [x] `make test-frontend`
- [x] `make test-frontend-e2e`

## Results Log (Phase 5.38 Slice)
- 2026-03-01: Removed `Target Scope` from `/Users/cvonderheid/workspace/mappo/frontend/src/App.tsx` and simplified run creation so `target_ids` are only sent when optional specific targets are selected.
- 2026-03-01: Reworked deployment drawer controls to keep `Target Group` primary and place `Specific Targets (optional)` directly beneath it.
- 2026-03-01: Updated Playwright page object and flows in `/Users/cvonderheid/workspace/mappo/frontend/e2e/pages/deployments.page.ts` and `/Users/cvonderheid/workspace/mappo/frontend/e2e/tests/core-flows.spec.ts` for the new selection model.
- 2026-03-01: Updated app unit expectation in `/Users/cvonderheid/workspace/mappo/frontend/src/App.test.tsx`.
- 2026-03-01: Verified `make lint-frontend`, `make typecheck`, `make test-frontend`, and `make test-frontend-e2e` pass.

---

## Scope (Phase 5.39 Slice)
Fleet filtering UX modernization:
- Replace dedicated Fleet filter card with per-column filters in a shadcn-style data table.
- Keep deployment targeting filters in Deployments drawer only.

## Plan (Phase 5.39 Slice)
- [x] Remove standalone Fleet Target Filters section from app shell.
- [x] Introduce Fleet data table with per-column filter controls and sortable headers.
- [x] Decouple deployment target-group filtering from Fleet page rendering.
- [x] Update API usage to fetch full fleet inventory and filter client-side in Fleet table.
- [x] Run verification and capture outcomes.

## Verification Commands (Phase 5.39 Slice)
- [x] `make lint-frontend`
- [x] `make typecheck`
- [x] `make test-frontend`
- [x] `make test-frontend-e2e`

## Results Log (Phase 5.39 Slice)
- 2026-03-01: Removed dedicated Fleet Target Filters card from `/Users/cvonderheid/workspace/mappo/frontend/src/App.tsx`.
- 2026-03-01: Added TanStack-backed shadcn-style Fleet data table with per-column filters/sorting in `/Users/cvonderheid/workspace/mappo/frontend/src/components/FleetTable.tsx`.
- 2026-03-01: Updated target fetching/filter flow in `/Users/cvonderheid/workspace/mappo/frontend/src/App.tsx` and `/Users/cvonderheid/workspace/mappo/frontend/src/lib/api.ts` to fetch full target list and apply deployment-group filtering only where needed.
- 2026-03-01: Added `@tanstack/react-table` dependency in `/Users/cvonderheid/workspace/mappo/frontend/package.json`.
- 2026-03-01: Verified `make lint-frontend`, `make typecheck`, `make test-frontend`, and `make test-frontend-e2e` pass.

---

## Scope (Phase 5.40 Slice - Feature Request)
Pre-deploy database backup + rollback metadata:
- Add a per-target pre-deploy backup stage (CodeDeploy-style `BeforeInstall` equivalent).
- Capture backup artifacts/restore points with release + Flyway version metadata.
- Support operator-visible backup status and backup identifiers in run logs/detail.
- Gate deployment on backup policy (`required` vs `best-effort`) for each run.

## Plan (Phase 5.40 Slice - Feature Request)
- [ ] Extend per-target state machine with `BACKING_UP` stage before `DEPLOYING`.
- [ ] Add backup adapter interface with initial Postgres implementation.
- [ ] Persist backup records tied to target/run/release/Flyway version.
- [ ] Add rollback metadata plumbing in API + UI run detail views.
- [ ] Add run-level policy knobs for backup required/best-effort semantics.
- [ ] Add backend tests for backup failure gating and rollback record creation.
- [ ] Add docs for backup/restore operational flow and guardrails.

## Acceptance Criteria (Phase 5.40 Slice - Feature Request)
- [ ] A deployment run can execute a backup stage per target before deploy.
- [ ] Backup result is visible in MAPPO logs/details with artifact or restore-point ID.
- [ ] If policy is `required`, target deployment does not proceed when backup fails.
- [ ] Rollback workflow can reference the recorded backup by release + Flyway version.

---

## Scope (Phase 5.41 Slice)
Marketplace-production parity for registration/preflight:
- Make Azure preflight default to marketplace event-driven model instead of inventory-first assumptions.
- Add a scriptable webhook-simulation path that registers targets through onboarding events.
- Update docs/checklists so primary demo flow no longer depends on direct `import-targets`.

## Plan (Phase 5.41 Slice)
- [x] Add `MAPPO_PREFLIGHT_MODE` support to `azure_preflight.sh` with `marketplace` default and `inventory` strict mode.
- [x] Keep inventory checks optional/non-blocking in marketplace mode.
- [x] Add script + Make target to ingest onboarding events from inventory through `/api/v1/admin/onboarding/events`.
- [x] Update README + playbooks/checklists to make event-driven registration the default demo path.
- [x] Run verification and capture outcomes.

## Verification Commands (Phase 5.41 Slice)
- [x] `bash -n scripts/azure_preflight.sh`
- [x] `bash -n scripts/marketplace_ingest_events.sh`
- [x] `make marketplace-ingest-events DRY_RUN=1`
- [x] `MAPPO_PREFLIGHT_MODE=marketplace MAPPO_TARGET_INVENTORY_PATH=/tmp/does-not-exist MAPPO_AZURE_ENV_FILE=/tmp/does-not-exist ./scripts/azure_preflight.sh || true`

## Results Log (Phase 5.41 Slice)
- 2026-03-01: Updated `/Users/cvonderheid/workspace/mappo/scripts/azure_preflight.sh` to support `MAPPO_PREFLIGHT_MODE` (default `marketplace`) and treat missing inventory as expected for event-driven onboarding.
- 2026-03-01: Added parser validation for `MAPPO_AZURE_TENANT_BY_SUBSCRIPTION` and explicit warning for unset `MAPPO_MARKETPLACE_INGEST_TOKEN`.
- 2026-03-01: Added webhook-simulation script `/Users/cvonderheid/workspace/mappo/scripts/marketplace_ingest_events.sh` and Make target `marketplace-ingest-events` to post onboarding events via production API path.
- 2026-03-01: Updated `/Users/cvonderheid/workspace/mappo/README.md`, `/Users/cvonderheid/workspace/mappo/docs/live-demo-checklist.md`, `/Users/cvonderheid/workspace/mappo/docs/marketplace-portal-playbook.md`, and `/Users/cvonderheid/workspace/mappo/docs/documentation.md` so event-driven registration is the default marketplace demo workflow.
- 2026-03-01: Updated `/Users/cvonderheid/workspace/mappo/scripts/azure_onboard_multitenant_runtime.sh` wording from discovery-focused to validation/quota-check scope for Reader role.

---

## Scope (Phase 5.42 Slice)
Repeatable teardown for live demo reset:
- Add scriptable cleanup for Entra/service-principal artifacts created during multi-tenant runtime onboarding.
- Ensure teardown includes role-assignment cleanup plus optional app-registration deletion.
- Document complete reset sequence (IaC + identity + local volumes).

## Plan (Phase 5.42 Slice)
- [x] Add Azure identity cleanup script for runtime app client ID across target subscriptions.
- [x] Add Make target wrapper with required arguments and optional app/env deletion flags.
- [x] Update docs/checklist with repeatable teardown command sequence.
- [x] Run script syntax and command surface verification.

## Verification Commands (Phase 5.42 Slice)
- [x] `bash -n scripts/azure_cleanup_runtime_identity.sh`
- [x] `make help | rg -n "azure-cleanup-runtime-identity|azure-onboard-multitenant-runtime|iac-destroy"`

## Results Log (Phase 5.42 Slice)
- 2026-03-01: Added `/Users/cvonderheid/workspace/mappo/scripts/azure_cleanup_runtime_identity.sh` to remove runtime SP role assignments in target subscriptions, delete tenant-local SP objects, optionally delete the home-tenant app registration, and optionally remove local Azure env file.
- 2026-03-01: Added Make target `azure-cleanup-runtime-identity` in `/Users/cvonderheid/workspace/mappo/Makefile`.
- 2026-03-01: Updated teardown docs in `/Users/cvonderheid/workspace/mappo/README.md`, `/Users/cvonderheid/workspace/mappo/docs/documentation.md`, and `/Users/cvonderheid/workspace/mappo/docs/live-demo-checklist.md`.

---

## Scope (Phase 5.43 Slice)
IaC resilience for managed Postgres provisioning:
- Reduce transient Azure `ServerIsBusy` failures during Postgres server/database/configuration creation.
- Keep stack apply deterministic for repeat demo resets.

## Plan (Phase 5.43 Slice)
- [x] Sequence Postgres extension configuration after database creation.
- [x] Add configuration resource timeouts to tolerate Azure control-plane lag.
- [x] Build-check Pulumi TypeScript program.

## Verification Commands (Phase 5.43 Slice)
- [x] `cd infra/pulumi && npm run build`

## Results Log (Phase 5.43 Slice)
- 2026-03-01: Updated `/Users/cvonderheid/workspace/mappo/infra/pulumi/index.ts` so `azure-native:dbforpostgresql:Configuration` depends on database creation and includes create/update timeouts; this avoids parallel server operations that caused `ServerIsBusy` errors.
- 2026-03-01: Verified Pulumi program compiles (`npm run build`).

---

## Scope (Phase 5.44 Slice - UX Follow-ups)
Operator UX and state-consistency fixes from live demo notes:
- Improve Admin onboarding visibility and feedback.
- Add stronger form validation and action-state affordances.
- Improve global in-app feedback with toasts.
- Fix fleet health status refresh after successful deployment runs.

## Plan (Phase 5.44 Slice - UX Follow-ups)
- [ ] Refactor Admin "Recent Onboarding Events" to a dedicated shadcn table view.
- [ ] Disable "Register from Event" until required fields are valid and complete.
- [ ] Add explicit success/error feedback for "Refresh Snapshot" so action outcome is visible.
- [ ] Add shadcn toast provider and use toasts for key user actions (run start, resume/retry, onboarding ingest, refresh, failures).
- [ ] Fix target health-status transition so a successfully deployed target no longer remains `registered` in Fleet (set healthy/degraded policy and persist consistently).
- [ ] Add/extend tests for Admin action enablement, snapshot refresh feedback, and fleet health-state update after successful runs.

## Acceptance Criteria (Phase 5.44 Slice - UX Follow-ups)
- [ ] Admin recent events render in a sortable/filterable table.
- [ ] Register action cannot be submitted with missing required fields.
- [ ] Refresh Snapshot always provides visible user feedback.
- [ ] Toasts appear for both success and failure paths on core operations.
- [ ] After a successful deployment, Fleet health for that target updates from `registered` to runtime health state.

---

## Scope (Phase 5.45 Slice)
Fleet health-state correctness after run completion:
- Ensure target `health_status` transitions from onboarding `registered` state to runtime state on deployment terminal events.
- Cover regression with onboarding -> deploy -> fleet assertion test.

## Plan (Phase 5.45 Slice)
- [x] Update terminal stage handling to persist target health updates on success/failure.
- [x] Add regression test for onboarding-registered target becoming healthy after successful run.
- [x] Run backend test coverage for run execution path.

## Verification Commands (Phase 5.45 Slice)
- [x] `cd backend && uv run --package mappo-backend -- pytest -q tests/test_runs.py`

## Results Log (Phase 5.45 Slice)
- 2026-03-01: Updated `/Users/cvonderheid/workspace/mappo/backend/app/modules/control_plane.py` so terminal stage updates set target health (`healthy` on success, `degraded` on failure) and persist `last_check_in_at`.
- 2026-03-01: Added onboarding regression in `/Users/cvonderheid/workspace/mappo/backend/tests/test_runs.py` verifying a target registered via onboarding transitions from `registered` to `healthy` after a successful run.
- 2026-03-01: Verified `tests/test_runs.py` passes.

---

## Scope (Phase 5.46 Slice)
Deployment drawer UX polish:
- Auto-close Deployment Controls drawer after successful run creation.
- Keep drawer open when immediate validation/API errors occur.

## Plan (Phase 5.46 Slice)
- [x] Update start-run success path to close the deployment drawer.
- [x] Verify frontend unit tests and TypeScript checks pass.

## Verification Commands (Phase 5.46 Slice)
- [x] `cd frontend && npm run test`
- [x] `cd frontend && npm run typecheck`

## Results Log (Phase 5.46 Slice)
- 2026-03-01: Updated `/Users/cvonderheid/workspace/mappo/frontend/src/App.tsx` to call `setDeploymentControlsOpen(false)` after successful `createRun` + refresh flow.
- 2026-03-01: Verified frontend tests and typecheck pass.

---

## Scope (Phase 5.47 Slice)
Deployment run-card density cleanup:
- Hide action buttons when they no longer apply for successful runs.
- Reduce the visual footprint of the run-details action.

## Plan (Phase 5.47 Slice)
- [x] Update run-card action rendering so successful runs do not render Resume/Retry controls.
- [x] Make "View Run Details" a compact button instead of full-width.
- [x] Update e2e expectations for succeeded-run action visibility.
- [x] Run frontend unit, typecheck, and e2e checks.

## Verification Commands (Phase 5.47 Slice)
- [x] `cd frontend && npm run test`
- [x] `cd frontend && npm run typecheck`
- [x] `cd frontend && npx playwright install chromium && npx playwright test e2e/tests/core-flows.spec.ts --reporter=line`

## Results Log (Phase 5.47 Slice)
- 2026-03-01: Updated `/Users/cvonderheid/workspace/mappo/frontend/src/components/RunPanels.tsx` to hide Resume/Retry controls on succeeded runs and render a compact non-full-width "View Run Details" action.
- 2026-03-01: Updated `/Users/cvonderheid/workspace/mappo/frontend/e2e/tests/core-flows.spec.ts` to assert succeeded-run Resume/Retry controls are not rendered.
- 2026-03-01: Verified frontend test, typecheck, and targeted Playwright core-flow spec pass.

---

## Scope (Phase 5.48 Slice)
Deployment runs table + clone-prepopulate workflow:
- Replace tall historical run cards with a compact data table.
- Move row actions into a contextual `...` menu.
- Add clone workflow that opens Deployment Controls pre-populated from source run configuration.

## Plan (Phase 5.48 Slice)
- [x] Convert `RunList` to TanStack/shadcn table layout with compact columns and filters.
- [x] Add row-level actions menu (`View Run Details`, `Clone Run`, conditional `Resume`/`Retry Failed`).
- [x] Implement clone handler in App shell to fetch run detail and pre-populate Deployment Controls.
- [x] Update unit/e2e tests and page objects for table/action-menu interaction model.
- [x] Run frontend test, typecheck, and Playwright core-flow verification.

## Verification Commands (Phase 5.48 Slice)
- [x] `cd frontend && npm run test`
- [x] `cd frontend && npm run typecheck`
- [x] `cd frontend && npx playwright test e2e/tests/core-flows.spec.ts --reporter=line`

## Results Log (Phase 5.48 Slice)
- 2026-03-01: Updated `/Users/cvonderheid/workspace/mappo/frontend/src/components/RunPanels.tsx` to render deployment history as a compact data table with shadcn dropdown actions and condensed progress/guardrail presentation.
- 2026-03-01: Added shadcn dropdown primitive in `/Users/cvonderheid/workspace/mappo/frontend/src/components/ui/dropdown-menu.tsx` and dependency `@radix-ui/react-dropdown-menu` in frontend package manifest.
- 2026-03-01: Updated `/Users/cvonderheid/workspace/mappo/frontend/src/App.tsx` to support `Clone Run` prepopulation flow (fetch run detail, set release/strategy/concurrency/stop policy/targets, open Deployment Controls).
- 2026-03-01: Updated `/Users/cvonderheid/workspace/mappo/frontend/e2e/pages/deployments.page.ts`, `/Users/cvonderheid/workspace/mappo/frontend/e2e/tests/core-flows.spec.ts`, and `/Users/cvonderheid/workspace/mappo/frontend/src/App.test.tsx` for table/actions behavior and clone regression coverage.

---

## Scope (Phase 5.49 Slice)
Run actions menu reliability and theming fix:
- Fix transparent/non-interactive actions dropdown in deployments table.
- Align dropdown behavior with current MAPPO theme tokens and Radix selection semantics.

## Plan (Phase 5.49 Slice)
- [x] Add missing popover theme tokens to frontend CSS variables.
- [x] Harden dropdown content/item styling for visible solid surface and pointer interaction.
- [x] Switch row-action handlers from click to Radix `onSelect` for consistent menu-item activation.
- [x] Re-run frontend test, typecheck, and Playwright core flows.

## Verification Commands (Phase 5.49 Slice)
- [x] `cd frontend && npm run test`
- [x] `cd frontend && npm run typecheck`
- [x] `cd frontend && npx playwright test e2e/tests/core-flows.spec.ts --reporter=line`

## Results Log (Phase 5.49 Slice)
- 2026-03-01: Updated `/Users/cvonderheid/workspace/mappo/frontend/src/styles.css` with `--popover` and `--popover-foreground` tokens to match shadcn dropdown expectations.
- 2026-03-01: Updated `/Users/cvonderheid/workspace/mappo/frontend/src/components/ui/dropdown-menu.tsx` to enforce visible layer styling (`z-index`, pointer events, pointer cursor).
- 2026-03-01: Updated `/Users/cvonderheid/workspace/mappo/frontend/src/components/RunPanels.tsx` to use `onSelect` for row actions (`View`, `Clone`, `Resume`, `Retry Failed`).
- 2026-03-01: Verified frontend unit, typecheck, and Playwright core-flow tests pass.

---

## Scope (Phase 5.50 Slice)
Deployments table selection clarity:
- Remove sticky run-row highlight after opening run detail.

## Plan (Phase 5.50 Slice)
- [x] Remove selected-row styling from deployment runs table.
- [x] Remove now-unused selected-row prop plumbing.
- [x] Re-run frontend test and typecheck.

## Verification Commands (Phase 5.50 Slice)
- [x] `cd frontend && npm run test`
- [x] `cd frontend && npm run typecheck`

## Results Log (Phase 5.50 Slice)
- 2026-03-01: Updated `/Users/cvonderheid/workspace/mappo/frontend/src/components/RunPanels.tsx` to remove persistent selected-row highlight in runs table.
- 2026-03-01: Updated `/Users/cvonderheid/workspace/mappo/frontend/src/App.tsx` to remove unused selected-run prop path from deployments list rendering.
- 2026-03-01: Verified frontend tests and typecheck pass.

---

## Scope (Phase 5.51 Slice)
Stabilize deployments row-actions interaction under auto-refresh:
- Prevent actions dropdown from collapsing/losing clickability while polling rerenders the runs table.

## Plan (Phase 5.51 Slice)
- [x] Reproduce issue via Playwright CLI interaction on live UI.
- [x] Add controlled actions-menu open state in `RunList`.
- [x] Pause deployments polling while actions menu or deployment drawer is open.
- [x] Add regression assertion that actions menu stays visible beyond one refresh interval.
- [x] Re-run frontend unit, typecheck, and Playwright core flows.

## Verification Commands (Phase 5.51 Slice)
- [x] `cd frontend && npm run test`
- [x] `cd frontend && npm run typecheck`
- [x] `cd frontend && npx playwright test e2e/tests/core-flows.spec.ts --reporter=line`
- [x] `"$PWCLI" run-code "async function (page) { ...actions click probe... }"`

## Results Log (Phase 5.51 Slice)
- 2026-03-01: Reproduced non-clickable actions flow with Playwright CLI and identified refresh-driven rerender/menu teardown.
- 2026-03-01: Updated `/Users/cvonderheid/workspace/mappo/frontend/src/components/RunPanels.tsx` to control dropdown open state and publish open/close state to parent.
- 2026-03-01: Updated `/Users/cvonderheid/workspace/mappo/frontend/src/App.tsx` polling effect to pause refresh while the deployments drawer or row-actions menu is open on `/deployments`.
- 2026-03-01: Extended `/Users/cvonderheid/workspace/mappo/frontend/e2e/tests/core-flows.spec.ts` to assert actions menu remains available after refresh interval.
- 2026-03-01: Verified tests and Playwright flow pass.

---

## Scope (Phase 5.52 Slice)
Dropdown visual integrity fix for run actions:
- Resolve transparent actions menu surface in deployments table.

## Plan (Phase 5.52 Slice)
- [x] Add missing Tailwind semantic color mappings for shadcn popover/accent tokens.
- [x] Add resilient dropdown style fallback to existing card/muted tokens.
- [x] Verify computed menu surface style and interaction in live browser automation.
- [x] Re-run frontend test, typecheck, and core e2e flow.

## Verification Commands (Phase 5.52 Slice)
- [x] `cd frontend && npm run test`
- [x] `cd frontend && npm run typecheck`
- [x] `cd frontend && npx playwright test e2e/tests/core-flows.spec.ts --reporter=line`
- [x] `"$PWCLI" run-code "async function (page) { ...menu style probe... }"`

## Results Log (Phase 5.52 Slice)
- 2026-03-01: Updated `/Users/cvonderheid/workspace/mappo/frontend/tailwind.config.ts` to include `popover` and `accent` color mappings.
- 2026-03-01: Updated `/Users/cvonderheid/workspace/mappo/frontend/src/styles.css` to define `--accent` and `--accent-foreground` tokens.
- 2026-03-01: Updated `/Users/cvonderheid/workspace/mappo/frontend/src/components/ui/dropdown-menu.tsx` to use robust `bg-card`/`text-card-foreground` and `focus:bg-muted` fallback styles.
- 2026-03-01: Verified in Playwright that menu style resolves to opaque background (`rgb(16, 33, 45)`), `opacity:1`, and `pointer-events:auto`.

---

## Scope (Phase 5.53 Slice)
Deployment/Admin IA alignment:
- Rename deployments primary CTA to `New Deployment`.
- Refactor Admin to match page pattern: top-action drawer for onboarding CRUD and tabbed datatable views for snapshot data.

## Plan (Phase 5.53 Slice)
- [x] Rename deployments drawer trigger label to `New Deployment` and update affected test copy checks.
- [x] Add shadcn tabs primitive and dependencies.
- [x] Rebuild Admin panel with a top drawer (`New Onboarding Event`) for event registration workflow.
- [x] Convert Admin `Registered Targets` and `Recent Onboarding Events` into tabbed datatables with sortable/filterable columns.
- [x] Run frontend unit tests, typecheck, and core e2e flow validation.

## Verification Commands (Phase 5.53 Slice)
- [x] `cd frontend && npm run test`
- [x] `cd frontend && npm run typecheck`
- [x] `cd frontend && npx playwright test e2e/tests/core-flows.spec.ts --reporter=line`

## Results Log (Phase 5.53 Slice)
- 2026-03-01: Updated `/Users/cvonderheid/workspace/mappo/frontend/src/App.tsx` deployment drawer trigger text to `New Deployment`.
- 2026-03-01: Replaced `/Users/cvonderheid/workspace/mappo/frontend/src/components/AdminPanel.tsx` with drawer-driven onboarding form + shadcn tabs containing sortable/filterable datatables for registrations and events.
- 2026-03-01: Added shadcn tabs primitive in `/Users/cvonderheid/workspace/mappo/frontend/src/components/ui/tabs.tsx` and Radix dependency updates in frontend package manifests.
- 2026-03-01: Updated `/Users/cvonderheid/workspace/mappo/frontend/src/App.test.tsx` and `/Users/cvonderheid/workspace/mappo/frontend/src/lib/types.ts` for label/type alignment.
- 2026-03-01: Verified frontend unit test, typecheck, and Playwright core flows all pass.

---

## Scope (Phase 5.54 Slice)
Marketplace webhook-forwarder preparation for production-like demo path:
- Add deployable Azure Function forwarder package (webhook receiver -> MAPPO onboarding API).
- Add repeatable CLI/make workflows for package/deploy/replay.
- Update docs/runbooks/checklists for the function-app-driven demo flow and security boundary.

## Plan (Phase 5.54 Slice)
- [x] Add Azure Function forwarder source package with normalization + forwarding logic.
- [x] Add packaging/deployment/replay scripts and expose via Make targets.
- [x] Document full runbook and update README/checklists/playbook references.
- [x] Validate script syntax, make help surface, and docs consistency checks.

## Verification Commands (Phase 5.54 Slice)
- [x] `bash -n scripts/marketplace_forwarder_package.sh scripts/marketplace_forwarder_deploy.sh scripts/marketplace_forwarder_replay_inventory.sh`
- [x] `make help | rg -n "marketplace-forwarder|marketplace-ingest-events|partner-center"`
- [x] `python3 scripts/docs_consistency_check.py`

## Results Log (Phase 5.54 Slice)
- 2026-03-01: Added Azure Function source package at `/Users/cvonderheid/workspace/mappo/integrations/marketplace-forwarder-function/` (`function_app.py`, `host.json`, `requirements.txt`) with MAPPO onboarding payload normalization + forwarding.
- 2026-03-01: Added scripts `/Users/cvonderheid/workspace/mappo/scripts/marketplace_forwarder_package.sh`, `/Users/cvonderheid/workspace/mappo/scripts/marketplace_forwarder_deploy.sh`, and `/Users/cvonderheid/workspace/mappo/scripts/marketplace_forwarder_replay_inventory.sh`.
- 2026-03-01: Added Make targets `marketplace-forwarder-package`, `marketplace-forwarder-deploy`, and `marketplace-forwarder-replay-inventory`.
- 2026-03-01: Updated `/Users/cvonderheid/workspace/mappo/README.md`, `/Users/cvonderheid/workspace/mappo/docs/live-demo-checklist.md`, `/Users/cvonderheid/workspace/mappo/docs/marketplace-portal-playbook.md`, `/Users/cvonderheid/workspace/mappo/docs/documentation.md`, and `/Users/cvonderheid/workspace/mappo/docs/architecture.md` for function-app-forwarder workflow and security guidance.

---

## Scope (Phase 5.55 Slice)
Strict-realism runtime path:
- Deploy MAPPO backend + frontend to Azure Container Apps (not local dev servers) with repeatable scripts.
- Keep runtime resources outside Pulumi-managed resource groups to preserve deterministic `iac-destroy`.
- Wire docs/make targets so forwarder can target cloud runtime by default.

## Plan (Phase 5.55 Slice)
- [x] Add production container artifacts for backend and frontend.
- [x] Add ACA runtime deploy script (RG/env/ACR + image build + app create/update + output env file).
- [x] Add ACA runtime destroy script for repeatable cleanup.
- [x] Add Make targets for runtime deploy/destroy.
- [x] Update runbooks/checklists/README to make cloud runtime the primary path.
- [x] Run syntax/docs verification and capture final command sequence.

## Verification Commands (Phase 5.55 Slice)
- [x] `bash -n scripts/runtime_aca_deploy.sh scripts/runtime_aca_destroy.sh scripts/marketplace_forwarder_deploy.sh`
- [x] `make help | rg -n "runtime-aca|marketplace-forwarder"`
- [x] `python3 scripts/docs_consistency_check.py`

## Results Log (Phase 5.55 Slice)
- 2026-03-01: Added backend runtime image at `/Users/cvonderheid/workspace/mappo/backend/Dockerfile`.
- 2026-03-01: Added frontend runtime image at `/Users/cvonderheid/workspace/mappo/frontend/Dockerfile` and SPA nginx config at `/Users/cvonderheid/workspace/mappo/infra/nginx/mappo-frontend.conf`.
- 2026-03-01: Added runtime deploy/destroy scripts at `/Users/cvonderheid/workspace/mappo/scripts/runtime_aca_deploy.sh` and `/Users/cvonderheid/workspace/mappo/scripts/runtime_aca_destroy.sh`.
- 2026-03-01: Added `.dockerignore` tuned for ACR build context hygiene.
- 2026-03-01: Added Make targets `runtime-aca-deploy` and `runtime-aca-destroy`.
- 2026-03-01: Updated docs (`README`, live checklist, marketplace playbook, forwarder runbook, architecture, documentation) and added `/Users/cvonderheid/workspace/mappo/docs/runtime-aca-runbook.md`.
- 2026-03-01: Executed `make runtime-aca-deploy` successfully against stack `demo`; runtime URLs emitted to `.data/mappo-runtime.env`.
- 2026-03-01: Executed `make marketplace-forwarder-deploy` using runtime env fallback for API base URL; webhook URL generated.
- 2026-03-01: Replayed inventory through Function webhook and verified cloud backend onboarding state reached 2 registrations.
- 2026-03-01: Executed live cloud canaries for target-01 and target-02; both succeeded and targets now report `health_status=healthy`.

---

## Scope (Phase 5.56 Slice)
EasyAuth hardening for cloud runtime:
- Add repeatable EasyAuth configuration for MAPPO frontend Container App.
- Keep Entra app registration lifecycle script-driven for deterministic callback URL wiring.
- Integrate EasyAuth step into `make deploy` orchestration and update runbooks.

## Plan (Phase 5.56 Slice)
- [x] Add runtime EasyAuth script to create/update Entra app registration and configure ACA auth.
- [x] Add Make target for EasyAuth configure and wire into `make deploy`.
- [x] Update README + runbooks/checklists/architecture docs for the new step.
- [x] Run syntax/help/docs verification and capture outcomes.

## Verification Commands (Phase 5.56 Slice)
- [x] `bash -n scripts/runtime_easyauth_configure.sh scripts/runtime_aca_deploy.sh scripts/marketplace_forwarder_deploy.sh`
- [x] `make help | rg -n "runtime-easyauth-configure|deploy|runtime-aca-deploy"`
- [x] `python3 scripts/docs_consistency_check.py`

## Results Log (Phase 5.56 Slice)
- 2026-03-01: Added `/Users/cvonderheid/workspace/mappo/scripts/runtime_easyauth_configure.sh` for idempotent EasyAuth setup (Entra app registration + frontend ACA auth config).
- 2026-03-01: Added `/Users/cvonderheid/workspace/mappo/scripts/azure_cleanup_easyauth.sh` and Make target `azure-cleanup-easyauth` for repeatable EasyAuth teardown.
- 2026-03-01: Added Make target `runtime-easyauth-configure` and wired it into `make deploy` (enabled by default; override with `ENABLE_EASYAUTH=false`).
- 2026-03-01: Updated `/Users/cvonderheid/workspace/mappo/README.md`, `/Users/cvonderheid/workspace/mappo/docs/runtime-aca-runbook.md`, `/Users/cvonderheid/workspace/mappo/docs/live-demo-checklist.md`, `/Users/cvonderheid/workspace/mappo/docs/marketplace-portal-playbook.md`, `/Users/cvonderheid/workspace/mappo/docs/documentation.md`, and `/Users/cvonderheid/workspace/mappo/docs/architecture.md`.
- 2026-03-01: Updated `/Users/cvonderheid/workspace/mappo/docs/script-sweep.md` inventory/disposition counts to include runtime EasyAuth script.
