import json
import logging
import os
import time
import urllib.error
import urllib.request
from uuid import uuid4
from typing import Any

import azure.functions as func

app = func.FunctionApp(http_auth_level=func.AuthLevel.FUNCTION)

logger = logging.getLogger("mappo.marketplace_forwarder")


def _string_value(value: Any, fallback: str = "") -> str:
    if isinstance(value, str):
        normalized = value.strip()
        if normalized != "":
            return normalized
    return fallback


def _dict_value(value: Any) -> dict[str, Any]:
    if isinstance(value, dict):
        return value
    return {}


def _build_normalized_event(payload: dict[str, Any]) -> dict[str, Any]:
    # Shape 1: payload is already MAPPO onboarding format.
    onboarding_keys = {
        "event_id",
        "tenant_id",
        "subscription_id",
        "container_app_resource_id",
    }
    if onboarding_keys.issubset(payload.keys()):
        return payload

    # Shape 2: marketplace payload plus explicit MAPPO target context.
    target = _dict_value(payload.get("mappo_target"))
    if target == {}:
        target = _dict_value(payload.get("target"))

    event_id = _string_value(payload.get("event_id"))
    if event_id == "":
        event_id = _string_value(payload.get("id"))
    if event_id == "":
        event_id = f"evt-marketplace-{int(time.time() * 1000)}"

    event_type = _string_value(payload.get("event_type"), "subscription_purchased")
    if event_type == "subscription_purchased":
        event_type = _string_value(payload.get("action"), "subscription_purchased")

    tenant_id = _string_value(target.get("tenant_id"))
    subscription_id = _string_value(target.get("subscription_id"))
    container_app_resource_id = _string_value(target.get("container_app_resource_id"))

    if tenant_id == "" or subscription_id == "" or container_app_resource_id == "":
        missing = []
        if tenant_id == "":
            missing.append("tenant_id")
        if subscription_id == "":
            missing.append("subscription_id")
        if container_app_resource_id == "":
            missing.append("container_app_resource_id")
        raise ValueError(
            "Payload cannot be normalized. Provide onboarding shape directly or include "
            + f"`mappo_target` with required fields: {', '.join(missing)}."
        )

    metadata = _dict_value(target.get("metadata"))
    metadata.update(
        {
            "source": "function-marketplace-forwarder",
            "marketplace_event_type": event_type,
            "marketplace_payload_id": _string_value(payload.get("id")),
        }
    )

    normalized = {
        "event_id": event_id,
        "event_type": event_type,
        "tenant_id": tenant_id,
        "subscription_id": subscription_id,
        "container_app_resource_id": container_app_resource_id,
        "managed_application_id": _string_value(target.get("managed_application_id")) or None,
        "managed_resource_group_id": _string_value(target.get("managed_resource_group_id")) or None,
        "container_app_name": _string_value(target.get("container_app_name")) or None,
        "target_group": _string_value(target.get("target_group"), "prod"),
        "region": _string_value(target.get("region"), "eastus"),
        "environment": _string_value(target.get("environment"), "prod"),
        "tier": _string_value(target.get("tier"), "standard"),
        "customer_name": _string_value(target.get("customer_name")) or None,
        "display_name": _string_value(target.get("display_name")) or None,
        "tags": _dict_value(target.get("tags")),
        "metadata": metadata,
        "health_status": "registered",
        "last_deployed_release": "unknown",
    }
    return normalized


def _ingest_endpoint() -> str:
    endpoint = _string_value(os.getenv("MAPPO_INGEST_ENDPOINT"))
    if endpoint != "":
        return endpoint

    base_url = _string_value(os.getenv("MAPPO_API_BASE_URL"))
    if base_url == "":
        raise RuntimeError("Missing MAPPO_INGEST_ENDPOINT or MAPPO_API_BASE_URL app setting.")
    return f"{base_url.rstrip('/')}/api/v1/admin/onboarding/events"


def _forwarder_logs_endpoint() -> str:
    endpoint = _string_value(os.getenv("MAPPO_FORWARDER_LOGS_ENDPOINT"))
    if endpoint != "":
        return endpoint

    base_url = _string_value(os.getenv("MAPPO_API_BASE_URL"))
    if base_url == "":
        return ""
    return f"{base_url.rstrip('/')}/api/v1/admin/onboarding/forwarder-logs"


def _ingest_headers() -> dict[str, str]:
    ingest_token = _string_value(os.getenv("MAPPO_INGEST_TOKEN"))
    headers = {
        "Content-Type": "application/json",
        "User-Agent": "mappo-marketplace-forwarder/1.0",
    }
    if ingest_token != "":
        headers["x-mappo-ingest-token"] = ingest_token
    return headers


def _post_json(endpoint: str, payload: dict[str, Any], *, timeout_seconds: float) -> tuple[int, str]:
    headers = _ingest_headers()
    request_body = json.dumps(payload).encode("utf-8")
    request = urllib.request.Request(endpoint, data=request_body, headers=headers, method="POST")
    try:
        with urllib.request.urlopen(request, timeout=timeout_seconds) as response:
            response_body = response.read().decode("utf-8")
            return response.status, response_body
    except urllib.error.HTTPError as error:
        response_body = error.read().decode("utf-8", errors="replace")
        return error.code, response_body


def _forward_to_mappo(event_payload: dict[str, Any]) -> tuple[int, str]:
    endpoint = _ingest_endpoint()
    timeout_seconds = float(_string_value(os.getenv("MAPPO_INGEST_TIMEOUT_SECONDS"), "15"))
    return _post_json(endpoint, event_payload, timeout_seconds=timeout_seconds)


def _truncate(value: str, max_length: int = 1600) -> str:
    if len(value) <= max_length:
        return value
    return value[: max_length - 3] + "..."


def _emit_forwarder_log(
    *,
    level: str,
    message: str,
    normalized_event: dict[str, Any] | None,
    backend_status_code: int | None = None,
    response_body: str | None = None,
    detail: str | None = None,
    request_id: str | None = None,
) -> None:
    endpoint = _forwarder_logs_endpoint()
    if endpoint == "":
        return

    timeout_seconds = float(_string_value(os.getenv("MAPPO_INGEST_TIMEOUT_SECONDS"), "15"))
    details: dict[str, Any] = {}
    if response_body:
        details["backend_response"] = _truncate(response_body)
    if detail:
        details["detail"] = _truncate(detail)

    payload = {
        "log_id": f"fwd-{int(time.time() * 1000)}-{uuid4().hex[:8]}",
        "level": level,
        "message": message,
        "event_id": _string_value((normalized_event or {}).get("event_id")) or None,
        "event_type": _string_value((normalized_event or {}).get("event_type")) or None,
        "target_id": _string_value((normalized_event or {}).get("target_id")) or None,
        "tenant_id": _string_value((normalized_event or {}).get("tenant_id")) or None,
        "subscription_id": _string_value((normalized_event or {}).get("subscription_id")) or None,
        "function_app_name": _string_value(os.getenv("WEBSITE_SITE_NAME")) or None,
        "forwarder_request_id": request_id,
        "backend_status_code": backend_status_code,
        "details": details,
    }
    try:
        status_code, response = _post_json(endpoint, payload, timeout_seconds=timeout_seconds)
        if status_code >= 400:
            logger.warning("Forwarder log ingestion failed: status=%s body=%s", status_code, response)
    except Exception:  # pragma: no cover - runtime safety
        logger.exception("Unable to emit forwarder log to MAPPO backend.")


@app.route(route="marketplace/events", methods=["POST"])
def marketplace_events(req: func.HttpRequest) -> func.HttpResponse:
    request_id = req.headers.get("x-ms-request-id") or req.headers.get("x-request-id")
    normalized_event: dict[str, Any] | None = None
    try:
        payload = req.get_json()
    except ValueError:
        _emit_forwarder_log(
            level="warning",
            message="Forwarder received invalid JSON payload.",
            normalized_event=None,
            detail="Invalid JSON payload.",
            request_id=request_id,
        )
        return func.HttpResponse(
            json.dumps({"detail": "Invalid JSON payload."}),
            status_code=400,
            mimetype="application/json",
        )

    if not isinstance(payload, dict):
        _emit_forwarder_log(
            level="warning",
            message="Forwarder received non-object JSON payload.",
            normalized_event=None,
            detail="Payload must be a JSON object.",
            request_id=request_id,
        )
        return func.HttpResponse(
            json.dumps({"detail": "Payload must be a JSON object."}),
            status_code=400,
            mimetype="application/json",
        )

    try:
        normalized_event = _build_normalized_event(payload)
    except ValueError as error:
        _emit_forwarder_log(
            level="warning",
            message="Forwarder could not normalize marketplace payload.",
            normalized_event=None,
            detail=str(error),
            request_id=request_id,
        )
        return func.HttpResponse(
            json.dumps({"detail": str(error)}),
            status_code=400,
            mimetype="application/json",
        )

    try:
        status_code, response_body = _forward_to_mappo(normalized_event)
    except Exception as error:  # pragma: no cover - runtime safety
        logger.exception("Failed to forward marketplace event to MAPPO backend.")
        _emit_forwarder_log(
            level="error",
            message="Forwarder request to MAPPO backend failed.",
            normalized_event=normalized_event,
            detail=str(error),
            request_id=request_id,
        )
        return func.HttpResponse(
            json.dumps({"detail": f"Forwarding failure: {error}"}),
            status_code=502,
            mimetype="application/json",
        )

    if status_code >= 400:
        _emit_forwarder_log(
            level="error",
            message="MAPPO backend rejected forwarded marketplace event.",
            normalized_event=normalized_event,
            backend_status_code=status_code,
            response_body=response_body,
            request_id=request_id,
        )

    return func.HttpResponse(
        response_body,
        status_code=status_code,
        mimetype="application/json",
    )
