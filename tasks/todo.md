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
