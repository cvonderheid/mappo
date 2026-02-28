from __future__ import annotations

from typing import Annotated

from fastapi import APIRouter, Depends, Header, HTTPException, Query

from app.api.deps import get_store
from app.core.settings import get_settings
from app.modules.control_plane import ControlPlaneStore, StoreError
from app.modules.schemas import (
    AdminOnboardingSnapshotResponse,
    MarketplaceEventIngestRequest,
    MarketplaceEventIngestResponse,
)

router = APIRouter(prefix="/admin", tags=["admin"])


@router.get("/onboarding", response_model=AdminOnboardingSnapshotResponse)
async def get_onboarding_snapshot(
    event_limit: Annotated[int, Query(ge=1, le=250)] = 50,
    store: ControlPlaneStore = Depends(get_store),
) -> AdminOnboardingSnapshotResponse:
    return await store.get_onboarding_snapshot(event_limit=event_limit)


@router.post("/onboarding/events", response_model=MarketplaceEventIngestResponse)
async def ingest_marketplace_event(
    request: MarketplaceEventIngestRequest,
    store: ControlPlaneStore = Depends(get_store),
    ingest_token: Annotated[str | None, Header(alias="x-mappo-ingest-token")] = None,
) -> MarketplaceEventIngestResponse:
    settings = get_settings()
    required_token = settings.marketplace_ingest_token
    if required_token is not None and required_token != "":
        if ingest_token != required_token:
            raise HTTPException(status_code=401, detail="invalid marketplace ingest token")

    try:
        return await store.ingest_marketplace_event(request)
    except StoreError as error:
        raise HTTPException(status_code=400, detail=error.message) from error
