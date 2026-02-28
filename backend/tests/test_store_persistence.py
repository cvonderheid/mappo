from __future__ import annotations

import asyncio
import os
from datetime import UTC, datetime

from sqlalchemy import delete

from app.db.generated.models import Releases, Runs, Targets
from app.db.session import create_engine_and_session_factory
from app.modules.control_plane import ControlPlaneStore
from app.modules.execution import ExecutionMode
from app.modules.schemas import CreateRunRequest, Release, RunStatus, StrategyMode, Target
from tests.support.sample_data import sample_releases, seed_store

DEFAULT_DATABASE_URL = "postgresql+psycopg://mappo:mappo@localhost:5433/mappo"


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

    first_store = ControlPlaneStore(
        database_url=database_url,
        execution_mode=ExecutionMode.DEMO,
        stage_delay_seconds=0.01,
    )
    try:
        asyncio.run(seed_store(first_store))
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

    second_store = ControlPlaneStore(
        database_url=database_url,
        execution_mode=ExecutionMode.DEMO,
        stage_delay_seconds=0.01,
    )
    try:
        summaries = asyncio.run(second_store.list_runs())
        assert any(summary.id == created.id for summary in summaries)

        persisted = asyncio.run(second_store.get_run(created.id))
        assert persisted.status == RunStatus.HALTED
    finally:
        asyncio.run(second_store.shutdown())
        _reset_database(database_url)


def test_replace_releases_overwrites_catalog() -> None:
    database_url = _database_url()
    _reset_database(database_url)
    store = ControlPlaneStore(
        database_url=database_url,
        execution_mode=ExecutionMode.DEMO,
        stage_delay_seconds=0.01,
    )
    try:
        asyncio.run(seed_store(store))

        replacement = Release(
            id="rel-2026-03-01",
            template_spec_id=sample_releases()[0].template_spec_id,
            template_spec_version="2026.03.01.1",
            parameter_defaults={"imageTag": "1.6.0"},
            release_notes="Replacement release set.",
            verification_hints=["Health endpoint returns 200"],
            created_at=datetime.now(tz=UTC),
        )
        asyncio.run(store.replace_releases([replacement]))

        releases = asyncio.run(store.list_releases())
        assert len(releases) == 1
        assert releases[0].id == "rel-2026-03-01"
    finally:
        asyncio.run(store.shutdown())
        _reset_database(database_url)


def test_replace_targets_can_clear_runs() -> None:
    database_url = _database_url()
    _reset_database(database_url)
    store = ControlPlaneStore(
        database_url=database_url,
        execution_mode=ExecutionMode.DEMO,
        stage_delay_seconds=0.01,
    )
    try:
        asyncio.run(seed_store(store))
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

        replacement_target = Target(
            id="target-live-01",
            tenant_id="tenant-live-001",
            subscription_id="sub-live-0001",
            managed_app_id=(
                "/subscriptions/sub-live-0001/resourceGroups/rg-target-live-01/"
                "providers/Microsoft.App/containerApps/target-live-01"
            ),
            tags={"ring": "canary", "region": "eastus", "tier": "gold", "environment": "demo"},
            last_deployed_release="2026.02.20.1",
            health_status="healthy",
            last_check_in_at=datetime.now(tz=UTC),
            simulated_failure_mode="none",
        )
        asyncio.run(store.replace_targets([replacement_target], clear_runs=True))

        targets = asyncio.run(store.list_targets())
        runs = asyncio.run(store.list_runs())
        assert len(targets) == 1
        assert targets[0].id == "target-live-01"
        assert len(runs) == 0
    finally:
        asyncio.run(store.shutdown())
        _reset_database(database_url)
