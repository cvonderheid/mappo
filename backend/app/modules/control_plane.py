from __future__ import annotations

import asyncio
from collections import defaultdict
from datetime import UTC, datetime, timedelta
from typing import Any
from uuid import uuid4

from app.db.session import create_engine_and_session_factory
from app.modules.control_plane_helpers import (
    build_error_log_lines,
    count_targets,
    find_open_stage_record,
    is_guid,
    matches_tags,
    project_registration_from_target,
)
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
    AzureExecutionError,
    AzureExecutorSettings,
    ContainerAppResourceRef,
    ExecutionMode,
    TargetExecutor,
    create_target_executor,
    parse_container_app_resource_id,
)
from app.modules.schemas import (
    AdminOnboardingSnapshotResponse,
    CreateReleaseRequest,
    CreateRunRequest,
    DeploymentRun,
    MarketplaceEventIngestRequest,
    MarketplaceEventIngestResponse,
    MarketplaceEventRecord,
    MarketplaceEventStatus,
    Release,
    RunDetail,
    RunStatus,
    RunSummary,
    StrategyMode,
    StructuredError,
    Target,
    TargetExecutionRecord,
    TargetLogEvent,
    TargetRegistrationRecord,
    TargetStage,
    TargetStageRecord,
    UpdateTargetRegistrationRequest,
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
        for task in tasks:
            task.cancel()
        if tasks:
            await asyncio.gather(*tasks, return_exceptions=True)
        self._engine.dispose()

    async def list_targets(self, tag_filters: dict[str, str] | None = None) -> list[Target]:
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
            await asyncio.gather(*tasks_to_cancel, return_exceptions=True)

    async def get_onboarding_snapshot(
        self,
        *,
        event_limit: int = 50,
    ) -> AdminOnboardingSnapshotResponse:
        safe_limit = max(1, event_limit)
        async with self._lock:
            registrations = [
                project_registration_from_target(item, self._targets.get(item.target_id))
                for item in sorted(self._registrations.values(), key=lambda row: row.target_id)
            ]
            events = sorted(
                (item.model_copy(deep=True) for item in self._marketplace_events.values()),
                key=lambda row: row.created_at,
                reverse=True,
            )[:safe_limit]
        return AdminOnboardingSnapshotResponse(registrations=registrations, events=events)

    async def update_target_registration(
        self,
        target_id: str,
        request: UpdateTargetRegistrationRequest,
    ) -> TargetRegistrationRecord:
        async with self._lock:
            registration = self._registrations.get(target_id)
            target = self._targets.get(target_id)
            if registration is None or target is None:
                raise StoreError(f"target registration not found: {target_id}")

            fields = set(request.model_fields_set)
            if not fields:
                return project_registration_from_target(registration, self._targets.get(target_id))

            now = utc_now()
            updated_registration = registration.model_copy(deep=True)
            updated_target = target.model_copy(deep=True)

            if "display_name" in fields:
                if request.display_name is None:
                    updated_registration.display_name = updated_target.id
                else:
                    display_name = request.display_name.strip()
                    if display_name == "":
                        raise StoreError("display_name must not be empty when provided")
                    updated_registration.display_name = display_name

            if "customer_name" in fields:
                customer_name = request.customer_name.strip() if request.customer_name else ""
                updated_registration.customer_name = customer_name or None
                updated_target.customer_name = customer_name or None

            if "managed_application_id" in fields:
                managed_app_id = (
                    request.managed_application_id.strip()
                    if request.managed_application_id is not None
                    else ""
                )
                updated_registration.managed_application_id = (
                    self._normalize_resource_id(managed_app_id)
                    if managed_app_id != ""
                    else None
                )

            if "managed_resource_group_id" in fields:
                managed_rg_id = (
                    request.managed_resource_group_id.strip()
                    if request.managed_resource_group_id is not None
                    else ""
                )
                if managed_rg_id == "":
                    raise StoreError(
                        "managed_resource_group_id must not be empty when provided"
                    )
                updated_registration.managed_resource_group_id = self._normalize_resource_id(
                    managed_rg_id
                )

            if "container_app_resource_id" in fields:
                container_resource_id = (
                    request.container_app_resource_id.strip()
                    if request.container_app_resource_id is not None
                    else ""
                )
                if container_resource_id == "":
                    raise StoreError(
                        "container_app_resource_id must not be empty when provided"
                    )
                normalized_container_resource_id = self._normalize_resource_id(
                    container_resource_id
                )
                container_ref = self._parse_container_app_resource_id_safe(
                    normalized_container_resource_id
                )
                if container_ref.subscription_id != updated_target.subscription_id:
                    raise StoreError(
                        "container_app_resource_id subscription must match target subscription_id"
                    )
                updated_registration.container_app_resource_id = (
                    normalized_container_resource_id
                )
                updated_target.managed_app_id = normalized_container_resource_id
                if "managed_resource_group_id" not in fields:
                    updated_registration.managed_resource_group_id = (
                        f"/subscriptions/{container_ref.subscription_id}/resourceGroups/"
                        f"{container_ref.resource_group_name}"
                    )

            tags = dict(updated_registration.tags)
            if "tags" in fields:
                tags = {}
                for key, value in (request.tags or {}).items():
                    normalized_key = key.strip()
                    normalized_value = value.strip()
                    if normalized_key == "" or normalized_value == "":
                        continue
                    tags[normalized_key] = normalized_value

            if "target_group" in fields:
                target_group = request.target_group.strip() if request.target_group else ""
                tags["ring"] = target_group or "prod"
            if "region" in fields:
                region = request.region.strip() if request.region else ""
                tags["region"] = region or "unknown"
            if "environment" in fields:
                environment = request.environment.strip() if request.environment else ""
                tags["environment"] = environment or "prod"
            if "tier" in fields:
                tier = request.tier.strip() if request.tier else ""
                tags["tier"] = tier or "standard"

            if fields.intersection({"tags", "target_group", "region", "environment", "tier"}):
                if tags.get("ring", "").strip() == "":
                    tags["ring"] = "prod"
                if tags.get("region", "").strip() == "":
                    tags["region"] = "unknown"
                if tags.get("environment", "").strip() == "":
                    tags["environment"] = "prod"
                if tags.get("tier", "").strip() == "":
                    tags["tier"] = "standard"
                updated_registration.tags = tags
                updated_target.tags = dict(tags)

            if "metadata" in fields:
                updated_registration.metadata = request.metadata or {}

            if "health_status" in fields:
                health_status = request.health_status.strip() if request.health_status else ""
                if health_status == "":
                    raise StoreError("health_status must not be empty when provided")
                updated_target.health_status = health_status

            if "last_deployed_release" in fields:
                deployed_release = (
                    request.last_deployed_release.strip()
                    if request.last_deployed_release
                    else ""
                )
                updated_target.last_deployed_release = deployed_release or "unknown"

            updated_registration.updated_at = now

            self._registrations[target_id] = updated_registration
            self._save_target_registration_locked(updated_registration)
            self._targets[target_id] = updated_target
            self._save_target_locked(updated_target)

            return project_registration_from_target(updated_registration, updated_target)

    async def delete_target_registration(self, target_id: str) -> None:
        async with self._lock:
            deleted = False
            if target_id in self._registrations:
                del self._registrations[target_id]
                self._delete_target_registration_locked(target_id)
                deleted = True
            if target_id in self._targets:
                del self._targets[target_id]
                self._delete_target_locked(target_id)
                deleted = True
            if not deleted:
                raise StoreError(f"target registration not found: {target_id}")

    async def ingest_marketplace_event(
        self,
        request: MarketplaceEventIngestRequest,
    ) -> MarketplaceEventIngestResponse:
        async with self._lock:
            existing = self._marketplace_events.get(request.event_id)
            if existing is not None:
                return MarketplaceEventIngestResponse(
                    event_id=existing.event_id,
                    status=MarketplaceEventStatus.DUPLICATE,
                    message=(
                        f"Event already processed with status {existing.status}: "
                        f"{existing.message}"
                    ),
                    target_id=existing.target_id,
                )

            now = utc_now()
            try:
                target, registration = self._build_registration_locked(
                    request=request,
                    now=now,
                )
            except StoreError as error:
                event = MarketplaceEventRecord(
                    event_id=request.event_id,
                    event_type=request.event_type,
                    status=MarketplaceEventStatus.REJECTED,
                    message=error.message,
                    target_id=None,
                    tenant_id=request.tenant_id.strip(),
                    subscription_id=request.subscription_id.strip(),
                    payload=request.model_dump(mode="json"),
                    created_at=now,
                    processed_at=now,
                )
                self._marketplace_events[event.event_id] = event
                self._save_marketplace_event_locked(event)
                return MarketplaceEventIngestResponse(
                    event_id=event.event_id,
                    status=event.status,
                    message=event.message,
                    target_id=event.target_id,
                )

            self._targets[target.id] = target
            self._save_target_locked(target)
            self._registrations[registration.target_id] = registration
            self._save_target_registration_locked(registration)

            event = MarketplaceEventRecord(
                event_id=request.event_id,
                event_type=request.event_type,
                status=MarketplaceEventStatus.APPLIED,
                message=(
                    f"Registered target {target.id} for subscription "
                    f"{target.subscription_id}."
                ),
                target_id=target.id,
                tenant_id=target.tenant_id,
                subscription_id=target.subscription_id,
                payload=request.model_dump(mode="json"),
                created_at=now,
                processed_at=now,
            )
            self._marketplace_events[event.event_id] = event
            self._save_marketplace_event_locked(event)

            return MarketplaceEventIngestResponse(
                event_id=event.event_id,
                status=event.status,
                message=event.message,
                target_id=event.target_id,
            )

    async def list_releases(self) -> list[Release]:
        async with self._lock:
            releases = [release.model_copy(deep=True) for release in self._releases.values()]
        return sorted(releases, key=lambda release: release.created_at, reverse=True)

    async def replace_releases(self, releases: list[Release]) -> None:
        by_id: dict[str, Release] = {}
        for release in releases:
            if release.id in by_id:
                raise StoreError(f"duplicate release id in import payload: {release.id}")
            by_id[release.id] = release

        async with self._lock:
            self._releases = by_id
            self._replace_releases_locked()

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
                target_id: self._targets[target_id].subscription_id
                for target_id in wave_targets
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

    async def _build_waves(self, *, run_id: str, target_ids: list[str]) -> list[list[str]]:
        async with self._lock:
            run = self._runs[run_id]
            if run.strategy_mode == StrategyMode.ALL_AT_ONCE:
                return [sorted(target_ids)]

            grouped: dict[str, list[str]] = defaultdict(list)
            for target_id in target_ids:
                tag_value = self._targets[target_id].tags.get(
                    run.wave_tag,
                    "unassigned",
                )
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

    def _refresh_run_terminal_status_locked(self, run: DeploymentRun) -> None:
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

        # Direct resource blade links are more reliable than BrowseResource links.
        tenant_id = target.tenant_id.strip()
        if is_guid(tenant_id):
            return f"https://portal.azure.com/#@{tenant_id}/resource{resource_id}/overview"
        return f"https://portal.azure.com/#resource{resource_id}/overview"

    def _normalize_portal_link(self, *, target_id: str, portal_link: str) -> str:
        link = portal_link.strip()
        if "HubsExtension/BrowseResource" in link:
            return self._build_portal_link(target_id)
        if link == "":
            return self._build_portal_link(target_id)
        return link

    def _build_registration_locked(
        self,
        *,
        request: MarketplaceEventIngestRequest,
        now: datetime,
    ) -> tuple[Target, TargetRegistrationRecord]:
        tenant_id = request.tenant_id.strip()
        subscription_id = request.subscription_id.strip()
        if tenant_id == "":
            raise StoreError("tenant_id is required")
        if subscription_id == "":
            raise StoreError("subscription_id is required")

        container_app_resource_id = self._resolve_container_app_resource_id(
            request=request,
            subscription_id=subscription_id,
        )
        managed_resource_group_id = self._resolve_managed_resource_group_id(
            request=request,
            container_app_resource_id=container_app_resource_id,
            subscription_id=subscription_id,
        )
        managed_application_id = self._normalize_managed_application_id(
            request=request,
            subscription_id=subscription_id,
        )

        container_ref = self._parse_container_app_resource_id_safe(container_app_resource_id)
        target_id = self._resolve_target_id(
            request=request,
            managed_application_id=managed_application_id,
            container_app_name=container_ref.container_app_name,
        )
        tags = self._build_target_tags(request=request)

        existing_registration = self._registrations.get(target_id)
        created_at = existing_registration.created_at if existing_registration else now

        target = Target(
            id=target_id,
            tenant_id=tenant_id,
            subscription_id=subscription_id,
            managed_app_id=container_app_resource_id,
            tags=tags,
            last_deployed_release=request.last_deployed_release.strip() or "unknown",
            health_status=request.health_status.strip() or "registered",
            last_check_in_at=request.event_time or now,
            simulated_failure_mode="none",
        )
        registration = TargetRegistrationRecord(
            target_id=target.id,
            tenant_id=tenant_id,
            subscription_id=subscription_id,
            managed_application_id=managed_application_id,
            managed_resource_group_id=managed_resource_group_id,
            container_app_resource_id=container_app_resource_id,
            display_name=self._resolve_display_name(
                request=request,
                default_name=target.id,
            ),
            customer_name=request.customer_name.strip() or None
            if request.customer_name is not None
            else None,
            tags=tags,
            metadata=request.metadata,
            last_event_id=request.event_id,
            created_at=created_at,
            updated_at=now,
        )
        return target, registration

    def _resolve_target_id(
        self,
        *,
        request: MarketplaceEventIngestRequest,
        managed_application_id: str | None,
        container_app_name: str,
    ) -> str:
        explicit_target_id = request.target_id.strip() if request.target_id else ""
        if explicit_target_id != "":
            return explicit_target_id
        managed_app_name = self._extract_managed_app_name(managed_application_id)
        if managed_app_name is not None:
            return managed_app_name
        return container_app_name

    def _resolve_display_name(
        self,
        *,
        request: MarketplaceEventIngestRequest,
        default_name: str,
    ) -> str:
        if request.display_name is not None and request.display_name.strip() != "":
            return request.display_name.strip()
        if request.customer_name is not None and request.customer_name.strip() != "":
            return request.customer_name.strip()
        return default_name

    def _build_target_tags(self, *, request: MarketplaceEventIngestRequest) -> dict[str, str]:
        normalized_tags: dict[str, str] = {}
        for key, value in request.tags.items():
            tag_key = key.strip()
            tag_value = value.strip()
            if tag_key == "" or tag_value == "":
                continue
            normalized_tags[tag_key] = tag_value
        normalized_tags["ring"] = request.target_group.strip() or "prod"
        region = request.region.strip() if request.region is not None else ""
        normalized_tags["region"] = (
            region if region != "" else "unknown"
        )
        normalized_tags["environment"] = (
            request.environment.strip() if request.environment.strip() != "" else "prod"
        )
        normalized_tags["tier"] = request.tier.strip() if request.tier.strip() != "" else "standard"
        return normalized_tags

    def _resolve_container_app_resource_id(
        self,
        *,
        request: MarketplaceEventIngestRequest,
        subscription_id: str,
    ) -> str:
        if request.container_app_resource_id is not None:
            resource_id = self._normalize_resource_id(request.container_app_resource_id)
            ref = self._parse_container_app_resource_id_safe(resource_id)
            if ref.subscription_id != subscription_id:
                raise StoreError(
                    "container_app_resource_id subscription does not match subscription_id"
                )
            return resource_id

        managed_rg = request.managed_resource_group_id
        app_name = request.container_app_name
        error_message = (
            "provide container_app_resource_id or "
            "managed_resource_group_id + container_app_name"
        )
        if managed_rg is None or managed_rg.strip() == "":
            raise StoreError(error_message)
        if app_name is None or app_name.strip() == "":
            raise StoreError(error_message)

        managed_rg_resource_id = self._normalize_resource_id(managed_rg)
        rg_parts = [part for part in managed_rg_resource_id.strip("/").split("/") if part]
        if len(rg_parts) != 4:
            raise StoreError("managed_resource_group_id is not a valid resource-group ID")
        if (
            rg_parts[0].lower() != "subscriptions"
            or rg_parts[2].lower() != "resourcegroups"
        ):
            raise StoreError(
                "managed_resource_group_id is missing subscription/resource-group segments"
            )
        if rg_parts[1] != subscription_id:
            raise StoreError(
                "managed_resource_group_id subscription does not match subscription_id"
            )
        return (
            f"/subscriptions/{subscription_id}/resourceGroups/{rg_parts[3]}"
            f"/providers/Microsoft.App/containerApps/{app_name.strip()}"
        )

    def _resolve_managed_resource_group_id(
        self,
        *,
        request: MarketplaceEventIngestRequest,
        container_app_resource_id: str,
        subscription_id: str,
    ) -> str:
        container_parts = [part for part in container_app_resource_id.strip("/").split("/") if part]
        if len(container_parts) != 8:
            raise StoreError("container_app_resource_id is not a valid resource ID")
        inferred = f"/subscriptions/{container_parts[1]}/resourceGroups/{container_parts[3]}"

        if (
            request.managed_resource_group_id is None
            or request.managed_resource_group_id.strip() == ""
        ):
            return inferred

        provided = self._normalize_resource_id(request.managed_resource_group_id)
        provided_parts = [part for part in provided.strip("/").split("/") if part]
        if len(provided_parts) != 4:
            raise StoreError("managed_resource_group_id is not a valid resource-group ID")
        if (
            provided_parts[0].lower() != "subscriptions"
            or provided_parts[2].lower() != "resourcegroups"
        ):
            raise StoreError(
                "managed_resource_group_id is missing subscription/resource-group segments"
            )
        if provided_parts[1] != subscription_id:
            raise StoreError(
                "managed_resource_group_id subscription does not match subscription_id"
            )
        if provided_parts[3] != container_parts[3]:
            raise StoreError(
                "managed_resource_group_id does not match container_app_resource_id resource group"
            )
        return provided

    def _normalize_managed_application_id(
        self,
        *,
        request: MarketplaceEventIngestRequest,
        subscription_id: str,
    ) -> str | None:
        if request.managed_application_id is None or request.managed_application_id.strip() == "":
            return None

        resource_id = self._normalize_resource_id(request.managed_application_id)
        parts = [part for part in resource_id.strip("/").split("/") if part]
        if len(parts) != 8:
            raise StoreError(
                "managed_application_id is not a valid managed application resource ID"
            )
        if (
            parts[0].lower() != "subscriptions"
            or parts[2].lower() != "resourcegroups"
        ):
            raise StoreError(
                "managed_application_id is missing subscription/resource-group segments"
            )
        if parts[4].lower() != "providers" or parts[5].lower() != "microsoft.solutions":
            raise StoreError("managed_application_id provider must be Microsoft.Solutions")
        if parts[6].lower() != "applications":
            raise StoreError("managed_application_id type must be applications")
        if parts[1] != subscription_id:
            raise StoreError("managed_application_id subscription does not match subscription_id")
        return resource_id

    def _extract_managed_app_name(self, managed_application_id: str | None) -> str | None:
        if managed_application_id is None:
            return None
        parts = [part for part in managed_application_id.strip("/").split("/") if part]
        return parts[7] if len(parts) == 8 else None

    def _parse_container_app_resource_id_safe(
        self,
        resource_id: str,
    ) -> ContainerAppResourceRef:
        try:
            return parse_container_app_resource_id(resource_id)
        except AzureExecutionError as error:
            raise StoreError(error.message) from error

    @staticmethod
    def _normalize_resource_id(resource_id: str) -> str:
        trimmed = resource_id.strip()
        if not trimmed:
            raise StoreError("resource ID must not be empty")
        return trimmed if trimmed.startswith("/") else f"/{trimmed}"

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
