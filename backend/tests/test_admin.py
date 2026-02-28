from __future__ import annotations

from datetime import UTC, datetime
from typing import Any

from fastapi.testclient import TestClient
from pytest import MonkeyPatch

from app.modules.discovery import DiscoverImportResult, DiscoveryBlockedScope
from app.modules.schemas import Target


def _sample_target(
    *,
    target_id: str,
    tenant_id: str,
    subscription_id: str,
    resource_id: str,
    ring: str,
) -> Target:
    return Target(
        id=target_id,
        tenant_id=tenant_id,
        subscription_id=subscription_id,
        managed_app_id=resource_id,
        tags={
            "ring": ring,
            "region": "eastus",
            "environment": "prod",
            "tier": "gold",
        },
        last_deployed_release="2026.02.20.1",
        health_status="healthy",
        last_check_in_at=datetime.now(tz=UTC),
        simulated_failure_mode="none",
    )


def test_admin_discover_import_replaces_targets(
    client: TestClient,
    monkeypatch: MonkeyPatch,
) -> None:
    discovered = [
        _sample_target(
            target_id="mappo-ma-target-01",
            tenant_id="tenant-a",
            subscription_id="sub-a",
            resource_id=(
                "/subscriptions/sub-a/resourceGroups/rg-a/providers/"
                "Microsoft.App/containerApps/ca-a"
            ),
            ring="canary",
        ),
        _sample_target(
            target_id="mappo-ma-target-02",
            tenant_id="tenant-b",
            subscription_id="sub-b",
            resource_id=(
                "/subscriptions/sub-b/resourceGroups/rg-b/providers/"
                "Microsoft.App/containerApps/ca-b"
            ),
            ring="prod",
        ),
    ]

    def _stub_discover(**_: Any) -> DiscoverImportResult:
        return DiscoverImportResult(
            targets=discovered,
            discovered_managed_apps=2,
            skipped_managed_apps=0,
            subscriptions_scanned=2,
            scanned_subscription_ids=["sub-a", "sub-b"],
            auto_discovered_subscription_ids=[],
            blocked_enumeration=[],
            warnings=[],
        )

    monkeypatch.setattr("app.api.routers.admin.discover_targets_with_sdk", _stub_discover)

    response = client.post(
        "/api/v1/admin/discover-import",
        json={
            "subscription_ids": ["sub-a", "sub-b"],
            "managed_app_name_prefix": "mappo-ma",
            "clear_runs": True,
        },
    )

    assert response.status_code == 200
    payload = response.json()
    assert payload["imported_targets"] == 2
    assert payload["discovered_targets"] == 2
    assert payload["discovered_managed_apps"] == 2
    assert payload["subscriptions_scanned"] == 2

    targets_response = client.get("/api/v1/targets")
    assert targets_response.status_code == 200
    targets_payload = targets_response.json()
    assert len(targets_payload) == 2
    assert {target["id"] for target in targets_payload} == {
        "mappo-ma-target-01",
        "mappo-ma-target-02",
    }


def test_admin_discover_import_requires_subscriptions(client: TestClient) -> None:
    response = client.post(
        "/api/v1/admin/discover-import",
        json={
            "subscription_ids": [],
            "auto_enumerate_subscriptions": False,
        },
    )

    assert response.status_code == 400
    assert (
        response.json()["detail"]
        == "provide subscription IDs or enable auto-enumeration"
    )


def test_admin_discover_import_returns_blocked_enumeration(
    client: TestClient,
    monkeypatch: MonkeyPatch,
) -> None:
    discovered = [
        _sample_target(
            target_id="mappo-ma-target-01",
            tenant_id="tenant-a",
            subscription_id="sub-a",
            resource_id=(
                "/subscriptions/sub-a/resourceGroups/rg-a/providers/"
                "Microsoft.App/containerApps/ca-a"
            ),
            ring="canary",
        ),
    ]

    def _stub_discover(**_: Any) -> DiscoverImportResult:
        return DiscoverImportResult(
            targets=discovered,
            discovered_managed_apps=1,
            skipped_managed_apps=1,
            subscriptions_scanned=1,
            scanned_subscription_ids=["sub-a"],
            auto_discovered_subscription_ids=["sub-a"],
            blocked_enumeration=[
                DiscoveryBlockedScope(
                    scope_type="tenant",
                    scope_id="tenant-a",
                    reason="AuthorizationFailed",
                )
            ],
            warnings=["subscription enumeration blocked in tenant tenant-a"],
        )

    monkeypatch.setattr("app.api.routers.admin.discover_targets_with_sdk", _stub_discover)

    response = client.post(
        "/api/v1/admin/discover-import",
        json={
            "subscription_ids": [],
            "auto_enumerate_subscriptions": True,
            "managed_app_name_prefix": "mappo-ma",
            "clear_runs": True,
        },
    )

    assert response.status_code == 200
    payload = response.json()
    assert payload["auto_discovered_subscription_ids"] == ["sub-a"]
    assert payload["scanned_subscription_ids"] == ["sub-a"]
    assert payload["blocked_enumeration"] == [
        {
            "scope_type": "tenant",
            "scope_id": "tenant-a",
            "reason": "AuthorizationFailed",
        }
    ]
