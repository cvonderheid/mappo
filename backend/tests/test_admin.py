from __future__ import annotations

from fastapi.testclient import TestClient
from pytest import MonkeyPatch

from app.core.settings import get_settings

TENANT_LIVE_A = "11111111-1111-1111-1111-111111111111"
SUBSCRIPTION_LIVE_A = "22222222-2222-2222-2222-222222222222"


def _sample_onboarding_event(event_id: str) -> dict[str, object]:
    return {
        "event_id": event_id,
        "event_type": "subscription_purchased",
        "tenant_id": TENANT_LIVE_A,
        "subscription_id": SUBSCRIPTION_LIVE_A,
        "managed_application_id": (
            f"/subscriptions/{SUBSCRIPTION_LIVE_A}/resourceGroups/rg-mappo-ma-apps-live/providers/"
            "Microsoft.Solutions/applications/mappo-ma-target-live-01"
        ),
        "managed_resource_group_id": (
            f"/subscriptions/{SUBSCRIPTION_LIVE_A}/resourceGroups/rg-mappo-ma-mrg-live-01"
        ),
        "container_app_resource_id": (
            f"/subscriptions/{SUBSCRIPTION_LIVE_A}/resourceGroups/rg-mappo-ma-mrg-live-01/providers/"
            "Microsoft.App/containerApps/ca-mappo-ma-target-live-01"
        ),
        "customer_name": "Contoso",
        "target_group": "canary",
        "region": "eastus",
        "environment": "prod",
        "tier": "gold",
        "metadata": {"source": "partner-center"},
    }


def _sample_forwarder_log(log_id: str) -> dict[str, object]:
    return {
        "log_id": log_id,
        "level": "error",
        "message": "MAPPO backend rejected forwarded marketplace event.",
        "event_id": "evt-001",
        "event_type": "subscription_purchased",
        "target_id": "mappo-ma-target-live-01",
        "tenant_id": TENANT_LIVE_A,
        "subscription_id": SUBSCRIPTION_LIVE_A,
        "function_app_name": "fa-mappo-forwarder-demo",
        "forwarder_request_id": "request-abc-123",
        "backend_status_code": 400,
        "details": {"backend_response": '{"detail":"invalid payload"}'},
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
    assert snapshot["forwarder_logs"] == []


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


def test_admin_registration_can_be_updated(client: TestClient) -> None:
    create_response = client.post(
        "/api/v1/admin/onboarding/events",
        json=_sample_onboarding_event("evt-005"),
    )
    assert create_response.status_code == 200

    update_response = client.patch(
        "/api/v1/admin/onboarding/registrations/mappo-ma-target-live-01",
        json={
            "display_name": "Contoso Production",
            "customer_name": "Contoso Ltd",
            "target_group": "prod",
            "region": "centralus",
            "environment": "staging",
            "tier": "platinum",
            "health_status": "healthy",
        },
    )
    assert update_response.status_code == 200
    payload = update_response.json()
    assert payload["display_name"] == "Contoso Production"
    assert payload["customer_name"] == "Contoso Ltd"
    assert payload["tags"]["ring"] == "prod"
    assert payload["tags"]["region"] == "centralus"
    assert payload["tags"]["environment"] == "staging"
    assert payload["tags"]["tier"] == "platinum"

    targets_response = client.get("/api/v1/targets")
    assert targets_response.status_code == 200
    target = next(
        item
        for item in targets_response.json()
        if item["id"] == "mappo-ma-target-live-01"
    )
    assert target["health_status"] == "healthy"
    assert target["customer_name"] == "Contoso Ltd"
    assert target["tags"]["ring"] == "prod"
    assert target["tags"]["region"] == "centralus"


def test_admin_registration_can_be_deleted(client: TestClient) -> None:
    create_response = client.post(
        "/api/v1/admin/onboarding/events",
        json=_sample_onboarding_event("evt-006"),
    )
    assert create_response.status_code == 200

    delete_response = client.delete(
        "/api/v1/admin/onboarding/registrations/mappo-ma-target-live-01"
    )
    assert delete_response.status_code == 200
    assert delete_response.json() == {
        "target_id": "mappo-ma-target-live-01",
        "deleted": True,
    }

    targets_response = client.get("/api/v1/targets")
    assert targets_response.status_code == 200
    target_ids = {item["id"] for item in targets_response.json()}
    assert "mappo-ma-target-live-01" not in target_ids

    snapshot_response = client.get("/api/v1/admin/onboarding")
    assert snapshot_response.status_code == 200
    snapshot = snapshot_response.json()
    registration_ids = {item["target_id"] for item in snapshot["registrations"]}
    assert "mappo-ma-target-live-01" not in registration_ids


def test_admin_forwarder_log_ingest_and_snapshot(client: TestClient) -> None:
    create_response = client.post(
        "/api/v1/admin/onboarding/forwarder-logs",
        json=_sample_forwarder_log("fwd-001"),
    )
    assert create_response.status_code == 200
    assert create_response.json() == {
        "log_id": "fwd-001",
        "status": "applied",
        "message": "Forwarder log recorded.",
    }

    snapshot_response = client.get("/api/v1/admin/onboarding")
    assert snapshot_response.status_code == 200
    snapshot = snapshot_response.json()
    assert snapshot["forwarder_logs"][0]["log_id"] == "fwd-001"
    assert snapshot["forwarder_logs"][0]["level"] == "error"
    assert snapshot["forwarder_logs"][0]["backend_status_code"] == 400

    list_response = client.get("/api/v1/admin/onboarding/forwarder-logs")
    assert list_response.status_code == 200
    payload = list_response.json()
    assert payload[0]["log_id"] == "fwd-001"


def test_admin_forwarder_log_is_idempotent(client: TestClient) -> None:
    payload = _sample_forwarder_log("fwd-002")
    first = client.post("/api/v1/admin/onboarding/forwarder-logs", json=payload)
    assert first.status_code == 200
    assert first.json()["status"] == "applied"

    duplicate = client.post("/api/v1/admin/onboarding/forwarder-logs", json=payload)
    assert duplicate.status_code == 200
    assert duplicate.json()["status"] == "duplicate"


def test_admin_forwarder_log_ingest_token_enforced(
    client: TestClient,
    monkeypatch: MonkeyPatch,
) -> None:
    monkeypatch.setenv("MAPPO_MARKETPLACE_INGEST_TOKEN", "expected-token")
    get_settings.cache_clear()
    try:
        without_token = client.post(
            "/api/v1/admin/onboarding/forwarder-logs",
            json=_sample_forwarder_log("fwd-003"),
        )
        assert without_token.status_code == 401

        with_token = client.post(
            "/api/v1/admin/onboarding/forwarder-logs",
            json=_sample_forwarder_log("fwd-003"),
            headers={"x-mappo-ingest-token": "expected-token"},
        )
        assert with_token.status_code == 200
        assert with_token.json()["status"] == "applied"
    finally:
        monkeypatch.delenv("MAPPO_MARKETPLACE_INGEST_TOKEN", raising=False)
        get_settings.cache_clear()
