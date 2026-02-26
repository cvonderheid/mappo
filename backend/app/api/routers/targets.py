from __future__ import annotations

from fastapi import APIRouter, Depends, Query

from app.api.deps import get_store
from app.modules.control_plane import ControlPlaneStore
from app.modules.schemas import Target

router = APIRouter(prefix="/targets", tags=["targets"])


@router.get("", response_model=list[Target])
async def list_targets(
    ring: str | None = Query(default=None),
    region: str | None = Query(default=None),
    tier: str | None = Query(default=None),
    environment: str | None = Query(default=None),
    store: ControlPlaneStore = Depends(get_store),
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
    return await store.list_targets(filters)
