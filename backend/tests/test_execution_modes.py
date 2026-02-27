from __future__ import annotations

import asyncio
import os
import time

from sqlalchemy import delete

from app.db.generated.models import Releases, Runs, Targets
from app.db.session import create_engine_and_session_factory
from app.modules.control_plane import ControlPlaneStore
from app.modules.execution import AzureExecutorSettings, ExecutionMode
from app.modules.schemas import CreateRunRequest, RunStatus, TargetStage

DEFAULT_DATABASE_URL = "postgresql+psycopg://txero:txero@localhost:5432/mappo"
TERMINAL_STATUSES = {RunStatus.SUCCEEDED, RunStatus.FAILED, RunStatus.PARTIAL, RunStatus.HALTED}


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


async def _wait_for_terminal(
    store: ControlPlaneStore,
    run_id: str,
    timeout_seconds: float = 2.0,
) -> RunStatus:
    deadline = time.monotonic() + timeout_seconds
    while time.monotonic() < deadline:
        run = await store.get_run(run_id)
        if run.status in TERMINAL_STATUSES:
            return run.status
        await asyncio.sleep(0.02)
    raise AssertionError(f"run did not become terminal within {timeout_seconds} seconds")


def test_azure_mode_fails_without_credentials() -> None:
    database_url = _database_url()
    _reset_database(database_url)

    async def _scenario() -> None:
        store = ControlPlaneStore(
            database_url=database_url,
            execution_mode=ExecutionMode.AZURE,
            azure_settings=AzureExecutorSettings(),
            stage_delay_seconds=0.0,
        )
        try:
            run = await store.create_run(
                CreateRunRequest(
                    release_id="rel-2026-02-25",
                    target_ids=["target-01"],
                    concurrency=1,
                )
            )
            status = await _wait_for_terminal(store, run.id)
            assert status == RunStatus.FAILED

            run_detail = await store.get_run(run.id)
            target_record = next(
                record
                for record in run_detail.target_records
                if record.target_id == "target-01"
            )
            assert target_record.status == TargetStage.FAILED
            error_codes = [
                stage.error.code
                for stage in target_record.stages
                if stage.error is not None
            ]
            assert "azure_executor_not_configured" in error_codes
        finally:
            await store.shutdown()

    try:
        asyncio.run(_scenario())
    finally:
        _reset_database(database_url)


def test_azure_mode_reports_not_implemented_with_credentials() -> None:
    database_url = _database_url()
    _reset_database(database_url)

    async def _scenario() -> None:
        store = ControlPlaneStore(
            database_url=database_url,
            execution_mode=ExecutionMode.AZURE,
            azure_settings=AzureExecutorSettings(
                tenant_id="tenant-live",
                client_id="client-live",
                client_secret="secret-live",
            ),
            stage_delay_seconds=0.0,
        )
        try:
            run = await store.create_run(
                CreateRunRequest(
                    release_id="rel-2026-02-25",
                    target_ids=["target-01"],
                    concurrency=1,
                )
            )
            status = await _wait_for_terminal(store, run.id)
            assert status == RunStatus.FAILED

            run_detail = await store.get_run(run.id)
            target_record = next(
                record
                for record in run_detail.target_records
                if record.target_id == "target-01"
            )
            assert target_record.status == TargetStage.FAILED
            error_codes = [
                stage.error.code
                for stage in target_record.stages
                if stage.error is not None
            ]
            assert "azure_executor_not_implemented" in error_codes
        finally:
            await store.shutdown()

    try:
        asyncio.run(_scenario())
    finally:
        _reset_database(database_url)
