from __future__ import annotations

from collections.abc import AsyncIterator
from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.api.router import api_router
from app.api.routers.health import root_router
from app.core.settings import get_settings
from app.modules.control_plane import ControlPlaneStore
from app.modules.execution import AzureExecutorSettings


def create_app() -> FastAPI:
    settings = get_settings()

    @asynccontextmanager
    async def app_lifespan(app: FastAPI) -> AsyncIterator[None]:
        app.state.store = ControlPlaneStore(
            database_url=settings.database_url,
            execution_mode=settings.execution_mode,
            azure_settings=AzureExecutorSettings(
                tenant_id=settings.azure_tenant_id,
                client_id=settings.azure_client_id,
                client_secret=settings.azure_client_secret,
                max_run_concurrency=settings.azure_max_run_concurrency,
                max_subscription_concurrency=settings.azure_max_subscription_concurrency,
                max_retry_attempts=settings.azure_max_retry_attempts,
                retry_base_delay_seconds=settings.azure_retry_base_delay_seconds,
                retry_max_delay_seconds=settings.azure_retry_max_delay_seconds,
                retry_jitter_seconds=settings.azure_retry_jitter_seconds,
                enable_quota_preflight=settings.azure_enable_quota_preflight,
                quota_warning_headroom_ratio=settings.azure_quota_warning_headroom_ratio,
                quota_min_remaining_warning=settings.azure_quota_min_remaining_warning,
            ),
            retention_days=settings.retention_days,
        )
        yield
        await app.state.store.shutdown()

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
