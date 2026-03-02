from __future__ import annotations

from app.domain.runtime import ControlPlaneRuntime, StoreError
from app.modules.schemas import CreateRunRequest, RunDetail, RunSummary
from app.services.errors import ServiceError


class RunsService:
    def __init__(self, control_plane: ControlPlaneRuntime):
        self._control_plane = control_plane

    async def list_runs(self) -> list[RunSummary]:
        try:
            return await self._control_plane.list_runs()
        except StoreError as error:
            raise ServiceError(error.message) from error

    async def get_run(self, run_id: str) -> RunDetail:
        try:
            return await self._control_plane.get_run(run_id)
        except StoreError as error:
            raise ServiceError(error.message) from error

    async def create_run(self, request: CreateRunRequest) -> RunDetail:
        try:
            return await self._control_plane.create_run(request)
        except StoreError as error:
            raise ServiceError(error.message) from error

    async def resume_run(self, run_id: str) -> RunDetail:
        try:
            return await self._control_plane.resume_run(run_id)
        except StoreError as error:
            raise ServiceError(error.message) from error

    async def retry_failed(self, run_id: str) -> RunDetail:
        try:
            return await self._control_plane.retry_failed(run_id)
        except StoreError as error:
            raise ServiceError(error.message) from error

