from __future__ import annotations

from typing import cast

from fastapi import Request

from app.modules.control_plane import ControlPlaneStore


def get_store(request: Request) -> ControlPlaneStore:
    return cast(ControlPlaneStore, request.app.state.store)
