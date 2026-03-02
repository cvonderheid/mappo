from __future__ import annotations

import asyncio

from app.domain.common import StoreError
from app.domain.helpers import matches_tags
from app.modules.schemas import DeploymentRun, Target


class TargetsDomainMixin:
    _lock: asyncio.Lock
    _targets: dict[str, Target]
    _execution_tasks: dict[str, asyncio.Task[None]]
    _runs: dict[str, DeploymentRun]

    def _replace_targets_locked(self) -> None: ...
    def _delete_all_runs_locked(self) -> None: ...
    async def _gather_cancelled_tasks(self, tasks: list[asyncio.Task[None]]) -> None: ...

    async def list_targets(
        self,
        tag_filters: dict[str, str] | None = None,
    ) -> list[Target]:
        filters = tag_filters or {}
        async with self._lock:
            targets = []
            for target in self._targets.values():
                target_copy = target.model_copy(deep=True)
                if not matches_tags(target_copy.tags, filters):
                    continue
                targets.append(target_copy)
        return sorted(targets, key=lambda target: target.id)

    async def replace_targets(
        self,
        targets: list[Target],
        *,
        clear_runs: bool = False,
    ) -> None:
        by_id: dict[str, Target] = {}
        for target in targets:
            if target.id in by_id:
                raise StoreError(f"duplicate target id in import payload: {target.id}")
            by_id[target.id] = target

        tasks_to_cancel: list[asyncio.Task[None]] = []
        async with self._lock:
            self._targets = by_id
            self._replace_targets_locked()
            if clear_runs:
                tasks_to_cancel = list(self._execution_tasks.values())
                self._execution_tasks.clear()
                self._runs = {}
                self._delete_all_runs_locked()

        for task in tasks_to_cancel:
            task.cancel()
        if tasks_to_cancel:
            await self._gather_cancelled_tasks(tasks_to_cancel)

    def _select_target_ids_locked(
        self,
        *,
        target_ids: list[str] | None,
        target_tags: dict[str, str],
    ) -> list[str]:
        if target_ids is None:
            candidates = list(self._targets.keys())
        else:
            unknown = [target_id for target_id in target_ids if target_id not in self._targets]
            if unknown:
                raise StoreError(f"unknown target IDs: {', '.join(sorted(unknown))}")
            candidates = target_ids

        selected = [
            target_id
            for target_id in candidates
            if matches_tags(self._targets[target_id].tags, target_tags)
        ]
        return sorted(set(selected))
