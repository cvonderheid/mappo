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
