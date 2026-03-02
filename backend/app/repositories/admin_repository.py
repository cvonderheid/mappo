from __future__ import annotations

from collections.abc import Iterator
from contextlib import contextmanager
from typing import Any

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.db.generated.models import ForwarderLogs, MarketplaceEvents
from app.modules.schemas import (
    ForwarderLogLevel,
    ForwarderLogRecord,
    MarketplaceEventRecord,
    MarketplaceEventStatus,
)
from app.repositories.common import optional_guid, require_guid


class AdminRepository:
    def __init__(
        self,
        session_factory: Any | None = None,
        *,
        session: Session | None = None,
    ):
        if session_factory is None and session is None:
            raise ValueError("AdminRepository requires a session factory or session")
        self._session_factory = session_factory
        self._session = session

    @contextmanager
    def _session_scope(self) -> Iterator[tuple[Session, bool]]:
        if self._session is not None:
            # Request-scoped sessions still need explicit commits for write operations.
            yield self._session, True
            return

        if self._session_factory is None:
            raise RuntimeError("AdminRepository requires session access")
        session = self._session_factory()
        try:
            yield session, True
        finally:
            session.close()

    def load_marketplace_events(self) -> dict[str, MarketplaceEventRecord]:
        with self._session_scope() as (session, _):
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
                status=(
                    row.status
                    if isinstance(row.status, MarketplaceEventStatus)
                    else MarketplaceEventStatus(row.status)
                ),
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

    def save_marketplace_event(self, *, event: MarketplaceEventRecord) -> None:
        with self._session_scope() as (session, should_commit):
            row = session.get(MarketplaceEvents, event.event_id)
            if row is None:
                row = MarketplaceEvents(id=event.event_id)
                session.add(row)

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

            if should_commit:
                session.commit()

    def load_forwarder_logs(self, *, limit: int = 100) -> list[ForwarderLogRecord]:
        safe_limit = max(1, limit)
        with self._session_scope() as (session, _):
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
                    level=(
                        row.level
                        if isinstance(row.level, ForwarderLogLevel)
                        else ForwarderLogLevel(row.level)
                    ),
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

    def save_forwarder_log(self, *, record: ForwarderLogRecord) -> None:
        with self._session_scope() as (session, should_commit):
            row = session.get(ForwarderLogs, record.log_id)
            if row is None:
                row = ForwarderLogs(id=record.log_id)
                session.add(row)

            row.level = record.level.value
            row.message = record.message
            row.event_id = record.event_id
            row.event_type = record.event_type
            row.target_id = record.target_id
            row.tenant_id = optional_guid(record.tenant_id, field_name="forwarder_log.tenant_id")
            row.subscription_id = optional_guid(
                record.subscription_id,
                field_name="forwarder_log.subscription_id",
            )
            row.function_app_name = record.function_app_name
            row.forwarder_request_id = record.forwarder_request_id
            row.backend_status_code = record.backend_status_code
            row.details = record.details
            row.created_at = record.created_at

            if should_commit:
                session.commit()

    def forwarder_log_exists(self, *, log_id: str) -> bool:
        with self._session_scope() as (session, _):
            return session.get(ForwarderLogs, log_id) is not None
