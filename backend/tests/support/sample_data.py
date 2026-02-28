from __future__ import annotations

from datetime import UTC, datetime

from app.modules.control_plane import ControlPlaneStore
from app.modules.schemas import Release, Target

DEFAULT_TEMPLATE_SPEC_ID = (
    "/subscriptions/provider-sub/resourceGroups/mappo-rg/providers/"
    "Microsoft.Resources/templateSpecs/mappo-managed-app"
)


def utc_now() -> datetime:
    return datetime.now(tz=UTC)


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
            tenant_id=f"tenant-{index:03d}",
            subscription_id=f"sub-{index:04d}",
            managed_app_id=(
                f"/subscriptions/sub-{index:04d}/resourceGroups/rg-{target_id}"
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
            parameter_defaults={"imageTag": "1.4.2", "featureFlag": "off"},
            release_notes="Stable baseline release.",
            verification_hints=["Health endpoint returns 200", "Container revision ready"],
            created_at=now,
        ),
        Release(
            id="rel-2026-02-25",
            template_spec_id=template_spec_id,
            template_spec_version="2026.02.25.3",
            parameter_defaults={"imageTag": "1.5.0", "featureFlag": "on"},
            release_notes="Canary-first rollout with new API image tag.",
            verification_hints=["App startup under 60s", "Dependency probe healthy"],
            created_at=now,
        ),
    ]


async def seed_store(store: ControlPlaneStore) -> None:
    await store.replace_targets(sample_targets(), clear_runs=True)
    await store.replace_releases(sample_releases())
