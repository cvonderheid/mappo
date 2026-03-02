from __future__ import annotations

from app.domain.runtime import ControlPlaneRuntime, StoreError
from app.modules.schemas import Target
from app.services.errors import ServiceError


class TargetsService:
    def __init__(self, control_plane: ControlPlaneRuntime):
        self._control_plane = control_plane

    async def list_targets(self, tag_filters: dict[str, str] | None = None) -> list[Target]:
        try:
            return await self._control_plane.list_targets(tag_filters)
        except StoreError as error:
            raise ServiceError(error.message) from error

