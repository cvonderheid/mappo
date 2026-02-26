from __future__ import annotations

import asyncio
import os

from sqlalchemy import delete

from app.db.generated.models import Releases, Runs, Targets
from app.db.session import create_engine_and_session_factory
from app.modules.control_plane import ControlPlaneStore
from app.modules.schemas import CreateRunRequest, RunStatus, StrategyMode

DEFAULT_DATABASE_URL = "postgresql+psycopg://txero:txero@localhost:5432/mappo"


def _database_url() -> str:
    return os.getenv("MAPPO_DATABASE_URL") or os.getenv("DATABASE_URL") or DEFAULT_DATABASE_URL


def _reset_database(database_url: str) -> None:
    engine, session_factory = create_engine_and_session_factory(database_url)
    try:
        with session_factory() as session:
            session.execute(delete(Runs))
            session.execute(delete(Releases))
            session.execute(delete(Targets))
            session.commit()
    finally:
        engine.dispose()


def test_run_persists_across_store_restarts() -> None:
    database_url = _database_url()
    _reset_database(database_url)

    first_store = ControlPlaneStore(database_url=database_url, stage_delay_seconds=0.01)
    try:
        created = asyncio.run(
            first_store.create_run(
                CreateRunRequest(
                    release_id="rel-2026-02-25",
                    strategy_mode=StrategyMode.ALL_AT_ONCE,
                    target_ids=["target-01"],
                    concurrency=1,
                )
            )
        )
        assert created.status == RunStatus.RUNNING
    finally:
        asyncio.run(first_store.shutdown())

    second_store = ControlPlaneStore(database_url=database_url, stage_delay_seconds=0.01)
    try:
        summaries = asyncio.run(second_store.list_runs())
        assert any(summary.id == created.id for summary in summaries)

        persisted = asyncio.run(second_store.get_run(created.id))
        assert persisted.status == RunStatus.HALTED
    finally:
        asyncio.run(second_store.shutdown())
        _reset_database(database_url)


def test_demo_reset_reseeds_targets_and_clears_runs() -> None:
    database_url = _database_url()
    _reset_database(database_url)
    store = ControlPlaneStore(database_url=database_url, stage_delay_seconds=0.01)
    try:
        asyncio.run(
            store.create_run(
                CreateRunRequest(
                    release_id="rel-2026-02-25",
                    strategy_mode=StrategyMode.ALL_AT_ONCE,
                    target_ids=["target-01"],
                    concurrency=1,
                )
            )
        )
        assert len(asyncio.run(store.list_runs())) >= 1

        asyncio.run(store.reset_demo_data())

        targets = asyncio.run(store.list_targets())
        runs = asyncio.run(store.list_runs())
        releases = asyncio.run(store.list_releases())

        assert len(targets) == 10
        assert len(releases) >= 2
        assert len(runs) == 0
    finally:
        asyncio.run(store.shutdown())
        _reset_database(database_url)
