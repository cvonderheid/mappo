from __future__ import annotations

import asyncio
import hashlib
from collections import defaultdict
from datetime import UTC, datetime, timedelta
from uuid import uuid4

from sqlalchemy import delete, select

from app.db.generated.models import Releases, Runs, Targets
from app.db.session import create_engine_and_session_factory
from app.modules.schemas import (
    CreateReleaseRequest,
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

TERMINAL_RUN_STATUSES = {RunStatus.SUCCEEDED, RunStatus.FAILED, RunStatus.PARTIAL, RunStatus.HALTED}
IN_PROGRESS_TARGET_STATES = {TargetStage.VALIDATING, TargetStage.DEPLOYING, TargetStage.VERIFYING}


class StoreError(Exception):
    def __init__(self, message: str):
        super().__init__(message)
        self.message = message


def utc_now() -> datetime:
    return datetime.now(tz=UTC)


class ControlPlaneStore:
    def __init__(
        self,
        *,
        database_url: str,
        retention_days: int = 90,
        stage_delay_seconds: float = 0.2,
    ):
        self._lock = asyncio.Lock()
        self._stage_delay_seconds = stage_delay_seconds
        self._retention_days = max(1, retention_days)
        self._execution_tasks: dict[str, asyncio.Task[None]] = {}
        self._database_url = database_url
        self._engine, self._session_factory = create_engine_and_session_factory(database_url)

        self._targets = self._load_targets()
        if not self._targets:
            self._targets = self._seed_targets()
            self._replace_targets_locked()

        self._releases = self._load_releases()
        if not self._releases:
            self._releases = self._seed_releases()
            self._replace_releases_locked()

        self._runs = self._load_runs()
        self._reconcile_running_runs_after_startup()
        self._prune_retention_locked()

    async def shutdown(self) -> None:
        async with self._lock:
            tasks = list(self._execution_tasks.values())
            self._execution_tasks.clear()
        for task in tasks:
            task.cancel()
        if tasks:
            await asyncio.gather(*tasks, return_exceptions=True)
        self._engine.dispose()

    async def list_targets(self, tag_filters: dict[str, str] | None = None) -> list[Target]:
        filters = tag_filters or {}
        async with self._lock:
            targets = [
                target.model_copy(deep=True)
                for target in self._targets.values()
                if self._matches_tags(target.tags, filters)
            ]
        return sorted(targets, key=lambda target: target.id)

    async def list_releases(self) -> list[Release]:
        async with self._lock:
            releases = [release.model_copy(deep=True) for release in self._releases.values()]
        return sorted(releases, key=lambda release: release.created_at, reverse=True)

    async def create_release(self, request: CreateReleaseRequest) -> Release:
        release_id = f"rel-{uuid4().hex[:10]}"
        release = Release(
            id=release_id,
            template_spec_id=request.template_spec_id,
            template_spec_version=request.template_spec_version,
            parameter_defaults=request.parameter_defaults,
            release_notes=request.release_notes,
            verification_hints=request.verification_hints,
            created_at=utc_now(),
        )
        async with self._lock:
            self._releases[release.id] = release
            self._save_release_locked(release)
        return release.model_copy(deep=True)

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

            now = utc_now()
            run_id = f"run-{uuid4().hex[:10]}"
            target_records = {
                target_id: TargetExecutionRecord(
                    target_id=target_id,
                    subscription_id=self._targets[target_id].subscription_id,
                    tenant_id=self._targets[target_id].tenant_id,
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
                concurrency=request.concurrency,
                stop_policy=request.stop_policy,
                target_ids=selected_ids,
                status=RunStatus.RUNNING,
                halt_reason=None,
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

    async def reset_demo_data(self) -> None:
        async with self._lock:
            tasks = list(self._execution_tasks.values())
            self._execution_tasks.clear()

            self._targets = self._seed_targets()
            self._releases = self._seed_releases()
            self._runs = {}

            self._replace_targets_locked()
            self._replace_releases_locked()
            self._delete_all_runs_locked()

        for task in tasks:
            task.cancel()
        if tasks:
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
                with self._session_factory() as session:
                    session.execute(delete(Runs).where(Runs.id.in_(removable_ids)))
                    session.commit()
            return len(removable_ids)

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

    def _on_task_done(self, run_id: str, task: asyncio.Task[None]) -> None:
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
            concurrency = await self._get_concurrency(run_id)
            for start in range(0, len(wave_targets), concurrency):
                chunk = wave_targets[start : start + concurrency]
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

    async def _build_waves(self, *, run_id: str, target_ids: list[str]) -> list[list[str]]:
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

        for stage in [TargetStage.VALIDATING, TargetStage.DEPLOYING, TargetStage.VERIFYING]:
            correlation_id = self._build_correlation_id(run_id, target_id, attempt, stage)
            await self._start_stage(run_id, target_id, stage, correlation_id)
            await asyncio.sleep(self._stage_delay_seconds)

            if self._should_fail_target(
                run_id=run_id,
                target_id=target_id,
                attempt=attempt,
                stage=stage,
            ):
                error = StructuredError(
                    code="verification_failed",
                    message="Simulated verification failure. Retry or resume the run.",
                    details={"target_id": target_id, "attempt": attempt},
                )
                await self._complete_stage(
                    run_id=run_id,
                    target_id=target_id,
                    stage=stage,
                    correlation_id=correlation_id,
                    message="Target failed verification checks.",
                    error=error,
                    terminal_state=TargetStage.FAILED,
                )
                return

            await self._complete_stage(
                run_id=run_id,
                target_id=target_id,
                stage=stage,
                correlation_id=correlation_id,
                message=f"{stage.value.title()} completed.",
                error=None,
                terminal_state=None,
            )

        success_correlation = self._build_correlation_id(
            run_id,
            target_id,
            attempt,
            TargetStage.SUCCEEDED,
        )
        await self._start_stage(run_id, target_id, TargetStage.SUCCEEDED, success_correlation)
        await self._complete_stage(
            run_id=run_id,
            target_id=target_id,
            stage=TargetStage.SUCCEEDED,
            correlation_id=success_correlation,
            message="Target deployment succeeded.",
            error=None,
            terminal_state=TargetStage.SUCCEEDED,
        )

    async def _increment_attempt(self, run_id: str, target_id: str) -> int:
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
                message=f"{stage.value.title()} started.",
                error=None,
                correlation_id=correlation_id,
                portal_link=self._build_portal_link(target_id),
            )
            record.stages.append(stage_record)
            record.logs.append(
                TargetLogEvent(
                    timestamp=now,
                    level="INFO",
                    stage=stage,
                    message=f"{stage.value.title()} started",
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
            stage_record = self._find_open_stage_record(record, stage)
            if stage_record is not None:
                stage_record.ended_at = now
                stage_record.message = message
                stage_record.error = error

            record.logs.append(
                TargetLogEvent(
                    timestamp=now,
                    level="ERROR" if error else "INFO",
                    stage=stage,
                    message=message,
                    correlation_id=correlation_id,
                )
            )
            record.updated_at = now
            run.updated_at = now
            if terminal_state is not None:
                record.status = terminal_state
                if terminal_state == TargetStage.SUCCEEDED:
                    release = self._releases.get(run.release_id)
                    target = self._targets.get(target_id)
                    if release is not None and target is not None:
                        target.last_deployed_release = release.template_spec_version
                        target.last_check_in_at = now
                        self._save_target_locked(target)
            elif stage in IN_PROGRESS_TARGET_STATES:
                record.status = stage
            self._save_run_locked(run)

    @staticmethod
    def _find_open_stage_record(
        record: TargetExecutionRecord,
        stage: TargetStage,
    ) -> TargetStageRecord | None:
        for stage_record in reversed(record.stages):
            if stage_record.stage == stage and stage_record.ended_at is None:
                return stage_record
        return None

    async def _apply_stop_policy_if_needed(self, run_id: str) -> bool:
        async with self._lock:
            run = self._runs[run_id]
            counts = self._count_targets(run)
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

    def _refresh_run_terminal_status_locked(self, run: DeploymentRun) -> None:
        counts = self._count_targets(run)
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
            if self._matches_tags(self._targets[target_id].tags, target_tags)
        ]
        return sorted(set(selected))

    @staticmethod
    def _matches_tags(target_tags: dict[str, str], requested_tags: dict[str, str]) -> bool:
        for key, value in requested_tags.items():
            if target_tags.get(key) != value:
                return False
        return True

    def _to_run_summary(self, run: DeploymentRun) -> RunSummary:
        counts = self._count_targets(run)
        return RunSummary(
            id=run.id,
            release_id=run.release_id,
            status=run.status,
            strategy_mode=run.strategy_mode,
            created_at=run.created_at,
            started_at=run.started_at,
            ended_at=run.ended_at,
            total_targets=len(run.target_records),
            succeeded_targets=counts["succeeded"],
            failed_targets=counts["failed"],
            in_progress_targets=counts["in_progress"],
            queued_targets=counts["queued"],
            halt_reason=run.halt_reason,
        )

    def _to_run_detail(self, run: DeploymentRun) -> RunDetail:
        ordered_records = [
            run.target_records[target_id].model_copy(deep=True)
            for target_id in run.target_ids
        ]
        return RunDetail(
            id=run.id,
            release_id=run.release_id,
            status=run.status,
            strategy_mode=run.strategy_mode,
            wave_tag=run.wave_tag,
            wave_order=run.wave_order,
            concurrency=run.concurrency,
            stop_policy=run.stop_policy,
            created_at=run.created_at,
            started_at=run.started_at,
            ended_at=run.ended_at,
            updated_at=run.updated_at,
            halt_reason=run.halt_reason,
            target_records=ordered_records,
        )

    @staticmethod
    def _count_targets(run: DeploymentRun) -> dict[str, int]:
        counts = {"queued": 0, "in_progress": 0, "succeeded": 0, "failed": 0}
        for record in run.target_records.values():
            if record.status == TargetStage.QUEUED:
                counts["queued"] += 1
            elif record.status in IN_PROGRESS_TARGET_STATES:
                counts["in_progress"] += 1
            elif record.status == TargetStage.SUCCEEDED:
                counts["succeeded"] += 1
            elif record.status == TargetStage.FAILED:
                counts["failed"] += 1
        return counts

    def _build_correlation_id(
        self,
        run_id: str,
        target_id: str,
        attempt: int,
        stage: TargetStage,
    ) -> str:
        key = f"{run_id}:{target_id}:{attempt}:{stage.value}"
        digest = hashlib.sha256(key.encode()).hexdigest()
        return f"corr-{digest[:16]}"

    def _build_portal_link(self, target_id: str) -> str:
        target = self._targets[target_id]
        resource = target.managed_app_id.replace("/", "%2F")
        return (
            "https://portal.azure.com/#view/HubsExtension/BrowseResource/resourceId/"
            f"{resource}"
        )

    def _should_fail_target(
        self,
        *,
        run_id: str,
        target_id: str,
        attempt: int,
        stage: TargetStage,
    ) -> bool:
        del run_id
        if stage != TargetStage.VERIFYING:
            return False
        failure_mode = self._targets[target_id].simulated_failure_mode
        if failure_mode == "verify_once" and attempt == 1:
            return True
        if failure_mode == "always_fail":
            return True
        return False

    def _load_targets(self) -> dict[str, Target]:
        with self._session_factory() as session:
            rows = session.execute(select(Targets.payload_json)).scalars().all()
        targets: dict[str, Target] = {}
        for payload in rows:
            try:
                target = Target.model_validate(payload)
                targets[target.id] = target
            except Exception as error:
                print(f"failed to parse target payload: {error}")
        return targets

    def _load_releases(self) -> dict[str, Release]:
        with self._session_factory() as session:
            rows = session.execute(select(Releases.payload_json)).scalars().all()
        releases: dict[str, Release] = {}
        for payload in rows:
            try:
                release = Release.model_validate(payload)
                releases[release.id] = release
            except Exception as error:
                print(f"failed to parse release payload: {error}")
        return releases

    def _load_runs(self) -> dict[str, DeploymentRun]:
        with self._session_factory() as session:
            rows = (
                session.execute(select(Runs.payload_json).order_by(Runs.created_at.asc()))
                .scalars()
                .all()
            )
        runs: dict[str, DeploymentRun] = {}
        for payload in rows:
            try:
                run = DeploymentRun.model_validate(payload)
                runs[run.id] = run
            except Exception as error:
                print(f"failed to parse run payload: {error}")
        return runs

    def _replace_targets_locked(self) -> None:
        now = utc_now()
        with self._session_factory() as session:
            session.execute(delete(Targets))
            session.add_all(
                [
                    Targets(
                        id=target.id,
                        payload_json=target.model_dump(mode="json"),
                        updated_at=now,
                    )
                    for target in sorted(self._targets.values(), key=lambda item: item.id)
                ]
            )
            session.commit()

    def _replace_releases_locked(self) -> None:
        with self._session_factory() as session:
            session.execute(delete(Releases))
            session.add_all(
                [
                    Releases(
                        id=release.id,
                        payload_json=release.model_dump(mode="json"),
                        created_at=release.created_at,
                    )
                    for release in sorted(
                        self._releases.values(),
                        key=lambda item: item.created_at,
                    )
                ]
            )
            session.commit()

    def _save_release_locked(self, release: Release) -> None:
        with self._session_factory() as session:
            row = session.get(Releases, release.id)
            if row is None:
                session.add(
                    Releases(
                        id=release.id,
                        payload_json=release.model_dump(mode="json"),
                        created_at=release.created_at,
                    )
                )
            else:
                row.payload_json = release.model_dump(mode="json")
                row.created_at = release.created_at
            session.commit()

    def _save_target_locked(self, target: Target) -> None:
        now = utc_now()
        with self._session_factory() as session:
            row = session.get(Targets, target.id)
            if row is None:
                session.add(
                    Targets(
                        id=target.id,
                        payload_json=target.model_dump(mode="json"),
                        updated_at=now,
                    )
                )
            else:
                row.payload_json = target.model_dump(mode="json")
                row.updated_at = now
            session.commit()

    def _save_run_locked(self, run: DeploymentRun) -> None:
        with self._session_factory() as session:
            row = session.get(Runs, run.id)
            if row is None:
                session.add(
                    Runs(
                        id=run.id,
                        payload_json=run.model_dump(mode="json"),
                        created_at=run.created_at,
                        updated_at=run.updated_at,
                    )
                )
            else:
                row.payload_json = run.model_dump(mode="json")
                row.created_at = run.created_at
                row.updated_at = run.updated_at
            session.commit()

    def _delete_all_runs_locked(self) -> None:
        with self._session_factory() as session:
            session.execute(delete(Runs))
            session.commit()

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

        with self._session_factory() as session:
            session.execute(delete(Runs).where(Runs.id.in_(removable_ids)))
            session.commit()

    @staticmethod
    def _seed_targets() -> dict[str, Target]:
        now = utc_now()
        seed_rows: list[tuple[str, str, str, str, str, str, str]] = [
            ("target-01", "canary", "eastus", "gold", "prod", "none", "healthy"),
            ("target-02", "canary", "westus", "gold", "prod", "none", "healthy"),
            ("target-03", "prod", "eastus", "gold", "prod", "none", "healthy"),
            ("target-04", "prod", "westus", "gold", "prod", "none", "healthy"),
            ("target-05", "prod", "centralus", "silver", "prod", "none", "healthy"),
            ("target-06", "prod", "eastus2", "silver", "prod", "none", "healthy"),
            ("target-07", "prod", "westus2", "silver", "prod", "verify_once", "healthy"),
            ("target-08", "prod", "southcentralus", "silver", "prod", "none", "healthy"),
            ("target-09", "prod", "northcentralus", "bronze", "prod", "verify_once", "degraded"),
            ("target-10", "prod", "eastus", "bronze", "prod", "none", "healthy"),
        ]

        targets: dict[str, Target] = {}
        for index, row in enumerate(seed_rows, start=1):
            target_id, ring, region, tier, environment, failure_mode, health_status = row
            targets[target_id] = Target(
                id=target_id,
                tenant_id=f"tenant-{index:03d}",
                subscription_id=f"sub-{index:04d}",
                managed_app_id=(
                    f"/subscriptions/sub-{index:04d}/resourceGroups/rg-{target_id}"
                    f"/providers/Microsoft.Solutions/applications/{target_id}"
                ),
                tags={
                    "ring": ring,
                    "region": region,
                    "tier": tier,
                    "environment": environment,
                },
                last_deployed_release="2026.02.20.1",
                health_status=health_status,
                last_check_in_at=now,
                simulated_failure_mode=failure_mode,
            )
        return targets

    @staticmethod
    def _seed_releases() -> dict[str, Release]:
        now = utc_now()
        template_spec_id = (
            "/subscriptions/provider-sub/resourceGroups/mappo-rg/providers/"
            "Microsoft.Resources/templateSpecs/mappo-managed-app"
        )
        return {
            "rel-2026-02-20": Release(
                id="rel-2026-02-20",
                template_spec_id=template_spec_id,
                template_spec_version="2026.02.20.1",
                parameter_defaults={"imageTag": "1.4.2", "featureFlag": "off"},
                release_notes="Stable baseline release.",
                verification_hints=["Health endpoint returns 200", "Container revision ready"],
                created_at=now,
            ),
            "rel-2026-02-25": Release(
                id="rel-2026-02-25",
                template_spec_id=template_spec_id,
                template_spec_version="2026.02.25.3",
                parameter_defaults={"imageTag": "1.5.0", "featureFlag": "on"},
                release_notes="Canary-first rollout with new API image tag.",
                verification_hints=["App startup under 60s", "Dependency probe healthy"],
                created_at=now,
            ),
        }
