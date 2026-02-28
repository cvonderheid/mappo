from __future__ import annotations

import asyncio

from fastapi import APIRouter, Depends, HTTPException

from app.api.deps import get_store
from app.core.settings import get_settings
from app.modules.control_plane import ControlPlaneStore, StoreError
from app.modules.discovery import AzureDiscoveryError, discover_targets_with_sdk
from app.modules.execution import AzureExecutorSettings
from app.modules.schemas import (
    AdminDiscoverImportRequest,
    AdminDiscoverImportResponse,
    AdminDiscoveryBlockedScope,
)

router = APIRouter(prefix="/admin", tags=["admin"])


@router.post("/discover-import", response_model=AdminDiscoverImportResponse)
async def discover_and_import_targets(
    request: AdminDiscoverImportRequest,
    store: ControlPlaneStore = Depends(get_store),
) -> AdminDiscoverImportResponse:
    if not request.subscription_ids and not request.auto_enumerate_subscriptions:
        raise HTTPException(
            status_code=400,
            detail="provide subscription IDs or enable auto-enumeration",
        )

    settings = get_settings()
    existing_targets = await store.list_targets()
    by_existing_id = {target.id: target for target in existing_targets}

    try:
        result = await asyncio.to_thread(
            discover_targets_with_sdk,
            subscription_ids=request.subscription_ids,
            auto_enumerate_subscriptions=request.auto_enumerate_subscriptions,
            managed_app_name_prefix=request.managed_app_name_prefix,
            preferred_container_app_name=request.preferred_container_app_name,
            default_target_group=request.default_target_group,
            group_tag_key=request.group_tag_key,
            azure_settings=AzureExecutorSettings(
                tenant_id=settings.azure_tenant_id,
                client_id=settings.azure_client_id,
                client_secret=settings.azure_client_secret,
            ),
            existing_targets_by_id=by_existing_id,
        )
        await store.replace_targets(result.targets, clear_runs=request.clear_runs)
    except AzureDiscoveryError as error:
        raise HTTPException(status_code=400, detail=error.message) from error
    except StoreError as error:
        raise HTTPException(status_code=400, detail=error.message) from error

    return AdminDiscoverImportResponse(
        imported_targets=len(result.targets),
        discovered_targets=len(result.targets),
        discovered_managed_apps=result.discovered_managed_apps,
        skipped_managed_apps=result.skipped_managed_apps,
        subscriptions_scanned=result.subscriptions_scanned,
        scanned_subscription_ids=result.scanned_subscription_ids,
        auto_discovered_subscription_ids=result.auto_discovered_subscription_ids,
        blocked_enumeration=[
            AdminDiscoveryBlockedScope(
                scope_type=item.scope_type,
                scope_id=item.scope_id,
                reason=item.reason,
            )
            for item in result.blocked_enumeration
        ],
        warnings=result.warnings,
        target_ids=sorted(target.id for target in result.targets),
    )
