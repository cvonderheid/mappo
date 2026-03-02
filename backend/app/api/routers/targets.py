from __future__ import annotations

from fastapi import APIRouter, Depends, HTTPException, Query

from app.api.deps import get_targets_service
from app.modules.schemas import Target
from app.services.errors import ServiceError
from app.services.targets_service import TargetsService

router = APIRouter(prefix="/targets", tags=["targets"])


@router.get("", response_model=list[Target])
async def list_targets(
    ring: str | None = Query(default=None),
    region: str | None = Query(default=None),
    tier: str | None = Query(default=None),
    environment: str | None = Query(default=None),
    service: TargetsService = Depends(get_targets_service),
) -> list[Target]:
    filters = {
        key: value
        for key, value in {
            "ring": ring,
            "region": region,
            "tier": tier,
            "environment": environment,
        }.items()
        if value is not None
    }
    try:
        return await service.list_targets(filters)
    except ServiceError as error:
        raise HTTPException(status_code=400, detail=error.message) from error
