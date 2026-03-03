from __future__ import annotations

import argparse
import asyncio
from datetime import UTC, datetime

from app.core.settings import get_settings
from app.domain.runtime import ControlPlaneRuntime
from app.modules.execution import AzureExecutorSettings
from app.modules.schemas import Release

DEFAULT_TEMPLATE_SPEC_ID = (
    "/subscriptions/provider-sub/resourceGroups/mappo-rg/providers/"
    "Microsoft.Resources/templateSpecs/mappo-managed-app"
)


def utc_now() -> datetime:
    return datetime.now(tz=UTC)


def default_releases(*, template_spec_id: str) -> list[Release]:
    now = utc_now()
    return [
        Release(
            id="rel-2026-02-20",
            template_spec_id=template_spec_id,
            template_spec_version="2026.02.20.1",
            parameter_defaults={
                "containerImage": "mcr.microsoft.com/azuredocs/containerapps-helloworld:latest",
                "featureFlag": "off",
            },
            release_notes="Stable baseline release.",
            verification_hints=["Health endpoint returns 200", "Container revision ready"],
            created_at=now,
        ),
        Release(
            id="rel-2026-02-25",
            template_spec_id=template_spec_id,
            template_spec_version="2026.02.25.3",
            parameter_defaults={
                "containerImage": "mcr.microsoft.com/azuredocs/containerapps-helloworld:latest",
                "featureFlag": "on",
            },
            release_notes="Canary-first rollout with feature flag enabled.",
            verification_hints=["App startup under 60s", "Dependency probe healthy"],
            created_at=now,
        ),
    ]


async def main() -> None:
    parser = argparse.ArgumentParser(
        description="Bootstrap default releases for local/demo execution."
    )
    parser.add_argument(
        "--force",
        action="store_true",
        help="Replace all existing releases with default release set.",
    )
    parser.add_argument(
        "--template-spec-id",
        default=DEFAULT_TEMPLATE_SPEC_ID,
        help=(
            "Template Spec ID to embed in default releases "
            f"(default: {DEFAULT_TEMPLATE_SPEC_ID})"
        ),
    )
    args = parser.parse_args()

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
        existing = await store.list_releases()
        if existing and not args.force:
            print(
                f"bootstrap-releases: found {len(existing)} existing release(s); "
                "skipping (use --force to replace)."
            )
            return

        releases = default_releases(template_spec_id=args.template_spec_id)
        await store.replace_releases(releases)
        action = "replaced" if existing else "created"
        print(f"bootstrap-releases: {action} {len(releases)} release(s).")
    finally:
        await store.shutdown()


if __name__ == "__main__":
    asyncio.run(main())
