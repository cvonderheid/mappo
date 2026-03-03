from __future__ import annotations

from collections import defaultdict
from typing import Any

from sqlalchemy import delete, select

from app.db.generated.models import (
    RunGuardrailWarnings,
    Runs,
    RunTargets,
    RunWaveOrder,
    TargetExecutionRecords,
    TargetLogEvents,
    TargetStageRecords,
)
from app.modules.schemas import (
    DeploymentMode,
    DeploymentRun,
    StopPolicy,
    StructuredError,
    TargetExecutionRecord,
    TargetLogEvent,
    TargetStage,
    TargetStageRecord,
)
from app.repositories.common import require_guid


class RunsRepository:
    def __init__(self, session_factory: Any):
        self._session_factory = session_factory

    def load_runs(self) -> dict[str, DeploymentRun]:
        with self._session_factory() as session:
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
                    .order_by(
                        RunGuardrailWarnings.run_id.asc(),
                        RunGuardrailWarnings.position.asc(),
                    )
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
                execution_mode=(
                    row.execution_mode
                    if isinstance(row.execution_mode, DeploymentMode)
                    else DeploymentMode(row.execution_mode)
                ),
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

    def save_run(self, *, run: DeploymentRun) -> None:
        with self._session_factory() as session:
            row = session.get(Runs, run.id)
            if row is None:
                session.add(
                    Runs(
                        id=run.id,
                        release_id=run.release_id,
                        execution_mode=run.execution_mode.value,
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
                row.execution_mode = run.execution_mode.value
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
                session.add(RunWaveOrder(run_id=run.id, position=position, wave_value=wave_value))

            for position, warning in enumerate(run.guardrail_warnings):
                session.add(RunGuardrailWarnings(run_id=run.id, position=position, warning=warning))

            for position, target_id in enumerate(run.target_ids):
                session.add(RunTargets(run_id=run.id, position=position, target_id=target_id))

            for target_id in run.target_ids:
                record = run.target_records.get(target_id)
                if record is None:
                    continue
                session.add(
                    TargetExecutionRecords(
                        run_id=run.id,
                        target_id=target_id,
                        subscription_id=require_guid(
                            record.subscription_id,
                            field_name=f"run[{run.id}].target[{target_id}].subscription_id",
                        ),
                        tenant_id=require_guid(
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

    def delete_all_runs(self) -> None:
        with self._session_factory() as session:
            session.execute(delete(Runs))
            session.commit()

    def delete_runs_by_ids(self, *, run_ids: list[str]) -> None:
        if not run_ids:
            return
        with self._session_factory() as session:
            session.execute(delete(Runs).where(Runs.id.in_(run_ids)))
            session.commit()
