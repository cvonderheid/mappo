from __future__ import annotations

import asyncio

from app.core.settings import get_settings
from app.modules.control_plane import ControlPlaneStore
from app.modules.execution import AzureExecutorSettings


async def main() -> None:
    settings = get_settings()
    store = ControlPlaneStore(
        database_url=settings.database_url,
        execution_mode=settings.execution_mode,
        azure_settings=AzureExecutorSettings(
            tenant_id=settings.azure_tenant_id,
            client_id=settings.azure_client_id,
            client_secret=settings.azure_client_secret,
        ),
        retention_days=settings.retention_days,
        stage_delay_seconds=0.0,
    )
    try:
        await store.reset_demo_data()
        print("demo-reset: seeded 10 targets + releases into postgres")
    finally:
        await store.shutdown()


if __name__ == "__main__":
    asyncio.run(main())
