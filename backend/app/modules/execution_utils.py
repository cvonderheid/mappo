from __future__ import annotations

import hashlib
import json
import random
import re
from typing import Any

from app.modules.schemas import TargetStage

TENANT_GUID_PATTERN = re.compile(
    r"^[0-9a-fA-F]{8}-"
    r"[0-9a-fA-F]{4}-"
    r"[0-9a-fA-F]{4}-"
    r"[0-9a-fA-F]{4}-"
    r"[0-9a-fA-F]{12}$"
)


def is_guid_tenant_id(value: str | None) -> bool:
    if value is None:
        return False
    return TENANT_GUID_PATTERN.fullmatch(value.strip()) is not None


def normalize_tenant_hint(value: str | None) -> str | None:
    if value is None:
        return None
    normalized = value.strip()
    if normalized == "":
        return None
    lowered = normalized.lower()
    if lowered in {"unknown-tenant", "unknown"}:
        return None
    if re.fullmatch(r"tenant-\d+", lowered):
        return None
    return normalized


def resolve_tenant_for_subscription(
    *,
    subscription_id: str,
    target_tenant_hint: str | None,
    default_tenant_id: str | None,
    tenant_by_subscription: dict[str, str],
) -> str | None:
    mapped = normalize_tenant_hint(tenant_by_subscription.get(subscription_id))
    if mapped:
        return mapped

    normalized_target = normalize_tenant_hint(target_tenant_hint)
    if normalized_target and (
        is_guid_tenant_id(normalized_target)
        or "." in normalized_target
    ):
        return normalized_target

    return normalize_tenant_hint(default_tenant_id)


def dedupe_strings_in_order(values: list[str]) -> list[str]:
    seen: set[str] = set()
    ordered: list[str] = []
    for item in values:
        if item in seen:
            continue
        seen.add(item)
        ordered.append(item)
    return ordered


def normalize_location(value: str | None) -> str | None:
    if value is None:
        return None
    normalized = value.strip().lower().replace(" ", "")
    if normalized == "":
        return None
    return normalized


def parse_usage_item(usage: Any) -> tuple[str, float, float] | None:
    name = getattr(usage, "name", None)
    usage_name = ""
    if name is not None:
        usage_name = (
            str(getattr(name, "value", "") or getattr(name, "localized_value", "")).strip()
        )
    if usage_name == "":
        usage_name = "unknown"

    current = getattr(usage, "current_value", None)
    if current is None:
        current = getattr(usage, "currentValue", None)
    limit = getattr(usage, "limit", None)
    if current is None or limit is None:
        return None

    try:
        current_value = float(current)
        limit_value = float(limit)
    except (TypeError, ValueError):
        return None

    return usage_name, current_value, limit_value


def is_retryable_exception(error: Exception) -> bool:
    from azure.core.exceptions import HttpResponseError, ServiceRequestError

    if isinstance(error, ServiceRequestError):
        return True
    if isinstance(error, HttpResponseError):
        status_code = getattr(error, "status_code", None)
        if isinstance(status_code, int):
            return status_code in {408, 409, 429, 500, 502, 503, 504}
    return False


def compute_retry_delay_seconds(
    *,
    error: Exception,
    attempt: int,
    base_delay_seconds: float,
    max_delay_seconds: float,
    jitter_seconds: float,
) -> float:
    retry_after = parse_retry_after_seconds(error)
    if retry_after is not None:
        base_delay = retry_after
    else:
        base_delay = min(
            max_delay_seconds,
            base_delay_seconds * (2 ** max(0, attempt - 1)),
        )
    jitter = random.uniform(0.0, jitter_seconds) if jitter_seconds > 0 else 0.0
    return min(max_delay_seconds, base_delay + jitter)


def parse_retry_after_seconds(error: Exception) -> float | None:
    response = getattr(error, "response", None)
    if response is None:
        return None

    headers = getattr(response, "headers", None)
    if headers is None:
        return None

    try:
        retry_after = headers.get("Retry-After")
    except Exception:
        return None
    if retry_after is None:
        return None
    try:
        parsed = float(str(retry_after).strip())
    except ValueError:
        return None
    if parsed < 0:
        return None
    return parsed


def stringify_exception(error: Exception) -> str:
    message = str(error).strip()
    if message:
        return message
    return error.__class__.__name__


def extract_http_response_error_details(error: Exception) -> dict[str, Any]:
    details: dict[str, Any] = {}
    response = getattr(error, "response", None)
    if response is not None:
        details.update(extract_azure_response_headers(getattr(response, "headers", None)))
        response_text = read_response_body_text(response)
        response_json = parse_json_payload(response_text)
        if response_json is not None:
            details["azure_response_body"] = response_json
            details.update(extract_azure_error_from_payload(response_json))
        elif response_text:
            details["azure_response_text"] = truncate_text(response_text, 4096)

    for key, value in extract_azure_error_from_model(getattr(error, "error", None)).items():
        details.setdefault(key, value)
    for key, value in extract_azure_error_from_text(stringify_exception(error)).items():
        details.setdefault(key, value)
    return details


def extract_azure_response_headers(headers: Any) -> dict[str, str]:
    if headers is None:
        return {}
    extracted: dict[str, str] = {}
    header_map = {
        "x-ms-request-id": "azure_request_id",
        "x-ms-correlation-request-id": "azure_correlation_id",
        "x-ms-routing-request-id": "azure_routing_request_id",
        "x-ms-arm-service-request-id": "azure_arm_service_request_id",
        "x-ms-client-request-id": "azure_client_request_id",
        "x-ms-operation-id": "azure_operation_id",
        "azure-asyncoperation": "azure_async_operation_url",
    }
    for source_header, target_key in header_map.items():
        header_value = read_header_value(headers, source_header)
        if header_value:
            extracted[target_key] = header_value
    return extracted


def read_header_value(headers: Any, key: str) -> str | None:
    key_lower = key.lower()
    try:
        items = headers.items()
    except Exception:
        items = []
    for header_key, header_value in items:
        if str(header_key).lower() != key_lower:
            continue
        normalized = to_non_empty_string(header_value)
        if normalized:
            return normalized
    try:
        raw = headers.get(key)
    except Exception:
        raw = None
    return to_non_empty_string(raw)


def read_response_body_text(response: Any) -> str | None:
    text_attr = getattr(response, "text", None)
    if isinstance(text_attr, str):
        return text_attr.strip() or None
    if callable(text_attr):
        try:
            text_value = text_attr()
        except TypeError:
            text_value = text_attr(encoding="utf-8")
        except Exception:
            text_value = None
        normalized = to_non_empty_string(text_value)
        if normalized:
            return normalized

    content_attr = getattr(response, "content", None)
    if isinstance(content_attr, bytes):
        try:
            decoded = content_attr.decode("utf-8", errors="replace")
        except Exception:
            decoded = ""
        normalized = decoded.strip()
        return normalized or None
    if isinstance(content_attr, str):
        return content_attr.strip() or None
    return None


def parse_json_payload(raw: str | None) -> Any | None:
    if raw is None:
        return None
    normalized = raw.strip()
    if normalized == "":
        return None
    if not normalized.startswith("{") and not normalized.startswith("["):
        return None
    try:
        return json.loads(normalized)
    except json.JSONDecodeError:
        return None


def extract_azure_error_from_payload(payload: Any) -> dict[str, Any]:
    root = payload
    if isinstance(payload, dict) and isinstance(payload.get("error"), dict):
        root = payload["error"]
    if not isinstance(root, dict):
        return {}

    extracted: dict[str, Any] = {}
    error_code = to_non_empty_string(root.get("code"))
    error_message = to_non_empty_string(root.get("message"))
    if error_code:
        extracted["azure_error_code"] = error_code
    if error_message:
        extracted["azure_error_message"] = error_message

    details_value = root.get("details")
    detail_entries = normalize_azure_error_detail_entries(details_value)
    if detail_entries:
        extracted["azure_error_details"] = detail_entries

    inner_error = root.get("innererror") or root.get("innerError")
    inner_entry = normalize_azure_error_detail_entry(inner_error)
    if inner_entry:
        extracted["azure_inner_error"] = inner_entry
    return extracted


def extract_azure_error_from_model(error_model: Any) -> dict[str, Any]:
    if error_model is None:
        return {}
    extracted: dict[str, Any] = {}
    error_code = to_non_empty_string(getattr(error_model, "code", None))
    error_message = to_non_empty_string(getattr(error_model, "message", None))
    if error_code:
        extracted["azure_error_code"] = error_code
    if error_message:
        extracted["azure_error_message"] = error_message

    detail_entries = normalize_azure_error_detail_entries(
        getattr(error_model, "details", None)
    )
    if detail_entries:
        extracted["azure_error_details"] = detail_entries
    return extracted


def normalize_azure_error_detail_entries(value: Any) -> list[dict[str, str]] | None:
    if not isinstance(value, list):
        return None
    entries: list[dict[str, str]] = []
    for item in value:
        normalized = normalize_azure_error_detail_entry(item)
        if normalized is not None:
            entries.append(normalized)
    if not entries:
        return None
    return entries


def normalize_azure_error_detail_entry(value: Any) -> dict[str, str] | None:
    if value is None:
        return None
    if isinstance(value, dict):
        source = value
    else:
        source = {
            "code": getattr(value, "code", None),
            "message": getattr(value, "message", None),
            "target": getattr(value, "target", None),
        }
    entry: dict[str, str] = {}
    for key in ("code", "message", "target"):
        normalized = to_non_empty_string(source.get(key))
        if normalized:
            entry[key] = normalized
    if not entry:
        return None
    return entry


def extract_azure_error_from_text(text: str) -> dict[str, str]:
    if text.strip() == "":
        return {}
    code_match = re.search(r"\bCode[:=]\s*\"?([A-Za-z0-9_.-]+)\"?", text, re.IGNORECASE)
    message_match = re.search(
        r"\bMessage[:=]\s*\"?([^\n\"]+)",
        text,
        re.IGNORECASE,
    )
    extracted: dict[str, str] = {}
    if code_match:
        extracted["azure_error_code"] = code_match.group(1).strip()
    if message_match:
        extracted["azure_error_message"] = message_match.group(1).strip()
    return extracted


def format_azure_error_summary(
    *,
    azure_error_code: str | None,
    azure_error_message: str | None,
) -> str | None:
    if azure_error_code and azure_error_message:
        return (
            f"(Azure {azure_error_code}: "
            f"{truncate_text(azure_error_message, 240)})"
        )
    if azure_error_message:
        return f"(Azure: {truncate_text(azure_error_message, 240)})"
    if azure_error_code:
        return f"(Azure code: {azure_error_code})"
    return None


def to_non_empty_string(value: Any) -> str | None:
    if value is None:
        return None
    normalized = str(value).strip()
    if normalized == "":
        return None
    return normalized


def truncate_text(value: str, max_length: int) -> str:
    if len(value) <= max_length:
        return value
    if max_length <= 3:
        return value[:max_length]
    return f"{value[: max_length - 3]}..."


def resolve_desired_image(
    *,
    current_image: str,
    parameter_defaults: dict[str, str],
) -> str:
    explicit_keys = ("containerImage", "image", "image_uri")
    for key in explicit_keys:
        value = parameter_defaults.get(key)
        if isinstance(value, str) and value.strip():
            return value.strip()

    tag_keys = ("imageTag", "image_tag")
    for key in tag_keys:
        value = parameter_defaults.get(key)
        if isinstance(value, str) and value.strip():
            return replace_image_tag(current_image=current_image, image_tag=value.strip())

    return current_image


def resolve_desired_feature_flag(parameter_defaults: dict[str, str]) -> str | None:
    for key in ("featureFlag", "feature_flag"):
        value = parameter_defaults.get(key)
        if isinstance(value, str) and value.strip():
            return value.strip()
    return None


def replace_image_tag(*, current_image: str, image_tag: str) -> str:
    if current_image.strip() == "":
        return image_tag

    without_digest = current_image.split("@", 1)[0]
    slash_index = without_digest.rfind("/")
    colon_index = without_digest.rfind(":")
    if colon_index > slash_index:
        repository = without_digest[:colon_index]
    else:
        repository = without_digest
    return f"{repository}:{image_tag}"


def read_container_env_value(*, app: Any, env_name: str) -> str | None:
    template = getattr(app, "template", None)
    containers = list(getattr(template, "containers", []) or [])
    if not containers:
        return None
    env_items = list(getattr(containers[0], "env", []) or [])
    for env_item in env_items:
        if isinstance(env_item, dict):
            if str(env_item.get("name", "")) == env_name:
                raw_value = env_item.get("value")
                if raw_value is None:
                    return None
                return str(raw_value)
            continue
        name = str(getattr(env_item, "name", ""))
        if name != env_name:
            continue
        raw_value = getattr(env_item, "value", None)
        if raw_value is None:
            return None
        return str(raw_value)
    return None


def set_container_env_value(
    *,
    container: Any,
    env_name: str,
    env_value: str,
) -> None:
    env_items = list(getattr(container, "env", []) or [])
    for env_item in env_items:
        if isinstance(env_item, dict):
            if str(env_item.get("name", "")) == env_name:
                env_item["value"] = env_value
                container.env = env_items
                return
            continue
        name = str(getattr(env_item, "name", ""))
        if name == env_name:
            env_item.value = env_value
            container.env = env_items
            return
    env_items.append({"name": env_name, "value": env_value})
    container.env = env_items


def build_correlation_id(
    run_id: str,
    target_id: str,
    attempt: int,
    stage: TargetStage,
) -> str:
    key = f"{run_id}:{target_id}:{attempt}:{stage.value}"
    digest = hashlib.sha256(key.encode()).hexdigest()
    return f"corr-{digest[:16]}"
