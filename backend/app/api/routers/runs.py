from __future__ import annotations

from fastapi import APIRouter, Depends, HTTPException

from app.api.deps import get_store
from app.modules.control_plane import ControlPlaneStore, StoreError
from app.modules.schemas import CreateRunRequest, RunDetail, RunSummary

router = APIRouter(prefix="/runs", tags=["runs"])


@router.get("", response_model=list[RunSummary])
async def list_runs(store: ControlPlaneStore = Depends(get_store)) -> list[RunSummary]:
    return await store.list_runs()


@router.get("/{run_id}", response_model=RunDetail)
async def get_run(run_id: str, store: ControlPlaneStore = Depends(get_store)) -> RunDetail:
    try:
        return await store.get_run(run_id)
    except StoreError as error:
        raise HTTPException(status_code=404, detail=error.message) from error


@router.post("", response_model=RunDetail, status_code=201)
async def create_run(
    request: CreateRunRequest,
    store: ControlPlaneStore = Depends(get_store),
) -> RunDetail:
    try:
        return await store.create_run(request)
    except StoreError as error:
        raise HTTPException(status_code=400, detail=error.message) from error


@router.post("/{run_id}/resume", response_model=RunDetail)
async def resume_run(run_id: str, store: ControlPlaneStore = Depends(get_store)) -> RunDetail:
    try:
        return await store.resume_run(run_id)
    except StoreError as error:
        raise HTTPException(status_code=400, detail=error.message) from error


@router.post("/{run_id}/retry-failed", response_model=RunDetail)
async def retry_failed(run_id: str, store: ControlPlaneStore = Depends(get_store)) -> RunDetail:
    try:
        return await store.retry_failed(run_id)
    except StoreError as error:
        raise HTTPException(status_code=400, detail=error.message) from error
