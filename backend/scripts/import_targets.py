from __future__ import annotations

import argparse
import asyncio
import json
from datetime import UTC, datetime
from pathlib import Path
from typing import Any

from app.core.settings import get_settings
from app.domain.runtime import ControlPlaneRuntime, StoreError
from app.modules.execution import AzureExecutorSettings
from app.modules.schemas import Target

DEFAULT_INVENTORY_PATH = (
    Path(__file__).resolve().parents[2] / ".data" / "mappo-target-inventory.json"
)
DEFAULT_BASELINE_RELEASE = "2026.02.20.1"


def utc_now() -> datetime:
    return datetime.now(tz=UTC)


async def main() -> None:
    parser = argparse.ArgumentParser(
        description="Import MAPPO targets from discovered inventory JSON."
    )
    parser.add_argument(
        "--file",
        type=Path,
        default=DEFAULT_INVENTORY_PATH,
        help=f"Path to inventory JSON (default: {DEFAULT_INVENTORY_PATH})",
    )
    parser.add_argument(
        "--clear-runs",
        action="store_true",
        help="Delete all existing run history when replacing targets.",
    )
    args = parser.parse_args()

    payload_path = args.file.expanduser().resolve()
    if not payload_path.exists():
        raise SystemExit(f"import-targets: file not found: {payload_path}")

    raw_payload = json.loads(payload_path.read_text(encoding="utf-8"))
    if not isinstance(raw_payload, list):
        raise SystemExit("import-targets: expected top-level JSON array")

    settings = get_settings()
    store = ControlPlaneRuntime(
        database_url=settings.database_url,
        execution_mode=settings.execution_mode,
        azure_settings=AzureExecutorSettings(
            tenant_id=settings.azure_tenant_id,
            client_id=settings.azure_client_id,
            client_secret=settings.azure_client_secret,
            tenant_by_subscription=settings.azure_tenant_by_subscription,
        ),
        retention_days=settings.retention_days,
        stage_delay_seconds=0.0,
    )
    try:
        existing_targets = await store.list_targets()
        by_existing_id = {target.id: target for target in existing_targets}
        imported = [_to_target(row, existing_by_id=by_existing_id) for row in raw_payload]
        await store.replace_targets(imported, clear_runs=args.clear_runs)
    except StoreError as error:
        raise SystemExit(f"import-targets: {error.message}") from error
    finally:
        await store.shutdown()

    print(
        f"import-targets: imported {len(raw_payload)} targets from {payload_path} "
        f"(clear_runs={args.clear_runs})"
    )


def _to_target(row: Any, *, existing_by_id: dict[str, Target]) -> Target:
    if not isinstance(row, dict):
        raise SystemExit("import-targets: each inventory row must be a JSON object")

    def required_string(key: str) -> str:
        value = row.get(key)
        if not isinstance(value, str) or value.strip() == "":
            raise SystemExit(f"import-targets: row missing required string field '{key}'")
        return value.strip()

    tags_raw = row.get("tags")
    tags: dict[str, str] = {}
    if isinstance(tags_raw, dict):
        tags = {str(k): str(v) for k, v in tags_raw.items()}

    target_id = required_string("id")
    existing = existing_by_id.get(target_id)
    return Target(
        id=target_id,
        tenant_id=required_string("tenant_id"),
        subscription_id=required_string("subscription_id"),
        managed_app_id=required_string("managed_app_id"),
        tags=tags,
        last_deployed_release=(
            existing.last_deployed_release if existing is not None else DEFAULT_BASELINE_RELEASE
        ),
        health_status=(existing.health_status if existing is not None else "healthy"),
        last_check_in_at=utc_now(),
        simulated_failure_mode=(
            existing.simulated_failure_mode if existing is not None else "none"
        ),
    )


if __name__ == "__main__":
    asyncio.run(main())
