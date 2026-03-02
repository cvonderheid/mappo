from __future__ import annotations

import asyncio
from datetime import timedelta
from typing import Any

from app.db.session import create_engine_and_session_factory
from app.domain.admin import AdminDomainMixin
from app.domain.common import StoreError, utc_now
from app.domain.releases import ReleasesDomainMixin
from app.domain.runs import RunsDomainMixin
from app.domain.targets import TargetsDomainMixin
from app.modules.execution import (
    AzureExecutorSettings,
    ExecutionMode,
    TargetExecutor,
    create_target_executor,
)
from app.modules.schemas import (
    DeploymentRun,
    MarketplaceEventRecord,
    Release,
    RunStatus,
    Target,
    TargetRegistrationRecord,
)
from app.repositories.admin_repository import AdminRepository
from app.repositories.releases_repository import ReleasesRepository
from app.repositories.runs_repository import RunsRepository
from app.repositories.targets_repository import TargetsRepository

__all__ = ["ControlPlaneRuntime", "StoreError"]


class ControlPlaneRuntime(
    AdminDomainMixin,
    ReleasesDomainMixin,
    TargetsDomainMixin,
    RunsDomainMixin,
):
    def __init__(
        self,
        *,
        database_url: str | None = None,
        engine: Any | None = None,
        session_factory: Any | None = None,
        execution_mode: ExecutionMode = ExecutionMode.AZURE,
        azure_settings: AzureExecutorSettings | None = None,
        retention_days: int = 90,
        stage_delay_seconds: float = 0.2,
    ):
        self._lock = asyncio.Lock()
        self._retention_days = max(1, retention_days)
        self._execution_tasks: dict[str, asyncio.Task[None]] = {}
        self._database_url = database_url
        if engine is None or session_factory is None:
            self._engine, self._session_factory = create_engine_and_session_factory(database_url)
        else:
            self._engine = engine
            self._session_factory = session_factory
        self._admin_repository = AdminRepository(self._session_factory)
        self._targets_repository = TargetsRepository(self._session_factory)
        self._releases_repository = ReleasesRepository(self._session_factory)
        self._runs_repository = RunsRepository(self._session_factory)
        self._execution_mode = execution_mode
        self._target_executor: TargetExecutor = create_target_executor(
            mode=execution_mode,
            stage_delay_seconds=stage_delay_seconds,
            azure_settings=azure_settings or AzureExecutorSettings(),
        )

        self._targets = self._targets_repository.load_targets()
        self._registrations = self._targets_repository.load_target_registrations()
        self._marketplace_events = self._admin_repository.load_marketplace_events()
        self._releases = self._releases_repository.load_releases()

        self._runs = self._runs_repository.load_runs()
        self._reconcile_running_runs_after_startup()
        self._prune_retention_locked()

    @property
    def session_factory(self) -> Any:
        return self._session_factory

    async def shutdown(self) -> None:
        async with self._lock:
            tasks = list(self._execution_tasks.values())
            self._execution_tasks.clear()
        await self._gather_cancelled_tasks(tasks)
        self._engine.dispose()

    async def _gather_cancelled_tasks(self, tasks: list[asyncio.Task[None]]) -> None:
        if not tasks:
            return
        for task in tasks:
            task.cancel()
        await asyncio.gather(*tasks, return_exceptions=True)

    async def prune_retention(self, retention_days: int | None = None) -> int:
        async with self._lock:
            if retention_days is None:
                threshold_days = self._retention_days
            else:
                threshold_days = max(1, retention_days)
            threshold = utc_now() - timedelta(days=threshold_days)

            removable_ids: list[str] = []
            for run_id, run in self._runs.items():
                reference_time = run.ended_at or run.updated_at
                if reference_time < threshold:
                    removable_ids.append(run_id)

            for run_id in removable_ids:
                self._runs.pop(run_id, None)
            if removable_ids:
                self._runs_repository.delete_runs_by_ids(run_ids=removable_ids)
            return len(removable_ids)

    def _replace_targets_locked(self) -> None:
        self._targets_repository.replace_targets(
            targets=list(self._targets.values()),
            updated_at=utc_now(),
        )

    def _replace_releases_locked(self) -> None:
        self._releases_repository.replace_releases(releases=list(self._releases.values()))

    def _save_release_locked(self, release: Release) -> None:
        self._releases_repository.save_release(release=release)

    def _save_target_locked(self, target: Target) -> None:
        self._targets_repository.save_target(target=target, updated_at=utc_now())

    def _save_target_registration_locked(self, registration: TargetRegistrationRecord) -> None:
        self._targets_repository.save_target_registration(
            registration=registration,
            updated_at=utc_now(),
        )

    def _delete_target_locked(self, target_id: str) -> None:
        self._targets_repository.delete_target(target_id=target_id)

    def _delete_target_registration_locked(self, target_id: str) -> None:
        self._targets_repository.delete_target_registration(target_id=target_id)

    def _save_marketplace_event_locked(self, event: MarketplaceEventRecord) -> None:
        self._admin_repository.save_marketplace_event(event=event)

    def _save_run_locked(self, run: DeploymentRun) -> None:
        self._runs_repository.save_run(run=run)

    def _delete_all_runs_locked(self) -> None:
        self._runs_repository.delete_all_runs()

    def _reconcile_running_runs_after_startup(self) -> None:
        changed = False
        for run in self._runs.values():
            if run.status != RunStatus.RUNNING:
                continue
            run.status = RunStatus.HALTED
            run.halt_reason = "Control plane restarted before run completion. Resume to continue."
            now = utc_now()
            run.ended_at = now
            run.updated_at = now
            changed = True

        if changed:
            for run in self._runs.values():
                self._save_run_locked(run)

    def _prune_retention_locked(self) -> None:
        threshold = utc_now() - timedelta(days=self._retention_days)
        removable_ids: list[str] = []
        for run_id, run in self._runs.items():
            reference_time = run.ended_at or run.updated_at
            if reference_time < threshold:
                removable_ids.append(run_id)

        if not removable_ids:
            return

        for run_id in removable_ids:
            self._runs.pop(run_id, None)
        self._runs_repository.delete_runs_by_ids(run_ids=removable_ids)
