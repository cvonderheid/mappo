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
