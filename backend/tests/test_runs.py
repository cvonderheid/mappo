import time
from typing import Any, cast

from fastapi.testclient import TestClient

TERMINAL_STATUSES = {"succeeded", "failed", "partial", "halted"}


def _wait_for_terminal(
    client: TestClient,
    run_id: str,
    timeout_seconds: float = 6.0,
) -> dict[str, Any]:
    deadline = time.monotonic() + timeout_seconds
    while time.monotonic() < deadline:
        response = client.get(f"/api/v1/runs/{run_id}")
        assert response.status_code == 200
        payload = cast(dict[str, Any], response.json())
        if payload["status"] in TERMINAL_STATUSES:
            return payload
        time.sleep(0.05)
    raise AssertionError(f"run did not become terminal within {timeout_seconds} seconds")


def test_create_run_and_complete_canary_wave(client: TestClient) -> None:
    response = client.post(
        "/api/v1/runs",
        json={
            "release_id": "rel-2026-02-25",
            "strategy_mode": "waves",
            "target_tags": {"ring": "canary"},
            "concurrency": 2,
        },
    )

    assert response.status_code == 201
    run_id = response.json()["id"]

    final_payload = _wait_for_terminal(client, run_id)
    assert final_payload["status"] == "succeeded"


def test_create_run_scopes_execution_to_specific_target_ids(client: TestClient) -> None:
    response = client.post(
        "/api/v1/runs",
        json={
            "release_id": "rel-2026-02-25",
            "target_ids": ["target-01", "target-03"],
            "concurrency": 2,
        },
    )

    assert response.status_code == 201
    payload = cast(dict[str, Any], response.json())
    target_ids = {record["target_id"] for record in payload["target_records"]}
    assert target_ids == {"target-01", "target-03"}


def test_successful_run_updates_fleet_target_release_version(client: TestClient) -> None:
    before_response = client.get("/api/v1/targets")
    assert before_response.status_code == 200
    before_payload = cast(list[dict[str, Any]], before_response.json())
    before_by_id = {target["id"]: target for target in before_payload}
    assert before_by_id["target-10"]["last_deployed_release"] == "2026.02.20.1"

    create_response = client.post(
        "/api/v1/runs",
        json={
            "release_id": "rel-2026-02-25",
            "target_ids": ["target-10"],
            "concurrency": 1,
        },
    )
    assert create_response.status_code == 201
    run_id = create_response.json()["id"]

    final_payload = _wait_for_terminal(client, run_id)
    assert final_payload["status"] == "succeeded"

    after_response = client.get("/api/v1/targets")
    assert after_response.status_code == 200
    after_payload = cast(list[dict[str, Any]], after_response.json())
    after_by_id = {target["id"]: target for target in after_payload}

    assert after_by_id["target-10"]["last_deployed_release"] == "2026.02.25.3"
    assert after_by_id["target-01"]["last_deployed_release"] == "2026.02.20.1"


def test_successful_run_updates_registered_target_health_to_healthy(client: TestClient) -> None:
    onboarding_response = client.post(
        "/api/v1/admin/onboarding/events",
        json={
            "event_id": "evt-health-001",
            "tenant_id": "tenant-live-a",
            "subscription_id": "sub-live-a",
            "container_app_resource_id": (
                "/subscriptions/sub-live-a/resourceGroups/rg-mappo-ma-mrg-live-01/providers/"
                "Microsoft.App/containerApps/ca-mappo-ma-target-live-01"
            ),
            "target_id": "mappo-ma-target-live-01",
            "target_group": "canary",
            "region": "eastus",
            "environment": "prod",
            "tier": "gold",
            "health_status": "registered",
        },
    )
    assert onboarding_response.status_code == 200
    assert onboarding_response.json()["status"] == "applied"

    before_response = client.get("/api/v1/targets")
    assert before_response.status_code == 200
    before_payload = cast(list[dict[str, Any]], before_response.json())
    before_by_id = {target["id"]: target for target in before_payload}
    assert before_by_id["mappo-ma-target-live-01"]["health_status"] == "registered"

    create_response = client.post(
        "/api/v1/runs",
        json={
            "release_id": "rel-2026-02-25",
            "target_ids": ["mappo-ma-target-live-01"],
            "concurrency": 1,
        },
    )
    assert create_response.status_code == 201
    run_id = create_response.json()["id"]

    final_payload = _wait_for_terminal(client, run_id)
    assert final_payload["status"] == "succeeded"

    after_response = client.get("/api/v1/targets")
    assert after_response.status_code == 200
    after_payload = cast(list[dict[str, Any]], after_response.json())
    after_by_id = {target["id"]: target for target in after_payload}
    assert after_by_id["mappo-ma-target-live-01"]["health_status"] == "healthy"


def test_retry_failed_target_succeeds_on_second_attempt(client: TestClient) -> None:
    create_response = client.post(
        "/api/v1/runs",
        json={
            "release_id": "rel-2026-02-25",
            "target_ids": ["target-07"],
            "concurrency": 1,
        },
    )
    assert create_response.status_code == 201
    run_id = create_response.json()["id"]

    first_result = _wait_for_terminal(client, run_id)
    assert first_result["status"] == "failed"

    retry_response = client.post(f"/api/v1/runs/{run_id}/retry-failed")
    assert retry_response.status_code == 200

    second_result = _wait_for_terminal(client, run_id)
    assert second_result["status"] == "succeeded"


def test_resume_after_retry_failed_completes_queued_targets(client: TestClient) -> None:
    create_response = client.post(
        "/api/v1/runs",
        json={
            "release_id": "rel-2026-02-25",
            "strategy_mode": "all_at_once",
            "target_ids": ["target-07", "target-09", "target-10"],
            "concurrency": 1,
            "stop_policy": {"max_failure_count": 2},
        },
    )
    assert create_response.status_code == 201
    run_id = create_response.json()["id"]

    first_result = _wait_for_terminal(client, run_id)
    assert first_result["status"] == "halted"

    retry_response = client.post(f"/api/v1/runs/{run_id}/retry-failed")
    assert retry_response.status_code == 200

    deadline = time.monotonic() + 6.0
    while time.monotonic() < deadline:
        run_response = client.get(f"/api/v1/runs/{run_id}")
        assert run_response.status_code == 200
        payload = cast(dict[str, Any], run_response.json())
        stages = {record["status"] for record in payload["target_records"]}
        if stages <= {"SUCCEEDED", "QUEUED"}:
            break
        time.sleep(0.05)
    else:
        raise AssertionError("retry-failed execution did not settle to succeeded+queued state")

    resume_response = client.post(f"/api/v1/runs/{run_id}/resume")
    assert resume_response.status_code == 200

    final_payload = _wait_for_terminal(client, run_id)
    assert final_payload["status"] == "succeeded"


def test_resume_rejected_for_completed_run(client: TestClient) -> None:
    create_response = client.post(
        "/api/v1/runs",
        json={
            "release_id": "rel-2026-02-25",
            "target_ids": ["target-01"],
            "concurrency": 1,
        },
    )
    assert create_response.status_code == 201
    run_id = create_response.json()["id"]

    final_payload = _wait_for_terminal(client, run_id)
    assert final_payload["status"] == "succeeded"

    resume_response = client.post(f"/api/v1/runs/{run_id}/resume")
    assert resume_response.status_code == 400
    assert resume_response.json()["detail"] == "run is not resumable"
