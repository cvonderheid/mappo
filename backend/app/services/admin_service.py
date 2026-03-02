from __future__ import annotations

from datetime import UTC, datetime

from app.domain.runtime import ControlPlaneRuntime, StoreError
from app.modules.schemas import (
    AdminOnboardingSnapshotResponse,
    ForwarderLogIngestRequest,
    ForwarderLogIngestResponse,
    ForwarderLogIngestStatus,
    ForwarderLogRecord,
    MarketplaceEventIngestRequest,
    MarketplaceEventIngestResponse,
    TargetRegistrationRecord,
    UpdateTargetRegistrationRequest,
)
from app.repositories.admin_repository import AdminRepository
from app.services.errors import ServiceError


class AdminService:
    def __init__(
        self,
        control_plane: ControlPlaneRuntime,
        admin_repository: AdminRepository,
    ):
        self._control_plane = control_plane
        self._repository = admin_repository

    async def get_onboarding_snapshot(
        self,
        *,
        event_limit: int = 50,
    ) -> AdminOnboardingSnapshotResponse:
        try:
            snapshot = await self._control_plane.get_onboarding_snapshot(event_limit=event_limit)
        except StoreError as error:
            raise ServiceError(error.message) from error

        forwarder_logs = self._repository.load_forwarder_logs(limit=event_limit)
        return snapshot.model_copy(update={"forwarder_logs": forwarder_logs})

    async def ingest_marketplace_event(
        self,
        request: MarketplaceEventIngestRequest,
    ) -> MarketplaceEventIngestResponse:
        try:
            return await self._control_plane.ingest_marketplace_event(request)
        except StoreError as error:
            raise ServiceError(error.message) from error

    async def list_forwarder_logs(self, *, limit: int = 50) -> list[ForwarderLogRecord]:
        return self._repository.load_forwarder_logs(limit=limit)

    async def ingest_forwarder_log(
        self,
        request: ForwarderLogIngestRequest,
    ) -> ForwarderLogIngestResponse:
        created_at = request.occurred_at or datetime.now(tz=UTC)
        record = ForwarderLogRecord(
            log_id=request.log_id,
            level=request.level,
            message=request.message,
            event_id=request.event_id,
            event_type=request.event_type,
            target_id=request.target_id,
            tenant_id=request.tenant_id,
            subscription_id=request.subscription_id,
            function_app_name=request.function_app_name,
            forwarder_request_id=request.forwarder_request_id,
            backend_status_code=request.backend_status_code,
            details=request.details,
            created_at=created_at,
        )
        if self._repository.forwarder_log_exists(log_id=record.log_id):
            return ForwarderLogIngestResponse(
                log_id=record.log_id,
                status=ForwarderLogIngestStatus.DUPLICATE,
                message="Forwarder log already recorded.",
            )
        self._repository.save_forwarder_log(record=record)
        return ForwarderLogIngestResponse(
            log_id=record.log_id,
            status=ForwarderLogIngestStatus.APPLIED,
            message="Forwarder log recorded.",
        )

    async def update_target_registration(
        self,
        *,
        target_id: str,
        request: UpdateTargetRegistrationRequest,
    ) -> TargetRegistrationRecord:
        try:
            return await self._control_plane.update_target_registration(
                target_id=target_id,
                request=request,
            )
        except StoreError as error:
            raise ServiceError(error.message) from error

    async def delete_target_registration(self, *, target_id: str) -> None:
        try:
            await self._control_plane.delete_target_registration(target_id=target_id)
        except StoreError as error:
            raise ServiceError(error.message) from error
