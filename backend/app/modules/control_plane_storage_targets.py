from __future__ import annotations

from collections import defaultdict
from datetime import datetime
from typing import Any

from sqlalchemy import delete, select

from app.db.generated.models import (
    ForwarderLogs,
    MarketplaceEvents,
    TargetRegistrations,
    Targets,
    TargetTags,
)
from app.modules.control_plane_storage_common import optional_guid, require_guid
from app.modules.schemas import (
    ForwarderLogRecord,
    MarketplaceEventRecord,
    Target,
    TargetRegistrationRecord,
)


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
            session.execute(select(ForwarderLogs).order_by(ForwarderLogs.created_at.desc()).limit(safe_limit))
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
                    tenant_id=require_guid(target.tenant_id, field_name="target.tenant_id"),
                    subscription_id=require_guid(
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
                session.add(TargetTags(target_id=target.id, tag_key=tag_key, tag_value=tag_value))
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
                    tenant_id=require_guid(target.tenant_id, field_name="target.tenant_id"),
                    subscription_id=require_guid(
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
            row.tenant_id = require_guid(target.tenant_id, field_name="target.tenant_id")
            row.subscription_id = require_guid(
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
            session.add(TargetTags(target_id=target.id, tag_key=tag_key, tag_value=tag_value))
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
                    tenant_id=require_guid(
                        event.tenant_id,
                        field_name="marketplace_event.tenant_id",
                    ),
                    subscription_id=require_guid(
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
            row.tenant_id = require_guid(
                event.tenant_id,
                field_name="marketplace_event.tenant_id",
            )
            row.subscription_id = require_guid(
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
                    tenant_id=optional_guid(
                        record.tenant_id,
                        field_name="forwarder_log.tenant_id",
                    ),
                    subscription_id=optional_guid(
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
            row.tenant_id = optional_guid(
                record.tenant_id,
                field_name="forwarder_log.tenant_id",
            )
            row.subscription_id = optional_guid(
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
