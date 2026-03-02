from __future__ import annotations

from app.domain.runtime import ControlPlaneRuntime, StoreError
from app.modules.schemas import CreateReleaseRequest, Release
from app.services.errors import ServiceError


class ReleasesService:
    def __init__(self, control_plane: ControlPlaneRuntime):
        self._control_plane = control_plane

    async def list_releases(self) -> list[Release]:
        try:
            return await self._control_plane.list_releases()
        except StoreError as error:
            raise ServiceError(error.message) from error

    async def create_release(self, request: CreateReleaseRequest) -> Release:
        try:
            return await self._control_plane.create_release(request)
        except StoreError as error:
            raise ServiceError(error.message) from error

