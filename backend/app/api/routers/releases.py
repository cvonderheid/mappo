from __future__ import annotations

from fastapi import APIRouter, Depends, HTTPException

from app.api.deps import get_store
from app.modules.control_plane import ControlPlaneStore, StoreError
from app.modules.schemas import CreateReleaseRequest, Release

router = APIRouter(prefix="/releases", tags=["releases"])


@router.get("", response_model=list[Release])
async def list_releases(store: ControlPlaneStore = Depends(get_store)) -> list[Release]:
    return await store.list_releases()


@router.post("", response_model=Release, status_code=201)
async def create_release(
    request: CreateReleaseRequest,
    store: ControlPlaneStore = Depends(get_store),
) -> Release:
    try:
        return await store.create_release(request)
    except StoreError as error:
        raise HTTPException(status_code=400, detail=error.message) from error
