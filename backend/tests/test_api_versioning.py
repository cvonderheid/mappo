from fastapi.testclient import TestClient


def test_docs_and_openapi_are_versioned(client: TestClient) -> None:
    docs_response = client.get("/api/v1/docs")
    openapi_response = client.get("/api/v1/openapi.json")

    assert docs_response.status_code == 200
    assert openapi_response.status_code == 200
    assert openapi_response.json()["openapi"]
