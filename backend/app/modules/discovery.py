from __future__ import annotations

import json
import re
import urllib.error
import urllib.request
from dataclasses import dataclass
from datetime import UTC, datetime
from typing import Any

from app.modules.execution import AzureExecutorSettings
from app.modules.schemas import Target

DEFAULT_BASELINE_RELEASE = "2026.02.20.1"


class AzureDiscoveryError(Exception):
    def __init__(self, message: str):
        super().__init__(message)
        self.message = message


@dataclass(frozen=True)
class DiscoveryBlockedScope:
    scope_type: str
    scope_id: str
    reason: str


@dataclass(frozen=True)
class DiscoverImportResult:
    targets: list[Target]
    discovered_managed_apps: int
    skipped_managed_apps: int
    subscriptions_scanned: int
    scanned_subscription_ids: list[str]
    auto_discovered_subscription_ids: list[str]
    blocked_enumeration: list[DiscoveryBlockedScope]
    warnings: list[str]


def discover_targets_with_sdk(
    *,
    subscription_ids: list[str],
    auto_enumerate_subscriptions: bool,
    managed_app_name_prefix: str | None,
    preferred_container_app_name: str | None,
    default_target_group: str,
    group_tag_key: str,
    azure_settings: AzureExecutorSettings,
    existing_targets_by_id: dict[str, Target],
) -> DiscoverImportResult:
    normalized_subscriptions = normalize_subscription_ids(subscription_ids)
    if not normalized_subscriptions and not auto_enumerate_subscriptions:
        raise AzureDiscoveryError(
            "Provide subscription IDs or enable auto-enumeration of accessible subscriptions."
        )

    if not (
        azure_settings.tenant_id and azure_settings.client_id and azure_settings.client_secret
    ):
        raise AzureDiscoveryError(
            "Azure discovery requires MAPPO_AZURE_TENANT_ID, MAPPO_AZURE_CLIENT_ID, and "
            "MAPPO_AZURE_CLIENT_SECRET."
        )

    try:
        from azure.identity import ClientSecretCredential
        from azure.mgmt.resource import ResourceManagementClient
    except Exception as error:  # pragma: no cover - exercised by integration only
        raise AzureDiscoveryError(
            "Azure SDK dependencies are unavailable for discovery. Install azure-identity and "
            "azure-mgmt-resource."
        ) from error

    credential = ClientSecretCredential(
        tenant_id=azure_settings.tenant_id,
        client_id=azure_settings.client_id,
        client_secret=azure_settings.client_secret,
    )

    warnings: list[str] = []
    blocked_scopes: list[DiscoveryBlockedScope] = []
    auto_discovered_subscriptions: list[str] = []
    if auto_enumerate_subscriptions:
        (
            auto_discovered_subscriptions,
            subscription_warnings,
            subscription_blocks,
        ) = enumerate_accessible_subscriptions(
            credential=credential,
            tenant_id=azure_settings.tenant_id,
        )
        warnings.extend(subscription_warnings)
        blocked_scopes.extend(subscription_blocks)

    subscriptions_to_scan = merge_subscription_ids(
        normalized_subscriptions,
        auto_discovered_subscriptions,
    )
    if not subscriptions_to_scan:
        if blocked_scopes:
            raise AzureDiscoveryError(
                "No subscriptions available for discovery. "
                f"{summarize_blocked_scopes(blocked_scopes)}"
            )
        raise AzureDiscoveryError(
            "No subscriptions available for discovery. Provide subscription IDs or enable "
            "auto-enumeration."
        )

    existing_tenants_by_subscription: dict[str, str] = {}
    for target in existing_targets_by_id.values():
        if target.subscription_id not in existing_tenants_by_subscription:
            existing_tenants_by_subscription[target.subscription_id] = target.tenant_id

    now = datetime.now(tz=UTC)
    managed_prefix = (managed_app_name_prefix or "").strip().lower()
    preferred_name = (preferred_container_app_name or "").strip()
    target_group_fallback = default_target_group.strip() or "prod"
    group_tag = group_tag_key.strip() or "ring"

    targets: list[Target] = []
    seen_target_ids: set[str] = set()
    managed_app_count = 0
    skipped_count = 0

    for subscription_id in subscriptions_to_scan:
        resource_client = ResourceManagementClient(
            credential=credential,
            subscription_id=subscription_id,
        )
        try:
            managed_app_resources = list(
                resource_client.resources.list(
                    filter="resourceType eq 'Microsoft.Solutions/applications'"
                )
            )
        except Exception as error:
            reason = stringify_error(error)
            warnings.append(
                "Unable to list managed applications in subscription "
                f"{subscription_id}: {reason}"
            )
            blocked_scopes.append(
                DiscoveryBlockedScope(
                    scope_type="subscription",
                    scope_id=subscription_id,
                    reason=reason,
                )
            )
            continue

        for managed_app_resource in managed_app_resources:
            managed_app = as_mapping(managed_app_resource)
            managed_app_name = str(managed_app.get("name", "")).strip()
            if managed_app_name == "":
                skipped_count += 1
                warnings.append(
                    f"Skipping managed application with missing name in {subscription_id}."
                )
                continue
            if managed_prefix and not managed_app_name.lower().startswith(managed_prefix):
                continue

            managed_app_count += 1
            managed_app_tags = as_string_map(managed_app.get("tags"))
            managed_rg_id = str(managed_app.get("managedResourceGroupId", "")).strip()
            if managed_rg_id == "":
                properties = as_mapping(managed_app.get("properties"))
                managed_rg_id = str(properties.get("managedResourceGroupId", "")).strip()
            managed_rg_name = parse_resource_group_name(managed_rg_id)
            if not managed_rg_name:
                skipped_count += 1
                warnings.append(
                    f"Managed application {managed_app_name} has no managed resource group."
                )
                continue

            try:
                container_resources = list(
                    resource_client.resources.list_by_resource_group(
                        resource_group_name=managed_rg_name,
                        filter="resourceType eq 'Microsoft.App/containerApps'",
                    )
                )
            except Exception as error:
                reason = stringify_error(error)
                skipped_count += 1
                warnings.append(
                    "Unable to list Container Apps in resource group "
                    f"{managed_rg_name}: {reason}"
                )
                blocked_scopes.append(
                    DiscoveryBlockedScope(
                        scope_type="resource_group",
                        scope_id=f"{subscription_id}/{managed_rg_name}",
                        reason=reason,
                    )
                )
                continue

            container_apps = [as_mapping(resource) for resource in container_resources]
            if not container_apps:
                skipped_count += 1
                warnings.append(
                    "No Container Apps found in managed resource group "
                    f"{managed_rg_name}; skipping {managed_app_name}."
                )
                continue

            container_app, ambiguous_choice = pick_container_app(
                container_apps=container_apps,
                preferred_name=preferred_name,
                managed_app_name=managed_app_name,
            )
            if ambiguous_choice:
                chosen_name = str(container_app.get("name", "")).strip() or "<unknown>"
                warnings.append(
                    "Multiple Container Apps found in managed resource group "
                    f"{managed_rg_name}; selected {chosen_name}."
                )

            container_app_id = str(container_app.get("id", "")).strip()
            if container_app_id == "":
                skipped_count += 1
                warnings.append(
                    "Selected Container App in resource group "
                    f"{managed_rg_name} has no resource ID; skipping {managed_app_name}."
                )
                continue

            container_tags = as_string_map(container_app.get("tags"))
            target_group = resolve_target_group(
                group_tag_key=group_tag,
                default_target_group=target_group_fallback,
                managed_app_tags=managed_app_tags,
                container_app_tags=container_tags,
            )
            region = (
                str(container_app.get("location", "")).strip()
                or str(managed_app.get("location", "")).strip()
                or "unknown"
            )
            tier = container_tags.get("tier") or managed_app_tags.get("tier") or "standard"
            environment = (
                container_tags.get("environment")
                or managed_app_tags.get("environment")
                or "prod"
            )
            tenant_id = (
                existing_tenants_by_subscription.get(subscription_id)
                or str(managed_app.get("tenantId", "")).strip()
                or "unknown-tenant"
            )

            target_id_base = normalize_target_id(managed_app_name)
            target_id = make_unique_target_id(seen=seen_target_ids, base=target_id_base)
            existing = existing_targets_by_id.get(target_id)
            target = Target(
                id=target_id,
                tenant_id=tenant_id,
                subscription_id=subscription_id,
                managed_app_id=container_app_id,
                tags={
                    "ring": target_group,
                    "region": region,
                    "environment": environment,
                    "tier": tier,
                },
                last_deployed_release=(
                    existing.last_deployed_release
                    if existing is not None
                    else DEFAULT_BASELINE_RELEASE
                ),
                health_status=existing.health_status if existing is not None else "healthy",
                last_check_in_at=now,
                simulated_failure_mode=(
                    existing.simulated_failure_mode if existing is not None else "none"
                ),
            )
            targets.append(target)

    if not targets:
        message = (
            "No targets discovered from managed applications in the scanned subscriptions."
        )
        if blocked_scopes:
            message = f"{message} {summarize_blocked_scopes(blocked_scopes)}"
        raise AzureDiscoveryError(message)

    return DiscoverImportResult(
        targets=targets,
        discovered_managed_apps=managed_app_count,
        skipped_managed_apps=skipped_count,
        subscriptions_scanned=len(subscriptions_to_scan),
        scanned_subscription_ids=subscriptions_to_scan,
        auto_discovered_subscription_ids=auto_discovered_subscriptions,
        blocked_enumeration=blocked_scopes,
        warnings=warnings,
    )


def normalize_subscription_ids(subscription_ids: list[str]) -> list[str]:
    normalized: list[str] = []
    seen: set[str] = set()
    for value in subscription_ids:
        subscription_id = value.strip()
        if not subscription_id or subscription_id in seen:
            continue
        seen.add(subscription_id)
        normalized.append(subscription_id)
    return normalized


def merge_subscription_ids(
    explicit_subscription_ids: list[str],
    enumerated_subscription_ids: list[str],
) -> list[str]:
    merged: list[str] = []
    seen: set[str] = set()
    for subscription_id in explicit_subscription_ids + enumerated_subscription_ids:
        if subscription_id in seen:
            continue
        seen.add(subscription_id)
        merged.append(subscription_id)
    return merged


def enumerate_accessible_subscriptions(
    *,
    credential: Any,
    tenant_id: str,
) -> tuple[list[str], list[str], list[DiscoveryBlockedScope]]:
    discovered_ids: list[str] = []
    warnings: list[str] = []
    blocked: list[DiscoveryBlockedScope] = []
    try:
        token_result = credential.get_token("https://management.azure.com/.default")
    except Exception as error:
        blocked.append(
            DiscoveryBlockedScope(
                scope_type="tenant",
                scope_id=tenant_id.strip() or "unknown-tenant",
                reason=stringify_error(error),
            )
        )
        return [], warnings, blocked

    token = str(getattr(token_result, "token", "")).strip()
    if token == "":
        blocked.append(
            DiscoveryBlockedScope(
                scope_type="tenant",
                scope_id=tenant_id.strip() or "unknown-tenant",
                reason="Received empty ARM access token from Azure credential.",
            )
        )
        return [], warnings, blocked

    next_url: str | None = (
        "https://management.azure.com/subscriptions?api-version=2022-12-01"
    )
    while next_url:
        request = urllib.request.Request(
            url=next_url,
            method="GET",
            headers={
                "Authorization": f"Bearer {token}",
                "Accept": "application/json",
            },
        )
        try:
            with urllib.request.urlopen(request, timeout=20.0) as response:
                body = response.read()
        except urllib.error.HTTPError as error:
            blocked.append(
                DiscoveryBlockedScope(
                    scope_type="tenant",
                    scope_id=tenant_id.strip() or "unknown-tenant",
                    reason=f"Subscription enumeration failed with HTTP {error.code}.",
                )
            )
            return [], warnings, blocked
        except urllib.error.URLError as error:
            blocked.append(
                DiscoveryBlockedScope(
                    scope_type="tenant",
                    scope_id=tenant_id.strip() or "unknown-tenant",
                    reason=f"Subscription enumeration request failed: {error.reason}",
                )
            )
            return [], warnings, blocked

        try:
            payload = json.loads(body.decode("utf-8"))
        except Exception as error:
            blocked.append(
                DiscoveryBlockedScope(
                    scope_type="tenant",
                    scope_id=tenant_id.strip() or "unknown-tenant",
                    reason=f"Invalid JSON from ARM subscription list: {stringify_error(error)}",
                )
            )
            return [], warnings, blocked

        items = payload.get("value")
        if not isinstance(items, list):
            warnings.append("ARM subscription list response missing expected `value` array.")
            items = []

        for item in items:
            data = as_mapping(item)
            subscription_id = (
                str(
                    data.get("subscriptionId")
                    or data.get("subscription_id")
                    or data.get("id")
                    or ""
                )
                .strip()
                .removeprefix("/subscriptions/")
            )
            if subscription_id == "":
                warnings.append("Skipping subscription entry with no ID returned by Azure.")
                continue

            state = str(data.get("state", "")).strip()
            if state and state.lower() != "enabled":
                warnings.append(
                    f"Skipping subscription {subscription_id} because state is {state}."
                )
                continue
            discovered_ids.append(subscription_id)

        next_link = payload.get("nextLink")
        if isinstance(next_link, str) and next_link.strip():
            next_url = next_link.strip()
        else:
            next_url = None

    return normalize_subscription_ids(discovered_ids), warnings, blocked


def stringify_error(error: Exception) -> str:
    message = str(error).strip()
    if message:
        return message
    return error.__class__.__name__


def summarize_blocked_scopes(blocked_scopes: list[DiscoveryBlockedScope]) -> str:
    if not blocked_scopes:
        return ""
    parts = [
        f"{item.scope_type}:{item.scope_id}"
        for item in blocked_scopes[:3]
    ]
    summary = ", ".join(parts)
    if len(blocked_scopes) > 3:
        summary = f"{summary}, +{len(blocked_scopes) - 3} more"
    return f"Blocked scopes: {summary}."


def as_mapping(value: Any) -> dict[str, Any]:
    if isinstance(value, dict):
        return value
    as_dict = getattr(value, "as_dict", None)
    if callable(as_dict):
        maybe_mapping = as_dict()
        if isinstance(maybe_mapping, dict):
            return maybe_mapping
    return {}


def as_string_map(value: Any) -> dict[str, str]:
    if not isinstance(value, dict):
        return {}
    return {str(key): str(item) for key, item in value.items()}


def parse_resource_group_name(resource_id: str) -> str | None:
    parts = [part for part in resource_id.strip("/").split("/") if part != ""]
    for index, part in enumerate(parts):
        if part.lower() == "resourcegroups" and index + 1 < len(parts):
            return parts[index + 1]
    return None


def pick_container_app(
    *,
    container_apps: list[dict[str, Any]],
    preferred_name: str,
    managed_app_name: str,
) -> tuple[dict[str, Any], bool]:
    if preferred_name:
        for app in container_apps:
            app_name = str(app.get("name", "")).strip()
            if app_name.lower() == preferred_name.lower():
                return app, False

    if len(container_apps) == 1:
        return container_apps[0], False

    for app in container_apps:
        tags = as_string_map(app.get("tags"))
        marker = tags.get("mappoTarget", "").strip().lower()
        if marker in {"true", "1", "yes"}:
            return app, False

    managed_prefix = normalize_target_id(managed_app_name)
    for app in container_apps:
        app_name = normalize_target_id(str(app.get("name", "")).strip())
        if app_name.startswith(managed_prefix):
            return app, False

    ordered = sorted(container_apps, key=lambda item: str(item.get("name", "")))
    return ordered[0], True


def resolve_target_group(
    *,
    group_tag_key: str,
    default_target_group: str,
    managed_app_tags: dict[str, str],
    container_app_tags: dict[str, str],
) -> str:
    alternate_keys = [
        group_tag_key,
        "targetGroup",
        "target_group",
        "ring",
        "mappoTargetGroup",
    ]
    for key in alternate_keys:
        for source in (container_app_tags, managed_app_tags):
            value = source.get(key)
            if value and value.strip():
                return value.strip()
    return default_target_group


def normalize_target_id(value: str) -> str:
    normalized = re.sub(r"[^a-zA-Z0-9-]", "-", value).strip("-").lower()
    normalized = re.sub(r"-{2,}", "-", normalized)
    return normalized or "target"


def make_unique_target_id(*, seen: set[str], base: str) -> str:
    if base not in seen:
        seen.add(base)
        return base

    index = 2
    while True:
        candidate = f"{base}-{index}"
        if candidate not in seen:
            seen.add(candidate)
            return candidate
        index += 1
