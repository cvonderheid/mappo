from __future__ import annotations

import argparse
import asyncio

from app.core.settings import get_settings
from app.modules.control_plane import ControlPlaneStore


async def main() -> None:
    parser = argparse.ArgumentParser(description="Prune MAPPO run history by retention window.")
    parser.add_argument("--days", type=int, default=None, help="Retention window in days")
    args = parser.parse_args()

    settings = get_settings()
    store = ControlPlaneStore(
        database_url=settings.database_url,
        retention_days=settings.retention_days,
        stage_delay_seconds=0.0,
    )

    try:
        deleted_count = await store.prune_retention(args.days)
        active_days = settings.retention_days if args.days is None else max(1, args.days)
        print(
            f"retention-prune: removed {deleted_count} runs older than {active_days} days "
            "from postgres"
        )
    finally:
        await store.shutdown()


if __name__ == "__main__":
    asyncio.run(main())
