from __future__ import annotations

import copy
import urllib.error
import urllib.request
from collections.abc import Callable
from typing import Any

from app.modules.execution_utils import (
    extract_http_response_error_details,
    format_azure_error_summary,
    set_container_env_value,
    to_non_empty_string,
)


def translate_azure_exception(
    *,
    error: Exception,
    code: str,
    message: str,
    details: dict[str, Any] | None = None,
    error_factory: Callable[[str, str, dict[str, Any] | None], Exception],
) -> Exception:
    from azure.core.exceptions import (
        ClientAuthenticationError,
        HttpResponseError,
        ResourceNotFoundError,
        ServiceRequestError,
    )

    if isinstance(error, ClientAuthenticationError):
        return error_factory(
            "azure_authentication_failed",
            "Azure authentication failed for the configured service principal.",
            {**(details or {}), "error": str(error)},
        )
    if isinstance(error, ResourceNotFoundError):
        return error_factory(
            "azure_target_not_found",
            "Azure target Container App was not found.",
            {**(details or {}), "error": str(error)},
        )
    if isinstance(error, ServiceRequestError):
        return error_factory(
            code,
            "Azure API request failed due to transport/network issues.",
            {**(details or {}), "error": str(error)},
        )
    if isinstance(error, HttpResponseError):
        http_error_details = extract_http_response_error_details(error)
        azure_summary = format_azure_error_summary(
            azure_error_code=to_non_empty_string(http_error_details.get("azure_error_code")),
            azure_error_message=to_non_empty_string(http_error_details.get("azure_error_message")),
        )
        translated_message = message
        if azure_summary and azure_summary not in translated_message:
            translated_message = f"{translated_message} {azure_summary}"
        return error_factory(
            code,
            translated_message,
            {
                **(details or {}),
                "error": str(error),
                **http_error_details,
                "status_code": getattr(error, "status_code", None),
            },
        )
    return error_factory(
        code,
        message,
        {**(details or {}), "error": str(error)},
    )


def parse_container_app_resource_id_parts(managed_app_id: str) -> tuple[str, str, str]:
    parts = [part for part in managed_app_id.strip("/").split("/") if part]
    if len(parts) != 8:
        raise ValueError("Managed app resource ID is not a valid Container App resource path.")

    if parts[0].lower() != "subscriptions" or parts[2].lower() != "resourcegroups":
        raise ValueError(
            "Managed app resource ID is missing subscription/resource-group segments."
        )
    if parts[4].lower() != "providers" or parts[5].lower() != "microsoft.app":
        raise ValueError("Managed app resource ID provider must be Microsoft.App.")
    if parts[6].lower() != "containerapps":
        raise ValueError("Managed app resource ID type must be containerApps.")

    return parts[1], parts[3], parts[7]


def build_health_url(
    *,
    latest_revision_fqdn: str | None,
    parameter_defaults: dict[str, str],
    resource_group_name: str,
    container_app_name: str,
) -> str:
    health_path = (
        parameter_defaults.get("healthUrl")
        or parameter_defaults.get("health_url")
        or parameter_defaults.get("healthPath")
        or parameter_defaults.get("health_path")
        or "/"
    )
    normalized = health_path.strip()
    if normalized.startswith("http://") or normalized.startswith("https://"):
        return normalized

    fqdn = (latest_revision_fqdn or "").strip()
    if not fqdn:
        raise ValueError(
            "Container App revision FQDN is unavailable for health verification: "
            f"{resource_group_name}/{container_app_name}"
        )
    path = normalized if normalized.startswith("/") else f"/{normalized}"
    return f"https://{fqdn}{path}"


def probe_health_url(
    *,
    health_url: str,
    timeout_seconds: float,
    error_factory: Callable[[str, str, dict[str, Any] | None], Exception],
) -> int:
    request = urllib.request.Request(
        url=health_url,
        method="GET",
        headers={"User-Agent": "mappo-health-check/1.0"},
    )
    try:
        with urllib.request.urlopen(request, timeout=timeout_seconds) as response:
            status_code = int(getattr(response, "status", response.getcode()))
    except urllib.error.HTTPError as error:
        raise error_factory(
            "azure_health_check_failed",
            f"Health check failed with HTTP {error.code}.",
            {"health_url": health_url, "status_code": error.code},
        ) from error
    except urllib.error.URLError as error:
        raise error_factory(
            "azure_health_check_failed",
            "Health check request failed to reach the target.",
            {"health_url": health_url, "reason": str(error.reason)},
        ) from error

    if status_code < 200 or status_code >= 400:
        raise error_factory(
            "azure_health_check_failed",
            f"Health check returned unexpected status {status_code}.",
            {"health_url": health_url, "status_code": status_code},
        )
    return status_code


def build_container_app_update_payload(
    *,
    app: Any,
    desired_image: str,
    desired_feature_flag: str | None,
    container_app_model_type: type[Any],
    error_factory: Callable[[str, str, dict[str, Any] | None], Exception],
) -> Any:
    location = getattr(app, "location", None)
    if not isinstance(location, str) or location.strip() == "":
        raise error_factory(
            "azure_update_payload_invalid",
            "Container App update payload is missing a valid location.",
            None,
        )

    template = copy.deepcopy(getattr(app, "template", None))
    containers = list(getattr(template, "containers", []) or [])
    if not containers:
        raise error_factory(
            "azure_update_payload_invalid",
            "Container App update payload has no containers.",
            None,
        )
    containers[0].image = desired_image
    if desired_feature_flag is not None:
        set_container_env_value(
            container=containers[0],
            env_name="MAPPO_FEATURE_FLAG",
            env_value=desired_feature_flag,
        )

    return container_app_model_type(
        location=location,
        tags=getattr(app, "tags", None),
        extended_location=getattr(app, "extended_location", None),
        identity=getattr(app, "identity", None),
        managed_by=getattr(app, "managed_by", None),
        kind=getattr(app, "kind", None),
        managed_environment_id=getattr(app, "managed_environment_id", None),
        environment_id=getattr(app, "environment_id", None),
        workload_profile_name=getattr(app, "workload_profile_name", None),
        configuration=getattr(app, "configuration", None),
        template=template,
    )
