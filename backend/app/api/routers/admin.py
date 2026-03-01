from __future__ import annotations

from datetime import UTC, datetime
from typing import Annotated

from fastapi import APIRouter, Depends, Header, HTTPException, Query

from app.api.deps import get_store
from app.core.settings import get_settings
from app.modules.control_plane import ControlPlaneStore, StoreError
from app.modules.control_plane_storage import (
    forwarder_log_exists,
    load_forwarder_logs,
    save_forwarder_log,
)
from app.modules.schemas import (
    AdminOnboardingSnapshotResponse,
    DeleteTargetRegistrationResponse,
    ForwarderLogIngestRequest,
    ForwarderLogIngestResponse,
    ForwarderLogIngestStatus,
    ForwarderLogRecord,
    MarketplaceEventIngestRequest,
    MarketplaceEventIngestResponse,
    TargetRegistrationRecord,
    UpdateTargetRegistrationRequest,
)

router = APIRouter(prefix="/admin", tags=["admin"])


def _validate_ingest_token(ingest_token: str | None) -> None:
    settings = get_settings()
    required_token = settings.marketplace_ingest_token
    if required_token is not None and required_token != "":
        if ingest_token != required_token:
            raise HTTPException(status_code=401, detail="invalid marketplace ingest token")


@router.get("/onboarding", response_model=AdminOnboardingSnapshotResponse)
async def get_onboarding_snapshot(
    event_limit: Annotated[int, Query(ge=1, le=250)] = 50,
    store: ControlPlaneStore = Depends(get_store),
) -> AdminOnboardingSnapshotResponse:
    snapshot = await store.get_onboarding_snapshot(event_limit=event_limit)
    forwarder_logs = load_forwarder_logs(store.session_factory, limit=event_limit)
    return snapshot.model_copy(update={"forwarder_logs": forwarder_logs})


@router.post("/onboarding/events", response_model=MarketplaceEventIngestResponse)
async def ingest_marketplace_event(
    request: MarketplaceEventIngestRequest,
    store: ControlPlaneStore = Depends(get_store),
    ingest_token: Annotated[str | None, Header(alias="x-mappo-ingest-token")] = None,
) -> MarketplaceEventIngestResponse:
    _validate_ingest_token(ingest_token)

    try:
        return await store.ingest_marketplace_event(request)
    except StoreError as error:
        raise HTTPException(status_code=400, detail=error.message) from error


@router.get(
    "/onboarding/forwarder-logs",
    response_model=list[ForwarderLogRecord],
)
async def get_forwarder_logs(
    limit: Annotated[int, Query(ge=1, le=250)] = 50,
    store: ControlPlaneStore = Depends(get_store),
) -> list[ForwarderLogRecord]:
    return load_forwarder_logs(store.session_factory, limit=limit)


@router.post(
    "/onboarding/forwarder-logs",
    response_model=ForwarderLogIngestResponse,
)
async def ingest_forwarder_log(
    request: ForwarderLogIngestRequest,
    store: ControlPlaneStore = Depends(get_store),
    ingest_token: Annotated[str | None, Header(alias="x-mappo-ingest-token")] = None,
) -> ForwarderLogIngestResponse:
    _validate_ingest_token(ingest_token)
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
    if forwarder_log_exists(store.session_factory, log_id=record.log_id):
        return ForwarderLogIngestResponse(
            log_id=record.log_id,
            status=ForwarderLogIngestStatus.DUPLICATE,
            message="Forwarder log already recorded.",
        )
    save_forwarder_log(store.session_factory, record=record)
    return ForwarderLogIngestResponse(
        log_id=record.log_id,
        status=ForwarderLogIngestStatus.APPLIED,
        message="Forwarder log recorded.",
    )


@router.patch(
    "/onboarding/registrations/{target_id}",
    response_model=TargetRegistrationRecord,
)
async def update_target_registration(
    target_id: str,
    request: UpdateTargetRegistrationRequest,
    store: ControlPlaneStore = Depends(get_store),
) -> TargetRegistrationRecord:
    try:
        return await store.update_target_registration(target_id=target_id, request=request)
    except StoreError as error:
        raise HTTPException(status_code=400, detail=error.message) from error


@router.delete(
    "/onboarding/registrations/{target_id}",
    response_model=DeleteTargetRegistrationResponse,
)
async def delete_target_registration(
    target_id: str,
    store: ControlPlaneStore = Depends(get_store),
) -> DeleteTargetRegistrationResponse:
    try:
        await store.delete_target_registration(target_id=target_id)
    except StoreError as error:
        raise HTTPException(status_code=400, detail=error.message) from error
    return DeleteTargetRegistrationResponse(target_id=target_id, deleted=True)
