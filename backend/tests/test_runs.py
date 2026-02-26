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
