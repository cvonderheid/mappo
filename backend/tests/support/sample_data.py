from __future__ import annotations

from datetime import UTC, datetime

from app.domain.runtime import ControlPlaneRuntime
from app.modules.schemas import Release, Target

DEFAULT_TEMPLATE_SPEC_ID = (
    "/subscriptions/provider-sub/resourceGroups/mappo-rg/providers/"
    "Microsoft.Resources/templateSpecs/mappo-managed-app"
)


def utc_now() -> datetime:
    return datetime.now(tz=UTC)


def _tenant_guid(index: int) -> str:
    return f"00000000-0000-0000-0000-{index:012d}"


def _subscription_guid(index: int) -> str:
    return f"10000000-0000-0000-0000-{index:012d}"


def sample_targets() -> list[Target]:
    now = utc_now()
    rows: list[tuple[str, str, str, str, str, str, str]] = [
        ("target-01", "canary", "eastus", "gold", "prod", "none", "healthy"),
        ("target-02", "canary", "westus", "gold", "prod", "none", "healthy"),
        ("target-03", "prod", "eastus", "gold", "prod", "none", "healthy"),
        ("target-04", "prod", "westus", "gold", "prod", "none", "healthy"),
        ("target-05", "prod", "centralus", "silver", "prod", "none", "healthy"),
        ("target-06", "prod", "eastus2", "silver", "prod", "none", "healthy"),
        ("target-07", "prod", "westus2", "silver", "prod", "verify_once", "healthy"),
        ("target-08", "prod", "southcentralus", "silver", "prod", "none", "healthy"),
        ("target-09", "prod", "northcentralus", "bronze", "prod", "verify_once", "degraded"),
        ("target-10", "prod", "eastus", "bronze", "prod", "none", "healthy"),
    ]
    return [
        Target(
            id=target_id,
            tenant_id=_tenant_guid(index),
            subscription_id=_subscription_guid(index),
            managed_app_id=(
                f"/subscriptions/{_subscription_guid(index)}/resourceGroups/rg-{target_id}"
                f"/providers/Microsoft.Solutions/applications/{target_id}"
            ),
            tags={
                "ring": ring,
                "region": region,
                "tier": tier,
                "environment": environment,
            },
            last_deployed_release="2026.02.20.1",
            health_status=health_status,
            last_check_in_at=now,
            simulated_failure_mode=failure_mode,
        )
        for index, (
            target_id,
            ring,
            region,
            tier,
            environment,
            failure_mode,
            health_status,
        ) in enumerate(rows, start=1)
    ]


def sample_releases(
    *,
    template_spec_id: str = DEFAULT_TEMPLATE_SPEC_ID,
) -> list[Release]:
    now = utc_now()
    return [
        Release(
            id="rel-2026-02-20",
            template_spec_id=template_spec_id,
            template_spec_version="2026.02.20.1",
            parameter_defaults={
                "containerImage": "docker.io/library/python:3.11-alpine",
                "softwareVersion": "2026.02.20.1",
                "dataModelVersion": "1",
            },
            release_notes="Stable baseline release with data model v1.",
            verification_hints=[
                "Target app endpoint returns JSON payload",
                "softwareVersion and dataModelVersion match release",
            ],
            created_at=now,
        ),
        Release(
            id="rel-2026-02-25",
            template_spec_id=template_spec_id,
            template_spec_version="2026.02.25.3",
            parameter_defaults={
                "containerImage": "docker.io/library/python:3.11-alpine",
                "softwareVersion": "2026.02.25.3",
                "dataModelVersion": "2",
            },
            release_notes="Canary-first rollout with data model v2.",
            verification_hints=[
                "Target app endpoint reflects software version 2026.02.25.3",
                "Target app endpoint reflects data model version 2",
            ],
            created_at=now,
        ),
    ]


async def seed_store(store: ControlPlaneRuntime) -> None:
    await store.replace_targets(sample_targets(), clear_runs=True)
    await store.replace_releases(sample_releases())
