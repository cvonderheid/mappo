from __future__ import annotations

from fastapi import APIRouter, Depends, HTTPException

from app.api.deps import get_releases_service
from app.modules.schemas import CreateReleaseRequest, Release
from app.services.errors import ServiceError
from app.services.releases_service import ReleasesService

router = APIRouter(prefix="/releases", tags=["releases"])


@router.get("", response_model=list[Release])
async def list_releases(service: ReleasesService = Depends(get_releases_service)) -> list[Release]:
    try:
        return await service.list_releases()
    except ServiceError as error:
        raise HTTPException(status_code=400, detail=error.message) from error


@router.post("", response_model=Release, status_code=201)
async def create_release(
    request: CreateReleaseRequest,
    service: ReleasesService = Depends(get_releases_service),
) -> Release:
    try:
        return await service.create_release(request)
    except ServiceError as error:
        raise HTTPException(status_code=400, detail=error.message) from error
