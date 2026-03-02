from __future__ import annotations

from fastapi import APIRouter, Depends, HTTPException

from app.api.deps import get_runs_service
from app.modules.schemas import CreateRunRequest, RunDetail, RunSummary
from app.services.errors import ServiceError
from app.services.runs_service import RunsService

router = APIRouter(prefix="/runs", tags=["runs"])


@router.get("", response_model=list[RunSummary])
async def list_runs(service: RunsService = Depends(get_runs_service)) -> list[RunSummary]:
    try:
        return await service.list_runs()
    except ServiceError as error:
        raise HTTPException(status_code=400, detail=error.message) from error


@router.get("/{run_id}", response_model=RunDetail)
async def get_run(run_id: str, service: RunsService = Depends(get_runs_service)) -> RunDetail:
    try:
        return await service.get_run(run_id)
    except ServiceError as error:
        raise HTTPException(status_code=404, detail=error.message) from error


@router.post("", response_model=RunDetail, status_code=201)
async def create_run(
    request: CreateRunRequest,
    service: RunsService = Depends(get_runs_service),
) -> RunDetail:
    try:
        return await service.create_run(request)
    except ServiceError as error:
        raise HTTPException(status_code=400, detail=error.message) from error


@router.post("/{run_id}/resume", response_model=RunDetail)
async def resume_run(run_id: str, service: RunsService = Depends(get_runs_service)) -> RunDetail:
    try:
        return await service.resume_run(run_id)
    except ServiceError as error:
        raise HTTPException(status_code=400, detail=error.message) from error


@router.post("/{run_id}/retry-failed", response_model=RunDetail)
async def retry_failed(
    run_id: str,
    service: RunsService = Depends(get_runs_service),
) -> RunDetail:
    try:
        return await service.retry_failed(run_id)
    except ServiceError as error:
        raise HTTPException(status_code=400, detail=error.message) from error
