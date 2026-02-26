from fastapi.testclient import TestClient


def test_lists_seeded_targets(client: TestClient) -> None:
    response = client.get("/api/v1/targets")

    assert response.status_code == 200
    payload = response.json()
    assert len(payload) == 10
    assert payload[0]["id"] == "target-01"


def test_filters_targets_by_ring(client: TestClient) -> None:
    response = client.get("/api/v1/targets", params={"ring": "canary"})

    assert response.status_code == 200
    payload = response.json()
    assert len(payload) == 2
    assert {target["tags"]["ring"] for target in payload} == {"canary"}
