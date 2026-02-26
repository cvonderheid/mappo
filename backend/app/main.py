from __future__ import annotations

from collections.abc import AsyncIterator
from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.api.router import api_router
from app.api.routers.health import root_router
from app.core.settings import get_settings
from app.modules.control_plane import ControlPlaneStore


@asynccontextmanager
async def app_lifespan(app: FastAPI) -> AsyncIterator[None]:
    app.state.store = ControlPlaneStore()
    yield
    await app.state.store.shutdown()


def create_app() -> FastAPI:
    settings = get_settings()

    app = FastAPI(
        title=settings.app_name,
        version=settings.app_version,
        description="MAPPO multi-tenant deployment control plane API",
        docs_url=f"{settings.api_prefix}/docs",
        redoc_url=f"{settings.api_prefix}/redoc",
        openapi_url=f"{settings.api_prefix}/openapi.json",
        lifespan=app_lifespan,
    )
    app.add_middleware(
        CORSMiddleware,
        allow_origins=list(settings.cors_origins),
        allow_credentials=True,
        allow_methods=["*"],
        allow_headers=["*"],
    )
    app.include_router(root_router)
    app.include_router(api_router)
    return app


app = create_app()
