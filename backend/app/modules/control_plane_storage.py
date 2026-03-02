from __future__ import annotations

from collections import defaultdict
from datetime import datetime
from typing import Any
from uuid import UUID

from sqlalchemy import delete, select

from app.db.generated.models import (
    ForwarderLogs,
    MarketplaceEvents,
    ReleaseParameterDefaults,
    Releases,
    ReleaseVerificationHints,
    RunGuardrailWarnings,
    Runs,
    RunTargets,
    RunWaveOrder,
    TargetExecutionRecords,
    TargetLogEvents,
    TargetRegistrations,
    Targets,
    TargetStageRecords,
    TargetTags,
)
from app.modules.schemas import (
    DeploymentRun,
    ForwarderLogRecord,
    MarketplaceEventRecord,
    Release,
    StopPolicy,
    StructuredError,
    Target,
    TargetExecutionRecord,
    TargetLogEvent,
    TargetRegistrationRecord,
    TargetStage,
    TargetStageRecord,
)


def _require_guid(value: str, *, field_name: str) -> UUID:
    try:
        return UUID(value)
    except ValueError as error:
        raise ValueError(f"{field_name} must be a valid GUID: {value}") from error


def _optional_guid(value: str | None, *, field_name: str) -> UUID | None:
    if value is None:
        return None
    return _require_guid(value, field_name=field_name)


def _load_target_tags_map(
    session: Any,
    *,
    target_ids: list[str] | None = None,
) -> dict[str, dict[str, str]]:
    query = select(TargetTags.target_id, TargetTags.tag_key, TargetTags.tag_value)
    if target_ids is not None:
        if not target_ids:
            return {}
        query = query.where(TargetTags.target_id.in_(target_ids))
    rows = session.execute(query).all()
    tags_by_target: dict[str, dict[str, str]] = defaultdict(dict)
    for target_id, tag_key, tag_value in rows:
        tags_by_target[target_id][tag_key] = tag_value
    return dict(tags_by_target)


def load_targets(session_factory: Any) -> dict[str, Target]:
    with session_factory() as session:
        target_rows = session.execute(select(Targets)).scalars().all()
        target_ids = [row.id for row in target_rows]
        tags_by_target = _load_target_tags_map(session, target_ids=target_ids)

    targets: dict[str, Target] = {}
    for row in target_rows:
        target = Target(
            id=row.id,
            tenant_id=str(row.tenant_id),
            subscription_id=str(row.subscription_id),
            managed_app_id=row.managed_app_id,
            customer_name=row.customer_name,
            tags=tags_by_target.get(row.id, {}),
            last_deployed_release=row.last_deployed_release,
            health_status=row.health_status,
            last_check_in_at=row.last_check_in_at,
            simulated_failure_mode=row.simulated_failure_mode,
        )
        targets[target.id] = target
    return targets


def load_target_registrations(session_factory: Any) -> dict[str, TargetRegistrationRecord]:
    with session_factory() as session:
        registration_rows = session.execute(select(TargetRegistrations)).scalars().all()
        target_ids = [row.target_id for row in registration_rows]
        if not target_ids:
            return {}
        target_rows = (
            session.execute(select(Targets).where(Targets.id.in_(target_ids))).scalars().all()
        )
        tags_by_target = _load_target_tags_map(session, target_ids=target_ids)

    targets_by_id = {row.id: row for row in target_rows}
    registrations: dict[str, TargetRegistrationRecord] = {}
    for row in registration_rows:
        target_row = targets_by_id.get(row.target_id)
        if target_row is None:
            print(f"target registration references unknown target: {row.target_id}")
            continue
        item = TargetRegistrationRecord(
            target_id=row.target_id,
            tenant_id=str(target_row.tenant_id),
            subscription_id=str(target_row.subscription_id),
            managed_application_id=row.managed_application_id,
            managed_resource_group_id=row.managed_resource_group_id,
            container_app_resource_id=target_row.managed_app_id,
            display_name=row.display_name,
            customer_name=target_row.customer_name,
            tags=tags_by_target.get(row.target_id, {}),
            metadata=row.metadata_ or {},
            last_event_id=row.last_event_id,
            created_at=row.created_at,
            updated_at=row.updated_at,
        )
        registrations[item.target_id] = item
    return registrations


def load_marketplace_events(session_factory: Any) -> dict[str, MarketplaceEventRecord]:
    with session_factory() as session:
        rows = (
            session.execute(select(MarketplaceEvents).order_by(MarketplaceEvents.created_at.asc()))
            .scalars()
            .all()
        )
    events: dict[str, MarketplaceEventRecord] = {}
    for row in rows:
        event = MarketplaceEventRecord(
            event_id=row.id,
            event_type=row.event_type,
            status=row.status,
            message=row.message,
            target_id=row.target_id,
            tenant_id=str(row.tenant_id),
            subscription_id=str(row.subscription_id),
            payload=row.payload or {},
            created_at=row.created_at,
            processed_at=row.processed_at,
        )
        events[event.event_id] = event
    return events


def load_forwarder_logs(session_factory: Any, *, limit: int = 100) -> list[ForwarderLogRecord]:
    safe_limit = max(1, limit)
    with session_factory() as session:
        rows = (
            session.execute(
                select(ForwarderLogs)
                .order_by(ForwarderLogs.created_at.desc())
                .limit(safe_limit)
            )
            .scalars()
            .all()
        )

    records: list[ForwarderLogRecord] = []
    for row in rows:
        records.append(
            ForwarderLogRecord(
                log_id=row.id,
                level=row.level,
                message=row.message,
                event_id=row.event_id,
                event_type=row.event_type,
                target_id=row.target_id,
                tenant_id=str(row.tenant_id) if row.tenant_id is not None else None,
                subscription_id=(
                    str(row.subscription_id) if row.subscription_id is not None else None
                ),
                function_app_name=row.function_app_name,
                forwarder_request_id=row.forwarder_request_id,
                backend_status_code=row.backend_status_code,
                details=row.details or {},
                created_at=row.created_at,
            )
        )
    return records


def load_releases(session_factory: Any) -> dict[str, Release]:
    with session_factory() as session:
        release_rows = session.execute(select(Releases)).scalars().all()
        release_ids = [row.id for row in release_rows]
        parameter_rows = (
            session.execute(
                select(ReleaseParameterDefaults)
                .where(ReleaseParameterDefaults.release_id.in_(release_ids))
                .order_by(
                    ReleaseParameterDefaults.release_id.asc(),
                    ReleaseParameterDefaults.param_key.asc(),
                )
            )
            .scalars()
            .all()
            if release_ids
            else []
        )
        hint_rows = (
            session.execute(
                select(ReleaseVerificationHints)
                .where(ReleaseVerificationHints.release_id.in_(release_ids))
                .order_by(
                    ReleaseVerificationHints.release_id.asc(),
                    ReleaseVerificationHints.position.asc(),
                )
            )
            .scalars()
            .all()
            if release_ids
            else []
        )

    params_by_release: dict[str, dict[str, str]] = defaultdict(dict)
    for row in parameter_rows:
        params_by_release[row.release_id][row.param_key] = row.param_value

    hints_by_release: dict[str, list[str]] = defaultdict(list)
    for row in hint_rows:
        hints_by_release[row.release_id].append(row.hint)

    releases: dict[str, Release] = {}
    for row in release_rows:
        release = Release(
            id=row.id,
            template_spec_id=row.template_spec_id,
            template_spec_version=row.template_spec_version,
            parameter_defaults=params_by_release.get(row.id, {}),
            release_notes=row.release_notes,
            verification_hints=hints_by_release.get(row.id, []),
            created_at=row.created_at,
        )
        releases[release.id] = release
    return releases


def load_runs(session_factory: Any) -> dict[str, DeploymentRun]:
    with session_factory() as session:
        run_rows = session.execute(select(Runs).order_by(Runs.created_at.asc())).scalars().all()
        run_ids = [row.id for row in run_rows]
        if not run_ids:
            return {}

        wave_rows = (
            session.execute(
                select(RunWaveOrder)
                .where(RunWaveOrder.run_id.in_(run_ids))
                .order_by(RunWaveOrder.run_id.asc(), RunWaveOrder.position.asc())
            )
            .scalars()
            .all()
        )
        warning_rows = (
            session.execute(
                select(RunGuardrailWarnings)
                .where(RunGuardrailWarnings.run_id.in_(run_ids))
                .order_by(RunGuardrailWarnings.run_id.asc(), RunGuardrailWarnings.position.asc())
            )
            .scalars()
            .all()
        )
        target_rows = (
            session.execute(
                select(RunTargets)
                .where(RunTargets.run_id.in_(run_ids))
                .order_by(RunTargets.run_id.asc(), RunTargets.position.asc())
            )
            .scalars()
            .all()
        )
        execution_rows = (
            session.execute(
                select(TargetExecutionRecords)
                .where(TargetExecutionRecords.run_id.in_(run_ids))
                .order_by(
                    TargetExecutionRecords.run_id.asc(),
                    TargetExecutionRecords.target_id.asc(),
                )
            )
            .scalars()
            .all()
        )
        stage_rows = (
            session.execute(
                select(TargetStageRecords)
                .where(TargetStageRecords.run_id.in_(run_ids))
                .order_by(
                    TargetStageRecords.run_id.asc(),
                    TargetStageRecords.target_id.asc(),
                    TargetStageRecords.position.asc(),
                )
            )
            .scalars()
            .all()
        )
        log_rows = (
            session.execute(
                select(TargetLogEvents)
                .where(TargetLogEvents.run_id.in_(run_ids))
                .order_by(
                    TargetLogEvents.run_id.asc(),
                    TargetLogEvents.target_id.asc(),
                    TargetLogEvents.position.asc(),
                )
            )
            .scalars()
            .all()
        )

    wave_order_by_run: dict[str, list[str]] = defaultdict(list)
    for row in wave_rows:
        wave_order_by_run[row.run_id].append(row.wave_value)

    warnings_by_run: dict[str, list[str]] = defaultdict(list)
    for row in warning_rows:
        warnings_by_run[row.run_id].append(row.warning)

    target_ids_by_run: dict[str, list[str]] = defaultdict(list)
    for row in target_rows:
        target_ids_by_run[row.run_id].append(row.target_id)

    execution_by_key: dict[tuple[str, str], TargetExecutionRecords] = {}
    execution_ids_by_run: dict[str, list[str]] = defaultdict(list)
    for row in execution_rows:
        key = (row.run_id, row.target_id)
        execution_by_key[key] = row
        execution_ids_by_run[row.run_id].append(row.target_id)

    stages_by_key: dict[tuple[str, str], list[TargetStageRecord]] = defaultdict(list)
    for row in stage_rows:
        error: StructuredError | None = None
        if row.error_code is not None and row.error_message is not None:
            error = StructuredError(
                code=row.error_code,
                message=row.error_message,
                details=row.error_details,
            )
        stages_by_key[(row.run_id, row.target_id)].append(
            TargetStageRecord(
                stage=row.stage,
                started_at=row.started_at,
                ended_at=row.ended_at,
                message=row.message,
                error=error,
                correlation_id=row.correlation_id,
                portal_link=row.portal_link,
            )
        )

    logs_by_key: dict[tuple[str, str], list[TargetLogEvent]] = defaultdict(list)
    for row in log_rows:
        logs_by_key[(row.run_id, row.target_id)].append(
            TargetLogEvent(
                timestamp=row.event_timestamp,
                level=row.level,
                stage=row.stage,
                message=row.message,
                correlation_id=row.correlation_id,
            )
        )

    runs: dict[str, DeploymentRun] = {}
    for row in run_rows:
        run_target_ids = target_ids_by_run.get(row.id, [])
        records: dict[str, TargetExecutionRecord] = {}

        for target_id in run_target_ids:
            key = (row.id, target_id)
            execution_row = execution_by_key.get(key)
            if execution_row is None:
                continue
            records[target_id] = TargetExecutionRecord(
                target_id=target_id,
                subscription_id=str(execution_row.subscription_id),
                tenant_id=str(execution_row.tenant_id),
                status=TargetStage(execution_row.status),
                attempt=execution_row.attempt,
                updated_at=execution_row.updated_at,
                stages=stages_by_key.get(key, []),
                logs=logs_by_key.get(key, []),
            )

        extra_target_ids = sorted(
            target_id
            for target_id in execution_ids_by_run.get(row.id, [])
            if target_id not in records
        )
        for target_id in extra_target_ids:
            key = (row.id, target_id)
            execution_row = execution_by_key[key]
            records[target_id] = TargetExecutionRecord(
                target_id=target_id,
                subscription_id=str(execution_row.subscription_id),
                tenant_id=str(execution_row.tenant_id),
                status=TargetStage(execution_row.status),
                attempt=execution_row.attempt,
                updated_at=execution_row.updated_at,
                stages=stages_by_key.get(key, []),
                logs=logs_by_key.get(key, []),
            )
            run_target_ids.append(target_id)

        run = DeploymentRun(
            id=row.id,
            release_id=row.release_id,
            strategy_mode=row.strategy_mode,
            wave_tag=row.wave_tag,
            wave_order=wave_order_by_run.get(row.id, []),
            concurrency=row.concurrency,
            subscription_concurrency=row.subscription_concurrency,
            stop_policy=StopPolicy(
                max_failure_count=row.stop_policy_max_failure_count,
                max_failure_rate=row.stop_policy_max_failure_rate,
            ),
            target_ids=run_target_ids,
            status=row.status,
            halt_reason=row.halt_reason,
            guardrail_warnings=warnings_by_run.get(row.id, []),
            created_at=row.created_at,
            started_at=row.started_at,
            ended_at=row.ended_at,
            updated_at=row.updated_at,
            target_records=records,
        )
        runs[run.id] = run
    return runs


def replace_targets(
    session_factory: Any,
    *,
    targets: list[Target],
    updated_at: datetime,
) -> None:
    with session_factory() as session:
        session.execute(delete(Targets))
        for target in sorted(targets, key=lambda item: item.id):
            session.add(
                Targets(
                    id=target.id,
                    tenant_id=_require_guid(target.tenant_id, field_name="target.tenant_id"),
                    subscription_id=_require_guid(
                        target.subscription_id,
                        field_name="target.subscription_id",
                    ),
                    managed_app_id=target.managed_app_id,
                    customer_name=target.customer_name,
                    last_deployed_release=target.last_deployed_release,
                    health_status=target.health_status,
                    last_check_in_at=target.last_check_in_at,
                    simulated_failure_mode=target.simulated_failure_mode,
                    updated_at=updated_at,
                )
            )
            for tag_key, tag_value in sorted(target.tags.items()):
                session.add(
                    TargetTags(
                        target_id=target.id,
                        tag_key=tag_key,
                        tag_value=tag_value,
                    )
                )
        session.commit()


def replace_releases(session_factory: Any, *, releases: list[Release]) -> None:
    with session_factory() as session:
        session.execute(delete(Releases))
        for release in sorted(releases, key=lambda item: item.created_at):
            session.add(
                Releases(
                    id=release.id,
                    template_spec_id=release.template_spec_id,
                    template_spec_version=release.template_spec_version,
                    release_notes=release.release_notes,
                    created_at=release.created_at,
                )
            )
            for key, value in sorted(release.parameter_defaults.items()):
                session.add(
                    ReleaseParameterDefaults(
                        release_id=release.id,
                        param_key=key,
                        param_value=value,
                    )
                )
            for position, hint in enumerate(release.verification_hints):
                session.add(
                    ReleaseVerificationHints(
                        release_id=release.id,
                        position=position,
                        hint=hint,
                    )
                )
        session.commit()


def save_release(session_factory: Any, *, release: Release) -> None:
    with session_factory() as session:
        row = session.get(Releases, release.id)
        if row is None:
            session.add(
                Releases(
                    id=release.id,
                    template_spec_id=release.template_spec_id,
                    template_spec_version=release.template_spec_version,
                    release_notes=release.release_notes,
                    created_at=release.created_at,
                )
            )
        else:
            row.template_spec_id = release.template_spec_id
            row.template_spec_version = release.template_spec_version
            row.release_notes = release.release_notes
            row.created_at = release.created_at
        session.execute(
            delete(ReleaseParameterDefaults).where(
                ReleaseParameterDefaults.release_id == release.id
            )
        )
        session.execute(
            delete(ReleaseVerificationHints).where(
                ReleaseVerificationHints.release_id == release.id
            )
        )
        for key, value in sorted(release.parameter_defaults.items()):
            session.add(
                ReleaseParameterDefaults(
                    release_id=release.id,
                    param_key=key,
                    param_value=value,
                )
            )
        for position, hint in enumerate(release.verification_hints):
            session.add(
                ReleaseVerificationHints(
                    release_id=release.id,
                    position=position,
                    hint=hint,
                )
            )
        session.commit()


def save_target(
    session_factory: Any,
    *,
    target: Target,
    updated_at: datetime,
) -> None:
    with session_factory() as session:
        row = session.get(Targets, target.id)
        if row is None:
            session.add(
                Targets(
                    id=target.id,
                    tenant_id=_require_guid(target.tenant_id, field_name="target.tenant_id"),
                    subscription_id=_require_guid(
                        target.subscription_id,
                        field_name="target.subscription_id",
                    ),
                    managed_app_id=target.managed_app_id,
                    customer_name=target.customer_name,
                    last_deployed_release=target.last_deployed_release,
                    health_status=target.health_status,
                    last_check_in_at=target.last_check_in_at,
                    simulated_failure_mode=target.simulated_failure_mode,
                    updated_at=updated_at,
                )
            )
        else:
            row.tenant_id = _require_guid(target.tenant_id, field_name="target.tenant_id")
            row.subscription_id = _require_guid(
                target.subscription_id,
                field_name="target.subscription_id",
            )
            row.managed_app_id = target.managed_app_id
            row.customer_name = target.customer_name
            row.last_deployed_release = target.last_deployed_release
            row.health_status = target.health_status
            row.last_check_in_at = target.last_check_in_at
            row.simulated_failure_mode = target.simulated_failure_mode
            row.updated_at = updated_at

        session.execute(delete(TargetTags).where(TargetTags.target_id == target.id))
        for tag_key, tag_value in sorted(target.tags.items()):
            session.add(
                TargetTags(
                    target_id=target.id,
                    tag_key=tag_key,
                    tag_value=tag_value,
                )
            )
        session.commit()


def save_target_registration(
    session_factory: Any,
    *,
    registration: TargetRegistrationRecord,
    updated_at: datetime,
) -> None:
    with session_factory() as session:
        row = session.get(TargetRegistrations, registration.target_id)
        if row is None:
            session.add(
                TargetRegistrations(
                    target_id=registration.target_id,
                    display_name=registration.display_name,
                    managed_application_id=registration.managed_application_id,
                    managed_resource_group_id=registration.managed_resource_group_id,
                    metadata_=registration.metadata,
                    last_event_id=registration.last_event_id,
                    created_at=registration.created_at,
                    updated_at=updated_at,
                )
            )
        else:
            row.display_name = registration.display_name
            row.managed_application_id = registration.managed_application_id
            row.managed_resource_group_id = registration.managed_resource_group_id
            row.metadata_ = registration.metadata
            row.last_event_id = registration.last_event_id
            row.created_at = registration.created_at
            row.updated_at = updated_at
        session.commit()


def delete_target(session_factory: Any, *, target_id: str) -> None:
    with session_factory() as session:
        session.execute(delete(Targets).where(Targets.id == target_id))
        session.commit()


def delete_target_registration(session_factory: Any, *, target_id: str) -> None:
    with session_factory() as session:
        session.execute(
            delete(TargetRegistrations).where(TargetRegistrations.target_id == target_id)
        )
        session.commit()


def save_marketplace_event(session_factory: Any, *, event: MarketplaceEventRecord) -> None:
    with session_factory() as session:
        row = session.get(MarketplaceEvents, event.event_id)
        if row is None:
            session.add(
                MarketplaceEvents(
                    id=event.event_id,
                    event_type=event.event_type,
                    status=event.status.value,
                    message=event.message,
                    target_id=event.target_id,
                    tenant_id=_require_guid(
                        event.tenant_id,
                        field_name="marketplace_event.tenant_id",
                    ),
                    subscription_id=_require_guid(
                        event.subscription_id,
                        field_name="marketplace_event.subscription_id",
                    ),
                    payload=event.payload,
                    created_at=event.created_at,
                    processed_at=event.processed_at,
                )
            )
        else:
            row.event_type = event.event_type
            row.status = event.status.value
            row.message = event.message
            row.target_id = event.target_id
            row.tenant_id = _require_guid(
                event.tenant_id,
                field_name="marketplace_event.tenant_id",
            )
            row.subscription_id = _require_guid(
                event.subscription_id,
                field_name="marketplace_event.subscription_id",
            )
            row.payload = event.payload
            row.created_at = event.created_at
            row.processed_at = event.processed_at
        session.commit()


def save_forwarder_log(session_factory: Any, *, record: ForwarderLogRecord) -> None:
    with session_factory() as session:
        row = session.get(ForwarderLogs, record.log_id)
        if row is None:
            session.add(
                ForwarderLogs(
                    id=record.log_id,
                    level=record.level.value,
                    message=record.message,
                    event_id=record.event_id,
                    event_type=record.event_type,
                    target_id=record.target_id,
                    tenant_id=_optional_guid(
                        record.tenant_id,
                        field_name="forwarder_log.tenant_id",
                    ),
                    subscription_id=_optional_guid(
                        record.subscription_id,
                        field_name="forwarder_log.subscription_id",
                    ),
                    function_app_name=record.function_app_name,
                    forwarder_request_id=record.forwarder_request_id,
                    backend_status_code=record.backend_status_code,
                    details=record.details,
                    created_at=record.created_at,
                )
            )
        else:
            row.level = record.level.value
            row.message = record.message
            row.event_id = record.event_id
            row.event_type = record.event_type
            row.target_id = record.target_id
            row.tenant_id = _optional_guid(
                record.tenant_id,
                field_name="forwarder_log.tenant_id",
            )
            row.subscription_id = _optional_guid(
                record.subscription_id,
                field_name="forwarder_log.subscription_id",
            )
            row.function_app_name = record.function_app_name
            row.forwarder_request_id = record.forwarder_request_id
            row.backend_status_code = record.backend_status_code
            row.details = record.details
            row.created_at = record.created_at
        session.commit()


def forwarder_log_exists(session_factory: Any, *, log_id: str) -> bool:
    with session_factory() as session:
        row = session.get(ForwarderLogs, log_id)
        return row is not None


def save_run(session_factory: Any, *, run: DeploymentRun) -> None:
    with session_factory() as session:
        row = session.get(Runs, run.id)
        if row is None:
            session.add(
                Runs(
                    id=run.id,
                    release_id=run.release_id,
                    strategy_mode=run.strategy_mode.value,
                    wave_tag=run.wave_tag,
                    concurrency=run.concurrency,
                    subscription_concurrency=run.subscription_concurrency,
                    stop_policy_max_failure_count=run.stop_policy.max_failure_count,
                    stop_policy_max_failure_rate=run.stop_policy.max_failure_rate,
                    status=run.status.value,
                    halt_reason=run.halt_reason,
                    created_at=run.created_at,
                    started_at=run.started_at,
                    ended_at=run.ended_at,
                    updated_at=run.updated_at,
                )
            )
        else:
            row.release_id = run.release_id
            row.strategy_mode = run.strategy_mode.value
            row.wave_tag = run.wave_tag
            row.concurrency = run.concurrency
            row.subscription_concurrency = run.subscription_concurrency
            row.stop_policy_max_failure_count = run.stop_policy.max_failure_count
            row.stop_policy_max_failure_rate = run.stop_policy.max_failure_rate
            row.status = run.status.value
            row.halt_reason = run.halt_reason
            row.created_at = run.created_at
            row.started_at = run.started_at
            row.ended_at = run.ended_at
            row.updated_at = run.updated_at

        session.execute(delete(TargetLogEvents).where(TargetLogEvents.run_id == run.id))
        session.execute(delete(TargetStageRecords).where(TargetStageRecords.run_id == run.id))
        session.execute(
            delete(TargetExecutionRecords).where(TargetExecutionRecords.run_id == run.id)
        )
        session.execute(delete(RunTargets).where(RunTargets.run_id == run.id))
        session.execute(
            delete(RunGuardrailWarnings).where(RunGuardrailWarnings.run_id == run.id)
        )
        session.execute(delete(RunWaveOrder).where(RunWaveOrder.run_id == run.id))

        for position, wave_value in enumerate(run.wave_order):
            session.add(
                RunWaveOrder(
                    run_id=run.id,
                    position=position,
                    wave_value=wave_value,
                )
            )

        for position, warning in enumerate(run.guardrail_warnings):
            session.add(
                RunGuardrailWarnings(
                    run_id=run.id,
                    position=position,
                    warning=warning,
                )
            )

        for position, target_id in enumerate(run.target_ids):
            session.add(
                RunTargets(
                    run_id=run.id,
                    position=position,
                    target_id=target_id,
                )
            )

        for target_id in run.target_ids:
            record = run.target_records.get(target_id)
            if record is None:
                continue
            session.add(
                TargetExecutionRecords(
                    run_id=run.id,
                    target_id=target_id,
                    subscription_id=_require_guid(
                        record.subscription_id,
                        field_name=f"run[{run.id}].target[{target_id}].subscription_id",
                    ),
                    tenant_id=_require_guid(
                        record.tenant_id,
                        field_name=f"run[{run.id}].target[{target_id}].tenant_id",
                    ),
                    status=record.status.value,
                    attempt=record.attempt,
                    updated_at=record.updated_at,
                )
            )
            for position, stage in enumerate(record.stages):
                session.add(
                    TargetStageRecords(
                        run_id=run.id,
                        target_id=target_id,
                        position=position,
                        stage=stage.stage.value,
                        started_at=stage.started_at,
                        ended_at=stage.ended_at,
                        message=stage.message,
                        error_code=stage.error.code if stage.error else None,
                        error_message=stage.error.message if stage.error else None,
                        error_details=stage.error.details if stage.error else None,
                        correlation_id=stage.correlation_id,
                        portal_link=stage.portal_link,
                    )
                )
            for position, log in enumerate(record.logs):
                session.add(
                    TargetLogEvents(
                        run_id=run.id,
                        target_id=target_id,
                        position=position,
                        event_timestamp=log.timestamp,
                        level=log.level,
                        stage=log.stage.value,
                        message=log.message,
                        correlation_id=log.correlation_id,
                    )
                )

        session.commit()


def delete_all_runs(session_factory: Any) -> None:
    with session_factory() as session:
        session.execute(delete(Runs))
        session.commit()


def delete_runs_by_ids(session_factory: Any, *, run_ids: list[str]) -> None:
    if not run_ids:
        return
    with session_factory() as session:
        session.execute(delete(Runs).where(Runs.id.in_(run_ids)))
        session.commit()
