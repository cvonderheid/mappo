from __future__ import annotations

from typing import Annotated

from fastapi import APIRouter, Depends, Header, HTTPException, Query

from app.api.deps import get_admin_service
from app.core.settings import get_settings
from app.modules.schemas import (
    AdminOnboardingSnapshotResponse,
    DeleteTargetRegistrationResponse,
    ForwarderLogIngestRequest,
    ForwarderLogIngestResponse,
    ForwarderLogRecord,
    MarketplaceEventIngestRequest,
    MarketplaceEventIngestResponse,
    TargetRegistrationRecord,
    UpdateTargetRegistrationRequest,
)
from app.services.admin_service import AdminService
from app.services.errors import ServiceError

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
    service: AdminService = Depends(get_admin_service),
) -> AdminOnboardingSnapshotResponse:
    try:
        return await service.get_onboarding_snapshot(event_limit=event_limit)
    except ServiceError as error:
        raise HTTPException(status_code=400, detail=error.message) from error


@router.post("/onboarding/events", response_model=MarketplaceEventIngestResponse)
async def ingest_marketplace_event(
    request: MarketplaceEventIngestRequest,
    service: AdminService = Depends(get_admin_service),
    ingest_token: Annotated[str | None, Header(alias="x-mappo-ingest-token")] = None,
) -> MarketplaceEventIngestResponse:
    _validate_ingest_token(ingest_token)

    try:
        return await service.ingest_marketplace_event(request)
    except ServiceError as error:
        raise HTTPException(status_code=400, detail=error.message) from error


@router.get(
    "/onboarding/forwarder-logs",
    response_model=list[ForwarderLogRecord],
)
async def get_forwarder_logs(
    limit: Annotated[int, Query(ge=1, le=250)] = 50,
    service: AdminService = Depends(get_admin_service),
) -> list[ForwarderLogRecord]:
    return await service.list_forwarder_logs(limit=limit)


@router.post(
    "/onboarding/forwarder-logs",
    response_model=ForwarderLogIngestResponse,
)
async def ingest_forwarder_log(
    request: ForwarderLogIngestRequest,
    service: AdminService = Depends(get_admin_service),
    ingest_token: Annotated[str | None, Header(alias="x-mappo-ingest-token")] = None,
) -> ForwarderLogIngestResponse:
    _validate_ingest_token(ingest_token)
    return await service.ingest_forwarder_log(request)


@router.patch(
    "/onboarding/registrations/{target_id}",
    response_model=TargetRegistrationRecord,
)
async def update_target_registration(
    target_id: str,
    request: UpdateTargetRegistrationRequest,
    service: AdminService = Depends(get_admin_service),
) -> TargetRegistrationRecord:
    try:
        return await service.update_target_registration(target_id=target_id, request=request)
    except ServiceError as error:
        raise HTTPException(status_code=400, detail=error.message) from error


@router.delete(
    "/onboarding/registrations/{target_id}",
    response_model=DeleteTargetRegistrationResponse,
)
async def delete_target_registration(
    target_id: str,
    service: AdminService = Depends(get_admin_service),
) -> DeleteTargetRegistrationResponse:
    try:
        await service.delete_target_registration(target_id=target_id)
    except ServiceError as error:
        raise HTTPException(status_code=400, detail=error.message) from error
    return DeleteTargetRegistrationResponse(target_id=target_id, deleted=True)
