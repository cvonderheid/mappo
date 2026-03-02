from __future__ import annotations

import asyncio
from collections import defaultdict
from uuid import uuid4

from app.modules.control_plane_common import StoreError, utc_now
from app.modules.control_plane_helpers import (
    build_error_log_lines,
    count_targets,
    find_open_stage_record,
    is_guid,
)
from app.modules.execution import TargetExecutor
from app.modules.schemas import (
    CreateRunRequest,
    DeploymentRun,
    Release,
    RunDetail,
    RunStatus,
    RunSummary,
    StrategyMode,
    StructuredError,
    Target,
    TargetExecutionRecord,
    TargetLogEvent,
    TargetStage,
    TargetStageRecord,
)

IN_PROGRESS_TARGET_STATES = {TargetStage.VALIDATING, TargetStage.DEPLOYING, TargetStage.VERIFYING}


class RunsDomainMixin:
    _lock: asyncio.Lock
    _runs: dict[str, DeploymentRun]
    _releases: dict[str, Release]
    _targets: dict[str, Target]
    _execution_tasks: dict[str, asyncio.Task[None]]
    _target_executor: TargetExecutor

    def _select_target_ids_locked(
        self,
        *,
        target_ids: list[str] | None,
        target_tags: dict[str, str],
    ) -> list[str]:
        raise NotImplementedError

    def _save_run_locked(self, run: DeploymentRun) -> None:
        raise NotImplementedError

    def _save_target_locked(self, target: Target) -> None:
        raise NotImplementedError

    async def list_runs(self) -> list[RunSummary]:
        async with self._lock:
            summaries = [self._to_run_summary(run) for run in self._runs.values()]
        return sorted(summaries, key=lambda run: run.created_at, reverse=True)

    async def get_run(self, run_id: str) -> RunDetail:
        async with self._lock:
            run = self._runs.get(run_id)
            if run is None:
                raise StoreError(f"run not found: {run_id}")
            return self._to_run_detail(run)

    async def create_run(self, request: CreateRunRequest) -> RunDetail:
        async with self._lock:
            if request.release_id not in self._releases:
                raise StoreError(f"release not found: {request.release_id}")

            selected_ids = self._select_target_ids_locked(
                target_ids=request.target_ids,
                target_tags=request.target_tags,
            )
            if not selected_ids:
                raise StoreError("no targets matched target selection")

            selected_targets = [
                self._targets[target_id].model_copy(deep=True) for target_id in selected_ids
            ]

        try:
            guardrail_plan = await self._target_executor.prepare_run(
                targets=selected_targets,
                requested_concurrency=request.concurrency,
            )
        except Exception as error:
            raise StoreError(f"run preflight failed: {error}") from error

        async with self._lock:
            now = utc_now()
            run_id = f"run-{uuid4().hex[:10]}"
            projected_targets_by_id = {
                target_id: self._targets[target_id].model_copy(deep=True)
                for target_id in selected_ids
            }
            target_records = {
                target_id: TargetExecutionRecord(
                    target_id=target_id,
                    subscription_id=projected_targets_by_id[target_id].subscription_id,
                    tenant_id=projected_targets_by_id[target_id].tenant_id,
                    status=TargetStage.QUEUED,
                    attempt=0,
                    updated_at=now,
                    stages=[],
                    logs=[],
                )
                for target_id in selected_ids
            }
            run = DeploymentRun(
                id=run_id,
                release_id=request.release_id,
                strategy_mode=request.strategy_mode,
                wave_tag=request.wave_tag,
                wave_order=request.wave_order,
                concurrency=guardrail_plan.effective_concurrency,
                subscription_concurrency=guardrail_plan.subscription_concurrency,
                stop_policy=request.stop_policy,
                target_ids=selected_ids,
                status=RunStatus.RUNNING,
                halt_reason=None,
                guardrail_warnings=guardrail_plan.warnings,
                created_at=now,
                started_at=now,
                ended_at=None,
                updated_at=now,
                target_records=target_records,
            )
            self._runs[run.id] = run
            self._save_run_locked(run)

        await self._launch_execution(
            run_id=run.id,
            include_queued=True,
            include_failed=False,
        )
        return await self.get_run(run.id)

    async def resume_run(self, run_id: str) -> RunDetail:
        async with self._lock:
            run = self._runs.get(run_id)
            if run is None:
                raise StoreError(f"run not found: {run_id}")
            if run.status not in {
                RunStatus.RUNNING,
                RunStatus.HALTED,
                RunStatus.FAILED,
                RunStatus.PARTIAL,
            }:
                raise StoreError("run is not resumable")
            resumable_target_ids = [
                target_id
                for target_id, record in run.target_records.items()
                if record.status in {TargetStage.QUEUED, TargetStage.FAILED}
            ]
            if not resumable_target_ids:
                raise StoreError("run has no queued or failed targets")
            run.status = RunStatus.RUNNING
            run.halt_reason = None
            run.ended_at = None
            run.updated_at = utc_now()
            self._save_run_locked(run)

        await self._launch_execution(
            run_id=run_id,
            include_queued=True,
            include_failed=True,
        )
        return await self.get_run(run_id)

    async def retry_failed(self, run_id: str) -> RunDetail:
        async with self._lock:
            run = self._runs.get(run_id)
            if run is None:
                raise StoreError(f"run not found: {run_id}")
            failed_ids = [
                target_id
                for target_id, record in run.target_records.items()
                if record.status == TargetStage.FAILED
            ]
            if not failed_ids:
                raise StoreError("run has no failed targets")
            run.status = RunStatus.RUNNING
            run.halt_reason = None
            run.ended_at = None
            run.updated_at = utc_now()
            self._save_run_locked(run)

        await self._launch_execution(
            run_id=run_id,
            include_queued=False,
            include_failed=True,
        )
        return await self.get_run(run_id)

    async def _launch_execution(
        self,
        *,
        run_id: str,
        include_queued: bool,
        include_failed: bool,
    ) -> None:
        async with self._lock:
            existing_task = self._execution_tasks.get(run_id)
            if existing_task is not None and not existing_task.done():
                raise StoreError("run is already executing")
            task = asyncio.create_task(
                self._execute_run(
                    run_id=run_id,
                    include_queued=include_queued,
                    include_failed=include_failed,
                )
            )
            self._execution_tasks[run_id] = task
            task.add_done_callback(lambda finished_task: self._on_task_done(run_id, finished_task))

    def _on_task_done(
        self,
        run_id: str,
        task: asyncio.Task[None],
    ) -> None:
        self._execution_tasks.pop(run_id, None)
        if task.cancelled():
            return
        error = task.exception()
        if error is not None:
            print(f"run execution task failed for {run_id}: {error}")

    async def _execute_run(
        self,
        *,
        run_id: str,
        include_queued: bool,
        include_failed: bool,
    ) -> None:
        pending_target_ids = await self._select_pending_target_ids(
            run_id=run_id,
            include_queued=include_queued,
            include_failed=include_failed,
        )

        if not pending_target_ids:
            async with self._lock:
                run = self._runs.get(run_id)
                if run is not None:
                    self._refresh_run_terminal_status_locked(run)
                    self._save_run_locked(run)
            return

        waves = await self._build_waves(run_id=run_id, target_ids=pending_target_ids)
        for wave_targets in waves:
            batches = await self._build_execution_batches(run_id, wave_targets)
            for chunk in batches:
                await asyncio.gather(
                    *(self._execute_target(run_id, target_id) for target_id in chunk)
                )
                if await self._apply_stop_policy_if_needed(run_id):
                    return

        async with self._lock:
            run = self._runs.get(run_id)
            if run is not None:
                self._refresh_run_terminal_status_locked(run)
                self._save_run_locked(run)

    async def _get_concurrency(self, run_id: str) -> int:
        async with self._lock:
            run = self._runs[run_id]
            return max(1, run.concurrency)

    async def _get_subscription_concurrency(self, run_id: str) -> int:
        async with self._lock:
            run = self._runs[run_id]
            return max(1, min(run.concurrency, run.subscription_concurrency))

    async def _build_execution_batches(
        self,
        run_id: str,
        wave_targets: list[str],
    ) -> list[list[str]]:
        if not wave_targets:
            return []

        concurrency = await self._get_concurrency(run_id)
        per_subscription = await self._get_subscription_concurrency(run_id)
        if per_subscription >= concurrency:
            return [
                wave_targets[start : start + concurrency]
                for start in range(0, len(wave_targets), concurrency)
            ]

        async with self._lock:
            subscription_by_target = {
                target_id: self._targets[target_id].subscription_id for target_id in wave_targets
            }

        queues_by_subscription: dict[str, list[str]] = {}
        for target_id in wave_targets:
            subscription_id = subscription_by_target[target_id]
            if subscription_id not in queues_by_subscription:
                queues_by_subscription[subscription_id] = []
            queues_by_subscription[subscription_id].append(target_id)

        batches: list[list[str]] = []
        ordered_subscriptions = sorted(queues_by_subscription.keys())
        while True:
            batch: list[str] = []
            any_remaining = False
            for subscription_id in ordered_subscriptions:
                queue = queues_by_subscription[subscription_id]
                if not queue:
                    continue
                any_remaining = True
                take_count = min(
                    per_subscription,
                    len(queue),
                    concurrency - len(batch),
                )
                for _ in range(take_count):
                    batch.append(queue.pop(0))
                if len(batch) >= concurrency:
                    break
            if not any_remaining:
                break
            if batch:
                batches.append(batch)
        return batches

    async def _select_pending_target_ids(
        self,
        *,
        run_id: str,
        include_queued: bool,
        include_failed: bool,
    ) -> list[str]:
        async with self._lock:
            run = self._runs[run_id]
            selected: list[str] = []
            for target_id in run.target_ids:
                status = run.target_records[target_id].status
                if include_queued and status == TargetStage.QUEUED:
                    selected.append(target_id)
                if include_failed and status == TargetStage.FAILED:
                    selected.append(target_id)
            return selected

    async def _build_waves(
        self,
        *,
        run_id: str,
        target_ids: list[str],
    ) -> list[list[str]]:
        async with self._lock:
            run = self._runs[run_id]
            if run.strategy_mode == StrategyMode.ALL_AT_ONCE:
                return [sorted(target_ids)]

            grouped: dict[str, list[str]] = defaultdict(list)
            for target_id in target_ids:
                tag_value = self._targets[target_id].tags.get(run.wave_tag, "unassigned")
                grouped[tag_value].append(target_id)

            waves: list[list[str]] = []
            seen_values: set[str] = set()

            for wave_value in run.wave_order:
                wave = sorted(grouped.get(wave_value, []))
                if wave:
                    waves.append(wave)
                    seen_values.add(wave_value)

            for wave_value in sorted(grouped.keys()):
                if wave_value in seen_values:
                    continue
                waves.append(sorted(grouped[wave_value]))

            return waves

    async def _execute_target(self, run_id: str, target_id: str) -> None:
        attempt = await self._increment_attempt(run_id, target_id)
        async with self._lock:
            run = self._runs[run_id].model_copy(deep=True)
            target = self._targets[target_id].model_copy(deep=True)
            release = self._releases[run.release_id].model_copy(deep=True)

        async for event in self._target_executor.execute_target(
            run=run,
            target=target,
            release=release,
            attempt=attempt,
        ):
            if event.event_type == "started":
                await self._start_stage(
                    run_id=run_id,
                    target_id=target_id,
                    stage=event.stage,
                    correlation_id=event.correlation_id,
                    message=event.message,
                )
                continue
            await self._complete_stage(
                run_id=run_id,
                target_id=target_id,
                stage=event.stage,
                correlation_id=event.correlation_id,
                message=event.message,
                error=event.error,
                terminal_state=event.terminal_state,
            )

    async def _increment_attempt(
        self,
        run_id: str,
        target_id: str,
    ) -> int:
        async with self._lock:
            run = self._runs[run_id]
            record = run.target_records[target_id]
            record.attempt += 1
            record.updated_at = utc_now()
            self._save_run_locked(run)
            return record.attempt

    async def _start_stage(
        self,
        run_id: str,
        target_id: str,
        stage: TargetStage,
        correlation_id: str,
        message: str,
    ) -> None:
        async with self._lock:
            run = self._runs[run_id]
            record = run.target_records[target_id]
            now = utc_now()
            record.status = stage
            record.updated_at = now
            run.updated_at = now
            stage_record = TargetStageRecord(
                stage=stage,
                started_at=now,
                ended_at=None,
                message=message,
                error=None,
                correlation_id=correlation_id,
                portal_link=self._build_portal_link(target_id),
            )
            record.stages.append(stage_record)
            record.logs.append(
                TargetLogEvent(
                    timestamp=now,
                    level="info",
                    stage=stage,
                    message=message,
                    correlation_id=correlation_id,
                )
            )
            self._save_run_locked(run)

    async def _complete_stage(
        self,
        *,
        run_id: str,
        target_id: str,
        stage: TargetStage,
        correlation_id: str,
        message: str,
        error: StructuredError | None,
        terminal_state: TargetStage | None,
    ) -> None:
        async with self._lock:
            run = self._runs[run_id]
            record = run.target_records[target_id]
            now = utc_now()
            stage_record = find_open_stage_record(record, stage)
            if stage_record is not None:
                stage_record.ended_at = now
                stage_record.message = message
                stage_record.error = error

            record.logs.append(
                TargetLogEvent(
                    timestamp=now,
                    level="error" if error else "info",
                    stage=stage,
                    message=message,
                    correlation_id=correlation_id,
                )
            )
            if error is not None:
                for diagnostic_line in build_error_log_lines(error):
                    record.logs.append(
                        TargetLogEvent(
                            timestamp=now,
                            level="error",
                            stage=stage,
                            message=diagnostic_line,
                            correlation_id=correlation_id,
                        )
                    )
            record.updated_at = now
            run.updated_at = now
            if terminal_state is not None:
                record.status = terminal_state
                target = self._targets.get(target_id)
                if terminal_state == TargetStage.SUCCEEDED:
                    release = self._releases.get(run.release_id)
                    if release is not None and target is not None:
                        target.last_deployed_release = release.template_spec_version
                        target.health_status = "healthy"
                        target.last_check_in_at = now
                        self._save_target_locked(target)
                elif terminal_state == TargetStage.FAILED and target is not None:
                    target.health_status = "degraded"
                    target.last_check_in_at = now
                    self._save_target_locked(target)
            elif stage in IN_PROGRESS_TARGET_STATES:
                record.status = stage
            self._save_run_locked(run)

    async def _apply_stop_policy_if_needed(self, run_id: str) -> bool:
        async with self._lock:
            run = self._runs[run_id]
            counts = count_targets(run, in_progress_states=IN_PROGRESS_TARGET_STATES)
            attempted = counts["succeeded"] + counts["failed"]
            if attempted == 0:
                return False

            max_failure_count = run.stop_policy.max_failure_count
            if max_failure_count is not None and counts["failed"] >= max_failure_count:
                run.status = RunStatus.HALTED
                run.halt_reason = (
                    f"Halted: failed targets {counts['failed']} reached threshold "
                    f"{max_failure_count}."
                )
                now = utc_now()
                run.ended_at = now
                run.updated_at = now
                self._save_run_locked(run)
                return True

            max_failure_rate = run.stop_policy.max_failure_rate
            if max_failure_rate is not None:
                failure_rate = counts["failed"] / attempted
                if failure_rate > max_failure_rate:
                    run.status = RunStatus.HALTED
                    run.halt_reason = (
                        f"Halted: failure rate {failure_rate:.2%} exceeded "
                        f"threshold {max_failure_rate:.2%}."
                    )
                    now = utc_now()
                    run.ended_at = now
                    run.updated_at = now
                    self._save_run_locked(run)
                    return True

            return False

    def _refresh_run_terminal_status_locked(
        self,
        run: DeploymentRun,
    ) -> None:
        counts = count_targets(run, in_progress_states=IN_PROGRESS_TARGET_STATES)
        if counts["queued"] > 0 or counts["in_progress"] > 0:
            run.status = RunStatus.RUNNING
            run.updated_at = utc_now()
            return

        if counts["failed"] == 0:
            run.status = RunStatus.SUCCEEDED
        elif counts["succeeded"] == 0:
            run.status = RunStatus.FAILED
        else:
            run.status = RunStatus.PARTIAL

        now = utc_now()
        run.ended_at = now
        run.updated_at = now

    def _to_run_summary(self, run: DeploymentRun) -> RunSummary:
        counts = count_targets(run, in_progress_states=IN_PROGRESS_TARGET_STATES)
        return RunSummary(
            id=run.id,
            release_id=run.release_id,
            status=run.status,
            strategy_mode=run.strategy_mode,
            created_at=run.created_at,
            started_at=run.started_at,
            ended_at=run.ended_at,
            subscription_concurrency=run.subscription_concurrency,
            total_targets=len(run.target_records),
            succeeded_targets=counts["succeeded"],
            failed_targets=counts["failed"],
            in_progress_targets=counts["in_progress"],
            queued_targets=counts["queued"],
            halt_reason=run.halt_reason,
            guardrail_warnings=run.guardrail_warnings,
        )

    def _to_run_detail(self, run: DeploymentRun) -> RunDetail:
        ordered_records: list[TargetExecutionRecord] = []
        for target_id in run.target_ids:
            record = run.target_records[target_id].model_copy(deep=True)
            for stage in record.stages:
                stage.portal_link = self._normalize_portal_link(
                    target_id=target_id,
                    portal_link=stage.portal_link,
                )
            ordered_records.append(record)
        return RunDetail(
            id=run.id,
            release_id=run.release_id,
            status=run.status,
            strategy_mode=run.strategy_mode,
            wave_tag=run.wave_tag,
            wave_order=run.wave_order,
            concurrency=run.concurrency,
            subscription_concurrency=run.subscription_concurrency,
            stop_policy=run.stop_policy,
            created_at=run.created_at,
            started_at=run.started_at,
            ended_at=run.ended_at,
            updated_at=run.updated_at,
            halt_reason=run.halt_reason,
            guardrail_warnings=run.guardrail_warnings,
            target_records=ordered_records,
        )

    def _build_portal_link(self, target_id: str) -> str:
        target = self._targets[target_id]
        resource_id = target.managed_app_id.strip()
        if not resource_id.startswith("/"):
            resource_id = f"/{resource_id}"

        tenant_id = target.tenant_id.strip()
        if is_guid(tenant_id):
            return f"https://portal.azure.com/#@{tenant_id}/resource{resource_id}/overview"
        return f"https://portal.azure.com/#resource{resource_id}/overview"

    def _normalize_portal_link(
        self,
        *,
        target_id: str,
        portal_link: str,
    ) -> str:
        link = portal_link.strip()
        if "HubsExtension/BrowseResource" in link:
            return self._build_portal_link(target_id)
        if link == "":
            return self._build_portal_link(target_id)
        return link
