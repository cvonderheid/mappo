from __future__ import annotations

from fastapi.testclient import TestClient
from pytest import MonkeyPatch

from app.core.settings import get_settings


def _sample_onboarding_event(event_id: str) -> dict[str, object]:
    return {
        "event_id": event_id,
        "event_type": "subscription_purchased",
        "tenant_id": "tenant-live-a",
        "subscription_id": "sub-live-a",
        "managed_application_id": (
            "/subscriptions/sub-live-a/resourceGroups/rg-mappo-ma-apps-live/providers/"
            "Microsoft.Solutions/applications/mappo-ma-target-live-01"
        ),
        "managed_resource_group_id": (
            "/subscriptions/sub-live-a/resourceGroups/rg-mappo-ma-mrg-live-01"
        ),
        "container_app_resource_id": (
            "/subscriptions/sub-live-a/resourceGroups/rg-mappo-ma-mrg-live-01/providers/"
            "Microsoft.App/containerApps/ca-mappo-ma-target-live-01"
        ),
        "customer_name": "Contoso",
        "target_group": "canary",
        "region": "eastus",
        "environment": "prod",
        "tier": "gold",
        "metadata": {"source": "partner-center"},
    }


def test_admin_onboarding_event_registers_target(client: TestClient) -> None:
    response = client.post(
        "/api/v1/admin/onboarding/events",
        json=_sample_onboarding_event("evt-001"),
    )
    assert response.status_code == 200
    payload = response.json()
    assert payload["status"] == "applied"
    assert payload["target_id"] == "mappo-ma-target-live-01"

    targets_response = client.get("/api/v1/targets")
    assert targets_response.status_code == 200
    target_ids = {item["id"] for item in targets_response.json()}
    assert "mappo-ma-target-live-01" in target_ids

    snapshot_response = client.get("/api/v1/admin/onboarding")
    assert snapshot_response.status_code == 200
    snapshot = snapshot_response.json()
    assert snapshot["registrations"][0]["target_id"] == "mappo-ma-target-live-01"
    assert snapshot["events"][0]["event_id"] == "evt-001"
    assert snapshot["events"][0]["status"] == "applied"


def test_admin_onboarding_event_is_idempotent(client: TestClient) -> None:
    payload = _sample_onboarding_event("evt-002")
    first = client.post("/api/v1/admin/onboarding/events", json=payload)
    assert first.status_code == 200
    assert first.json()["status"] == "applied"

    duplicate = client.post("/api/v1/admin/onboarding/events", json=payload)
    assert duplicate.status_code == 200
    duplicate_payload = duplicate.json()
    assert duplicate_payload["status"] == "duplicate"
    assert duplicate_payload["target_id"] == "mappo-ma-target-live-01"


def test_admin_onboarding_event_rejected_for_invalid_container_id(client: TestClient) -> None:
    payload = _sample_onboarding_event("evt-003")
    payload["container_app_resource_id"] = (
        "/subscriptions/sub-other/resourceGroups/rg-mappo-ma-mrg-live-01/providers/"
        "Microsoft.App/containerApps/ca-mappo-ma-target-live-01"
    )

    response = client.post("/api/v1/admin/onboarding/events", json=payload)
    assert response.status_code == 200
    assert response.json()["status"] == "rejected"


def test_admin_onboarding_ingest_token_enforced(
    client: TestClient,
    monkeypatch: MonkeyPatch,
) -> None:
    monkeypatch.setenv("MAPPO_MARKETPLACE_INGEST_TOKEN", "expected-token")
    get_settings.cache_clear()
    try:
        without_token = client.post(
            "/api/v1/admin/onboarding/events",
            json=_sample_onboarding_event("evt-004"),
        )
        assert without_token.status_code == 401

        with_token = client.post(
            "/api/v1/admin/onboarding/events",
            json=_sample_onboarding_event("evt-004"),
            headers={"x-mappo-ingest-token": "expected-token"},
        )
        assert with_token.status_code == 200
        assert with_token.json()["status"] == "applied"
    finally:
        monkeypatch.delenv("MAPPO_MARKETPLACE_INGEST_TOKEN", raising=False)
        get_settings.cache_clear()
