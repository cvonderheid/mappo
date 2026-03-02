from __future__ import annotations

import asyncio
from datetime import timedelta
from typing import Any

from app.db.session import create_engine_and_session_factory
from app.modules.control_plane_common import StoreError, utc_now
from app.modules.control_plane_domain_admin import AdminDomainMixin
from app.modules.control_plane_domain_releases import ReleasesDomainMixin
from app.modules.control_plane_domain_runs import RunsDomainMixin
from app.modules.control_plane_domain_targets import TargetsDomainMixin
from app.modules.control_plane_storage import (
    delete_all_runs,
    delete_runs_by_ids,
    delete_target,
    delete_target_registration,
    load_marketplace_events,
    load_releases,
    load_runs,
    load_target_registrations,
    load_targets,
    replace_releases,
    replace_targets,
    save_marketplace_event,
    save_release,
    save_run,
    save_target,
    save_target_registration,
)
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

__all__ = ["ControlPlaneStore", "StoreError"]


class ControlPlaneStore(
    AdminDomainMixin,
    ReleasesDomainMixin,
    TargetsDomainMixin,
    RunsDomainMixin,
):
    def __init__(
        self,
        *,
        database_url: str,
        execution_mode: ExecutionMode = ExecutionMode.AZURE,
        azure_settings: AzureExecutorSettings | None = None,
        retention_days: int = 90,
        stage_delay_seconds: float = 0.2,
    ):
        self._lock = asyncio.Lock()
        self._retention_days = max(1, retention_days)
        self._execution_tasks: dict[str, asyncio.Task[None]] = {}
        self._database_url = database_url
        self._engine, self._session_factory = create_engine_and_session_factory(database_url)
        self._execution_mode = execution_mode
        self._target_executor: TargetExecutor = create_target_executor(
            mode=execution_mode,
            stage_delay_seconds=stage_delay_seconds,
            azure_settings=azure_settings or AzureExecutorSettings(),
        )

        self._targets = load_targets(self._session_factory)
        self._registrations = load_target_registrations(self._session_factory)
        self._marketplace_events = load_marketplace_events(self._session_factory)
        self._releases = load_releases(self._session_factory)

        self._runs = load_runs(self._session_factory)
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
                delete_runs_by_ids(self._session_factory, run_ids=removable_ids)
            return len(removable_ids)

    def _replace_targets_locked(self) -> None:
        replace_targets(
            self._session_factory,
            targets=list(self._targets.values()),
            updated_at=utc_now(),
        )

    def _replace_releases_locked(self) -> None:
        replace_releases(self._session_factory, releases=list(self._releases.values()))

    def _save_release_locked(self, release: Release) -> None:
        save_release(self._session_factory, release=release)

    def _save_target_locked(self, target: Target) -> None:
        save_target(self._session_factory, target=target, updated_at=utc_now())

    def _save_target_registration_locked(self, registration: TargetRegistrationRecord) -> None:
        save_target_registration(
            self._session_factory,
            registration=registration,
            updated_at=utc_now(),
        )

    def _delete_target_locked(self, target_id: str) -> None:
        delete_target(self._session_factory, target_id=target_id)

    def _delete_target_registration_locked(self, target_id: str) -> None:
        delete_target_registration(self._session_factory, target_id=target_id)

    def _save_marketplace_event_locked(self, event: MarketplaceEventRecord) -> None:
        save_marketplace_event(self._session_factory, event=event)

    def _save_run_locked(self, run: DeploymentRun) -> None:
        save_run(self._session_factory, run=run)

    def _delete_all_runs_locked(self) -> None:
        delete_all_runs(self._session_factory)

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
        delete_runs_by_ids(self._session_factory, run_ids=removable_ids)
