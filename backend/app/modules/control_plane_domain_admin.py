from __future__ import annotations

import asyncio
from datetime import datetime

from app.modules.control_plane_common import StoreError, utc_now
from app.modules.control_plane_helpers import project_registration_from_target
from app.modules.execution import (
    AzureExecutionError,
    ContainerAppResourceRef,
    parse_container_app_resource_id,
)
from app.modules.schemas import (
    AdminOnboardingSnapshotResponse,
    MarketplaceEventIngestRequest,
    MarketplaceEventIngestResponse,
    MarketplaceEventRecord,
    MarketplaceEventStatus,
    Target,
    TargetRegistrationRecord,
    UpdateTargetRegistrationRequest,
)


class AdminDomainMixin:
    _lock: asyncio.Lock
    _targets: dict[str, Target]
    _registrations: dict[str, TargetRegistrationRecord]
    _marketplace_events: dict[str, MarketplaceEventRecord]

    def _save_target_locked(self, target: Target) -> None: ...
    def _save_target_registration_locked(self, registration: TargetRegistrationRecord) -> None: ...
    def _delete_target_locked(self, target_id: str) -> None: ...
    def _delete_target_registration_locked(self, target_id: str) -> None: ...
    def _save_marketplace_event_locked(self, event: MarketplaceEventRecord) -> None: ...

    async def get_onboarding_snapshot(
        self,
        *,
        event_limit: int = 50,
    ) -> AdminOnboardingSnapshotResponse:
        safe_limit = max(1, event_limit)
        async with self._lock:
            registrations = [
                project_registration_from_target(item, self._targets.get(item.target_id))
                for item in sorted(self._registrations.values(), key=lambda row: row.target_id)
            ]
            events = sorted(
                (item.model_copy(deep=True) for item in self._marketplace_events.values()),
                key=lambda row: row.created_at,
                reverse=True,
            )[:safe_limit]
        return AdminOnboardingSnapshotResponse(registrations=registrations, events=events)

    async def update_target_registration(
        self,
        target_id: str,
        request: UpdateTargetRegistrationRequest,
    ) -> TargetRegistrationRecord:
        async with self._lock:
            registration = self._registrations.get(target_id)
            target = self._targets.get(target_id)
            if registration is None or target is None:
                raise StoreError(f"target registration not found: {target_id}")

            fields = set(request.model_fields_set)
            if not fields:
                return project_registration_from_target(registration, self._targets.get(target_id))

            now = utc_now()
            updated_registration = registration.model_copy(deep=True)
            updated_target = target.model_copy(deep=True)

            if "display_name" in fields:
                if request.display_name is None:
                    updated_registration.display_name = updated_target.id
                else:
                    display_name = request.display_name.strip()
                    if display_name == "":
                        raise StoreError("display_name must not be empty when provided")
                    updated_registration.display_name = display_name

            if "customer_name" in fields:
                customer_name = request.customer_name.strip() if request.customer_name else ""
                updated_registration.customer_name = customer_name or None
                updated_target.customer_name = customer_name or None

            if "managed_application_id" in fields:
                managed_app_id = (
                    request.managed_application_id.strip()
                    if request.managed_application_id is not None
                    else ""
                )
                updated_registration.managed_application_id = (
                    self._normalize_resource_id(managed_app_id)
                    if managed_app_id != ""
                    else None
                )

            if "managed_resource_group_id" in fields:
                managed_rg_id = (
                    request.managed_resource_group_id.strip()
                    if request.managed_resource_group_id is not None
                    else ""
                )
                if managed_rg_id == "":
                    raise StoreError("managed_resource_group_id must not be empty when provided")
                updated_registration.managed_resource_group_id = self._normalize_resource_id(
                    managed_rg_id
                )

            if "container_app_resource_id" in fields:
                container_resource_id = (
                    request.container_app_resource_id.strip()
                    if request.container_app_resource_id is not None
                    else ""
                )
                if container_resource_id == "":
                    raise StoreError("container_app_resource_id must not be empty when provided")
                normalized_container_resource_id = self._normalize_resource_id(
                    container_resource_id
                )
                container_ref = self._parse_container_app_resource_id_safe(
                    normalized_container_resource_id
                )
                if container_ref.subscription_id != updated_target.subscription_id:
                    raise StoreError(
                        "container_app_resource_id subscription must match target subscription_id"
                    )
                updated_registration.container_app_resource_id = normalized_container_resource_id
                updated_target.managed_app_id = normalized_container_resource_id
                if "managed_resource_group_id" not in fields:
                    updated_registration.managed_resource_group_id = (
                        f"/subscriptions/{container_ref.subscription_id}/resourceGroups/"
                        f"{container_ref.resource_group_name}"
                    )

            tags = dict(updated_registration.tags)
            if "tags" in fields:
                tags = {}
                for key, value in (request.tags or {}).items():
                    normalized_key = key.strip()
                    normalized_value = value.strip()
                    if normalized_key == "" or normalized_value == "":
                        continue
                    tags[normalized_key] = normalized_value

            if "target_group" in fields:
                target_group = request.target_group.strip() if request.target_group else ""
                tags["ring"] = target_group or "prod"
            if "region" in fields:
                region = request.region.strip() if request.region else ""
                tags["region"] = region or "unknown"
            if "environment" in fields:
                environment = request.environment.strip() if request.environment else ""
                tags["environment"] = environment or "prod"
            if "tier" in fields:
                tier = request.tier.strip() if request.tier else ""
                tags["tier"] = tier or "standard"

            if fields.intersection({"tags", "target_group", "region", "environment", "tier"}):
                if tags.get("ring", "").strip() == "":
                    tags["ring"] = "prod"
                if tags.get("region", "").strip() == "":
                    tags["region"] = "unknown"
                if tags.get("environment", "").strip() == "":
                    tags["environment"] = "prod"
                if tags.get("tier", "").strip() == "":
                    tags["tier"] = "standard"
                updated_registration.tags = tags
                updated_target.tags = dict(tags)

            if "metadata" in fields:
                updated_registration.metadata = request.metadata or {}

            if "health_status" in fields:
                health_status = request.health_status.strip() if request.health_status else ""
                if health_status == "":
                    raise StoreError("health_status must not be empty when provided")
                updated_target.health_status = health_status

            if "last_deployed_release" in fields:
                deployed_release = (
                    request.last_deployed_release.strip() if request.last_deployed_release else ""
                )
                updated_target.last_deployed_release = deployed_release or "unknown"

            updated_registration.updated_at = now

            self._registrations[target_id] = updated_registration
            self._save_target_registration_locked(updated_registration)
            self._targets[target_id] = updated_target
            self._save_target_locked(updated_target)

            return project_registration_from_target(updated_registration, updated_target)

    async def delete_target_registration(self, target_id: str) -> None:
        async with self._lock:
            deleted = False
            if target_id in self._registrations:
                del self._registrations[target_id]
                self._delete_target_registration_locked(target_id)
                deleted = True
            if target_id in self._targets:
                del self._targets[target_id]
                self._delete_target_locked(target_id)
                deleted = True
            if not deleted:
                raise StoreError(f"target registration not found: {target_id}")

    async def ingest_marketplace_event(
        self,
        request: MarketplaceEventIngestRequest,
    ) -> MarketplaceEventIngestResponse:
        async with self._lock:
            existing = self._marketplace_events.get(request.event_id)
            if existing is not None:
                return MarketplaceEventIngestResponse(
                    event_id=existing.event_id,
                    status=MarketplaceEventStatus.DUPLICATE,
                    message=(
                        f"Event already processed with status {existing.status}: "
                        f"{existing.message}"
                    ),
                    target_id=existing.target_id,
                )

            now = utc_now()
            try:
                target, registration = self._build_registration_locked(request=request, now=now)
            except StoreError as error:
                event = MarketplaceEventRecord(
                    event_id=request.event_id,
                    event_type=request.event_type,
                    status=MarketplaceEventStatus.REJECTED,
                    message=error.message,
                    target_id=None,
                    tenant_id=request.tenant_id.strip(),
                    subscription_id=request.subscription_id.strip(),
                    payload=request.model_dump(mode="json"),
                    created_at=now,
                    processed_at=now,
                )
                self._marketplace_events[event.event_id] = event
                self._save_marketplace_event_locked(event)
                return MarketplaceEventIngestResponse(
                    event_id=event.event_id,
                    status=event.status,
                    message=event.message,
                    target_id=event.target_id,
                )

            self._targets[target.id] = target
            self._save_target_locked(target)
            self._registrations[registration.target_id] = registration
            self._save_target_registration_locked(registration)

            event = MarketplaceEventRecord(
                event_id=request.event_id,
                event_type=request.event_type,
                status=MarketplaceEventStatus.APPLIED,
                message=(
                    f"Registered target {target.id} for subscription "
                    f"{target.subscription_id}."
                ),
                target_id=target.id,
                tenant_id=target.tenant_id,
                subscription_id=target.subscription_id,
                payload=request.model_dump(mode="json"),
                created_at=now,
                processed_at=now,
            )
            self._marketplace_events[event.event_id] = event
            self._save_marketplace_event_locked(event)

            return MarketplaceEventIngestResponse(
                event_id=event.event_id,
                status=event.status,
                message=event.message,
                target_id=event.target_id,
            )

    def _build_registration_locked(
        self,
        *,
        request: MarketplaceEventIngestRequest,
        now: datetime,
    ) -> tuple[Target, TargetRegistrationRecord]:
        tenant_id = request.tenant_id.strip()
        subscription_id = request.subscription_id.strip()
        if tenant_id == "":
            raise StoreError("tenant_id is required")
        if subscription_id == "":
            raise StoreError("subscription_id is required")

        container_app_resource_id = self._resolve_container_app_resource_id(
            request=request,
            subscription_id=subscription_id,
        )
        managed_resource_group_id = self._resolve_managed_resource_group_id(
            request=request,
            container_app_resource_id=container_app_resource_id,
            subscription_id=subscription_id,
        )
        managed_application_id = self._normalize_managed_application_id(
            request=request,
            subscription_id=subscription_id,
        )

        container_ref = self._parse_container_app_resource_id_safe(container_app_resource_id)
        target_id = self._resolve_target_id(
            request=request,
            managed_application_id=managed_application_id,
            container_app_name=container_ref.container_app_name,
        )
        tags = self._build_target_tags(request=request)

        existing_registration = self._registrations.get(target_id)
        created_at = existing_registration.created_at if existing_registration else now

        target = Target(
            id=target_id,
            tenant_id=tenant_id,
            subscription_id=subscription_id,
            managed_app_id=container_app_resource_id,
            tags=tags,
            last_deployed_release=request.last_deployed_release.strip() or "unknown",
            health_status=request.health_status.strip() or "registered",
            last_check_in_at=request.event_time or now,
            simulated_failure_mode="none",
        )
        registration = TargetRegistrationRecord(
            target_id=target.id,
            tenant_id=tenant_id,
            subscription_id=subscription_id,
            managed_application_id=managed_application_id,
            managed_resource_group_id=managed_resource_group_id,
            container_app_resource_id=container_app_resource_id,
            display_name=self._resolve_display_name(request=request, default_name=target.id),
            customer_name=request.customer_name.strip() or None
            if request.customer_name is not None
            else None,
            tags=tags,
            metadata=request.metadata,
            last_event_id=request.event_id,
            created_at=created_at,
            updated_at=now,
        )
        return target, registration

    def _resolve_target_id(
        self,
        *,
        request: MarketplaceEventIngestRequest,
        managed_application_id: str | None,
        container_app_name: str,
    ) -> str:
        explicit_target_id = request.target_id.strip() if request.target_id else ""
        if explicit_target_id != "":
            return explicit_target_id
        managed_app_name = self._extract_managed_app_name(managed_application_id)
        if managed_app_name is not None:
            return managed_app_name
        return container_app_name

    def _resolve_display_name(
        self,
        *,
        request: MarketplaceEventIngestRequest,
        default_name: str,
    ) -> str:
        if request.display_name is not None and request.display_name.strip() != "":
            return request.display_name.strip()
        if request.customer_name is not None and request.customer_name.strip() != "":
            return request.customer_name.strip()
        return default_name

    def _build_target_tags(
        self,
        *,
        request: MarketplaceEventIngestRequest,
    ) -> dict[str, str]:
        normalized_tags: dict[str, str] = {}
        for key, value in request.tags.items():
            tag_key = key.strip()
            tag_value = value.strip()
            if tag_key == "" or tag_value == "":
                continue
            normalized_tags[tag_key] = tag_value
        normalized_tags["ring"] = request.target_group.strip() or "prod"
        region = request.region.strip() if request.region is not None else ""
        normalized_tags["region"] = region if region != "" else "unknown"
        normalized_tags["environment"] = (
            request.environment.strip() if request.environment.strip() != "" else "prod"
        )
        normalized_tags["tier"] = request.tier.strip() if request.tier.strip() != "" else "standard"
        return normalized_tags

    def _resolve_container_app_resource_id(
        self,
        *,
        request: MarketplaceEventIngestRequest,
        subscription_id: str,
    ) -> str:
        if request.container_app_resource_id is not None:
            resource_id = self._normalize_resource_id(request.container_app_resource_id)
            ref = self._parse_container_app_resource_id_safe(resource_id)
            if ref.subscription_id != subscription_id:
                raise StoreError(
                    "container_app_resource_id subscription does not match subscription_id"
                )
            return resource_id

        managed_rg = request.managed_resource_group_id
        app_name = request.container_app_name
        error_message = (
            "provide container_app_resource_id or "
            "managed_resource_group_id + container_app_name"
        )
        if managed_rg is None or managed_rg.strip() == "":
            raise StoreError(error_message)
        if app_name is None or app_name.strip() == "":
            raise StoreError(error_message)

        managed_rg_resource_id = self._normalize_resource_id(managed_rg)
        rg_parts = [part for part in managed_rg_resource_id.strip("/").split("/") if part]
        if len(rg_parts) != 4:
            raise StoreError("managed_resource_group_id is not a valid resource-group ID")
        if rg_parts[0].lower() != "subscriptions" or rg_parts[2].lower() != "resourcegroups":
            raise StoreError(
                "managed_resource_group_id is missing subscription/resource-group segments"
            )
        if rg_parts[1] != subscription_id:
            raise StoreError(
                "managed_resource_group_id subscription does not match subscription_id"
            )
        return (
            f"/subscriptions/{subscription_id}/resourceGroups/{rg_parts[3]}"
            f"/providers/Microsoft.App/containerApps/{app_name.strip()}"
        )

    def _resolve_managed_resource_group_id(
        self,
        *,
        request: MarketplaceEventIngestRequest,
        container_app_resource_id: str,
        subscription_id: str,
    ) -> str:
        container_parts = [part for part in container_app_resource_id.strip("/").split("/") if part]
        if len(container_parts) != 8:
            raise StoreError("container_app_resource_id is not a valid resource ID")
        inferred = f"/subscriptions/{container_parts[1]}/resourceGroups/{container_parts[3]}"

        if (
            request.managed_resource_group_id is None
            or request.managed_resource_group_id.strip() == ""
        ):
            return inferred

        provided = self._normalize_resource_id(request.managed_resource_group_id)
        provided_parts = [part for part in provided.strip("/").split("/") if part]
        if len(provided_parts) != 4:
            raise StoreError("managed_resource_group_id is not a valid resource-group ID")
        if (
            provided_parts[0].lower() != "subscriptions"
            or provided_parts[2].lower() != "resourcegroups"
        ):
            raise StoreError(
                "managed_resource_group_id is missing subscription/resource-group segments"
            )
        if provided_parts[1] != subscription_id:
            raise StoreError(
                "managed_resource_group_id subscription does not match subscription_id"
            )
        if provided_parts[3] != container_parts[3]:
            raise StoreError(
                "managed_resource_group_id does not match container_app_resource_id resource group"
            )
        return provided

    def _normalize_managed_application_id(
        self,
        *,
        request: MarketplaceEventIngestRequest,
        subscription_id: str,
    ) -> str | None:
        if request.managed_application_id is None or request.managed_application_id.strip() == "":
            return None

        resource_id = self._normalize_resource_id(request.managed_application_id)
        parts = [part for part in resource_id.strip("/").split("/") if part]
        if len(parts) != 8:
            raise StoreError(
                "managed_application_id is not a valid managed application resource ID"
            )
        if parts[0].lower() != "subscriptions" or parts[2].lower() != "resourcegroups":
            raise StoreError(
                "managed_application_id is missing subscription/resource-group segments"
            )
        if parts[4].lower() != "providers" or parts[5].lower() != "microsoft.solutions":
            raise StoreError("managed_application_id provider must be Microsoft.Solutions")
        if parts[6].lower() != "applications":
            raise StoreError("managed_application_id type must be applications")
        if parts[1] != subscription_id:
            raise StoreError("managed_application_id subscription does not match subscription_id")
        return resource_id

    def _extract_managed_app_name(
        self,
        managed_application_id: str | None,
    ) -> str | None:
        if managed_application_id is None:
            return None
        parts = [part for part in managed_application_id.strip("/").split("/") if part]
        return parts[7] if len(parts) == 8 else None

    def _parse_container_app_resource_id_safe(
        self,
        resource_id: str,
    ) -> ContainerAppResourceRef:
        try:
            return parse_container_app_resource_id(resource_id)
        except AzureExecutionError as error:
            raise StoreError(error.message) from error

    @staticmethod
    def _normalize_resource_id(resource_id: str) -> str:
        trimmed = resource_id.strip()
        if not trimmed:
            raise StoreError("resource ID must not be empty")
        return trimmed if trimmed.startswith("/") else f"/{trimmed}"
