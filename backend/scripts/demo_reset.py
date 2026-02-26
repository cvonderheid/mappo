from __future__ import annotations

import asyncio

from app.core.settings import get_settings
from app.modules.control_plane import ControlPlaneStore


async def main() -> None:
    settings = get_settings()
    store = ControlPlaneStore(
        database_url=settings.database_url,
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
