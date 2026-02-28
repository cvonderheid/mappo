from __future__ import annotations

import asyncio
import copy
import hashlib
import random
import re
import time
import urllib.error
import urllib.request
from collections.abc import AsyncIterator, Callable
from dataclasses import dataclass, field
from enum import StrEnum
from typing import Any, Literal, Protocol

from app.modules.schemas import (
    DeploymentRun,
    Release,
    StructuredError,
    Target,
    TargetStage,
)


class ExecutionMode(StrEnum):
    DEMO = "demo"
    AZURE = "azure"


ExecutionEventType = Literal["started", "completed"]


@dataclass(frozen=True)
class AzureExecutorSettings:
    tenant_id: str | None = None
    client_id: str | None = None
    client_secret: str | None = None
    tenant_by_subscription: dict[str, str] = field(default_factory=dict)
    verify_timeout_seconds: float = 180.0
    verify_poll_interval_seconds: float = 5.0
    health_timeout_seconds: float = 10.0
    max_run_concurrency: int = 6
    max_subscription_concurrency: int = 2
    max_retry_attempts: int = 5
    retry_base_delay_seconds: float = 1.0
    retry_max_delay_seconds: float = 20.0
    retry_jitter_seconds: float = 0.35
    enable_quota_preflight: bool = True
    quota_warning_headroom_ratio: float = 0.1
    quota_min_remaining_warning: int = 2


@dataclass(frozen=True)
class TargetExecutionEvent:
    event_type: ExecutionEventType
    stage: TargetStage
    correlation_id: str
    message: str
    error: StructuredError | None = None
    terminal_state: TargetStage | None = None


@dataclass(frozen=True)
class ContainerAppResourceRef:
    subscription_id: str
    resource_group_name: str
    container_app_name: str


@dataclass(frozen=True)
class AzureContainerAppSnapshot:
    resource_ref: ContainerAppResourceRef
    current_image: str
    latest_revision_name: str | None
    latest_ready_revision_name: str | None
    latest_revision_fqdn: str | None
    raw_app: Any


@dataclass(frozen=True)
class AzureDeployResult:
    snapshot: AzureContainerAppSnapshot
    desired_image: str
    changed: bool


@dataclass(frozen=True)
class AzureVerifyResult:
    health_url: str
    status_code: int
    ready_revision: str


@dataclass(frozen=True)
class RunGuardrailPlan:
    effective_concurrency: int
    subscription_concurrency: int
    warnings: list[str]


@dataclass(frozen=True)
class AzureQuotaPreflightResult:
    warnings: list[str]
    recommended_run_concurrency: int | None = None
    recommended_subscription_concurrency: int | None = None


class AzureExecutionError(Exception):
    def __init__(
        self,
        *,
        code: str,
        message: str,
        details: dict[str, Any] | None = None,
    ):
        super().__init__(message)
        self.code = code
        self.message = message
        self.details = details


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


class TargetExecutor(Protocol):
    async def prepare_run(
        self,
        *,
        targets: list[Target],
        requested_concurrency: int,
    ) -> RunGuardrailPlan:
        ...

    def execute_target(
        self,
        *,
        run: DeploymentRun,
        target: Target,
        release: Release,
        attempt: int,
    ) -> AsyncIterator[TargetExecutionEvent]:
        ...


class AzureRuntime(Protocol):
    def preflight_targets(
        self,
        *,
        targets: list[Target],
        requested_concurrency: int,
    ) -> AzureQuotaPreflightResult:
        ...

    def validate_target(self, target: Target) -> AzureContainerAppSnapshot:
        ...

    def deploy_release(
        self,
        *,
        target: Target,
        release: Release,
        snapshot: AzureContainerAppSnapshot,
    ) -> AzureDeployResult:
        ...

    def verify_target(
        self,
        *,
        target: Target,
        release: Release,
        snapshot: AzureContainerAppSnapshot,
    ) -> AzureVerifyResult:
        ...


AzureRuntimeFactory = Callable[[AzureExecutorSettings], AzureRuntime]


def create_target_executor(
    *,
    mode: ExecutionMode,
    stage_delay_seconds: float,
    azure_settings: AzureExecutorSettings,
) -> TargetExecutor:
    if mode == ExecutionMode.AZURE:
        return AzureTargetExecutor(azure_settings=azure_settings)
    return DemoTargetExecutor(stage_delay_seconds=stage_delay_seconds)


class DemoTargetExecutor:
    def __init__(self, *, stage_delay_seconds: float):
        self._stage_delay_seconds = max(0.0, stage_delay_seconds)

    async def prepare_run(
        self,
        *,
        targets: list[Target],
        requested_concurrency: int,
    ) -> RunGuardrailPlan:
        del targets
        concurrency = max(1, requested_concurrency)
        return RunGuardrailPlan(
            effective_concurrency=concurrency,
            subscription_concurrency=concurrency,
            warnings=[],
        )

    async def execute_target(
        self,
        *,
        run: DeploymentRun,
        target: Target,
        release: Release,
        attempt: int,
    ) -> AsyncIterator[TargetExecutionEvent]:
        del release
        for stage in [TargetStage.VALIDATING, TargetStage.DEPLOYING, TargetStage.VERIFYING]:
            correlation_id = build_correlation_id(run.id, target.id, attempt, stage)
            yield TargetExecutionEvent(
                event_type="started",
                stage=stage,
                correlation_id=correlation_id,
                message=f"{stage.value.title()} started.",
            )
            if self._stage_delay_seconds > 0:
                await asyncio.sleep(self._stage_delay_seconds)

            if self._should_fail_target(target=target, attempt=attempt, stage=stage):
                yield TargetExecutionEvent(
                    event_type="completed",
                    stage=stage,
                    correlation_id=correlation_id,
                    message="Target failed verification checks.",
                    error=StructuredError(
                        code="verification_failed",
                        message="Simulated verification failure. Retry or resume the run.",
                        details={"target_id": target.id, "attempt": attempt},
                    ),
                    terminal_state=TargetStage.FAILED,
                )
                return

            yield TargetExecutionEvent(
                event_type="completed",
                stage=stage,
                correlation_id=correlation_id,
                message=f"{stage.value.title()} completed.",
            )

        success_correlation = build_correlation_id(
            run.id,
            target.id,
            attempt,
            TargetStage.SUCCEEDED,
        )
        yield TargetExecutionEvent(
            event_type="started",
            stage=TargetStage.SUCCEEDED,
            correlation_id=success_correlation,
            message="Succeeded started.",
        )
        yield TargetExecutionEvent(
            event_type="completed",
            stage=TargetStage.SUCCEEDED,
            correlation_id=success_correlation,
            message="Target deployment succeeded.",
            terminal_state=TargetStage.SUCCEEDED,
        )

    @staticmethod
    def _should_fail_target(
        *,
        target: Target,
        attempt: int,
        stage: TargetStage,
    ) -> bool:
        if stage != TargetStage.VERIFYING:
            return False
        failure_mode = target.simulated_failure_mode
        if failure_mode == "verify_once" and attempt == 1:
            return True
        if failure_mode == "always_fail":
            return True
        return False


class AzureTargetExecutor:
    def __init__(
        self,
        *,
        azure_settings: AzureExecutorSettings,
        runtime_factory: AzureRuntimeFactory | None = None,
    ):
        self._settings = azure_settings
        self._runtime_factory = runtime_factory or create_azure_runtime

    async def prepare_run(
        self,
        *,
        targets: list[Target],
        requested_concurrency: int,
    ) -> RunGuardrailPlan:
        requested = max(1, requested_concurrency)
        warnings: list[str] = []

        effective = min(requested, max(1, self._settings.max_run_concurrency))
        if effective < requested:
            warnings.append(
                "Run concurrency capped for Azure safety "
                f"({requested} -> {effective})."
            )

        per_subscription = min(
            effective,
            max(1, self._settings.max_subscription_concurrency),
        )
        if per_subscription < effective:
            warnings.append(
                "Per-subscription concurrency cap applied "
                f"(max {per_subscription} per subscription)."
            )

        target_count_by_subscription: dict[str, int] = {}
        for target in targets:
            target_count_by_subscription[target.subscription_id] = (
                target_count_by_subscription.get(target.subscription_id, 0) + 1
            )
        busiest_subscription = max(target_count_by_subscription.values(), default=0)
        if busiest_subscription > per_subscription:
            warnings.append(
                "Targets are concentrated in one or more subscriptions; scheduling will batch "
                "requests by subscription to reduce ARM throttling."
            )

        if self._settings.enable_quota_preflight:
            try:
                runtime = self._runtime_factory(self._settings)
            except Exception as error:
                warnings.append(
                    "Quota preflight skipped because runtime initialization failed: "
                    f"{stringify_exception(error)}"
                )
            else:
                preflight_fn = getattr(runtime, "preflight_targets", None)
                if callable(preflight_fn):
                    preflight = await asyncio.to_thread(
                        preflight_fn,
                        targets=targets,
                        requested_concurrency=requested,
                    )
                    warnings.extend(preflight.warnings)
                    if preflight.recommended_run_concurrency is not None:
                        bounded = max(1, preflight.recommended_run_concurrency)
                        if bounded < effective:
                            warnings.append(
                                "Quota preflight lowered run concurrency "
                                f"({effective} -> {bounded})."
                            )
                            effective = bounded
                    if preflight.recommended_subscription_concurrency is not None:
                        bounded = max(1, preflight.recommended_subscription_concurrency)
                        if bounded < per_subscription:
                            warnings.append(
                                "Quota preflight lowered per-subscription concurrency "
                                f"({per_subscription} -> {bounded})."
                            )
                            per_subscription = bounded

        per_subscription = min(per_subscription, effective)
        return RunGuardrailPlan(
            effective_concurrency=effective,
            subscription_concurrency=per_subscription,
            warnings=dedupe_strings_in_order(warnings),
        )

    async def execute_target(
        self,
        *,
        run: DeploymentRun,
        target: Target,
        release: Release,
        attempt: int,
    ) -> AsyncIterator[TargetExecutionEvent]:
        runtime = None
        validating_correlation = build_correlation_id(
            run.id, target.id, attempt, TargetStage.VALIDATING
        )
        yield TargetExecutionEvent(
            event_type="started",
            stage=TargetStage.VALIDATING,
            correlation_id=validating_correlation,
            message="Validating started.",
        )
        try:
            runtime = self._runtime_factory(self._settings)
            snapshot = await asyncio.to_thread(runtime.validate_target, target)
        except AzureExecutionError as error:
            yield self._failed_event(
                correlation_id=validating_correlation,
                stage=TargetStage.VALIDATING,
                error=error,
            )
            return

        yield TargetExecutionEvent(
            event_type="completed",
            stage=TargetStage.VALIDATING,
            correlation_id=validating_correlation,
            message=(
                f"Validated target {snapshot.resource_ref.container_app_name}; "
                f"current image {snapshot.current_image}."
            ),
        )

        deploying_correlation = build_correlation_id(
            run.id,
            target.id,
            attempt,
            TargetStage.DEPLOYING,
        )
        yield TargetExecutionEvent(
            event_type="started",
            stage=TargetStage.DEPLOYING,
            correlation_id=deploying_correlation,
            message="Deploying started.",
        )
        try:
            deploy_result = await asyncio.to_thread(
                runtime.deploy_release,
                target=target,
                release=release,
                snapshot=snapshot,
            )
        except AzureExecutionError as error:
            yield self._failed_event(
                correlation_id=deploying_correlation,
                stage=TargetStage.DEPLOYING,
                error=error,
            )
            return

        deployment_message = (
            f"Deployment completed; image now {deploy_result.desired_image}."
            if deploy_result.changed
            else f"No image change requested; keeping {deploy_result.desired_image}."
        )
        yield TargetExecutionEvent(
            event_type="completed",
            stage=TargetStage.DEPLOYING,
            correlation_id=deploying_correlation,
            message=deployment_message,
        )

        verifying_correlation = build_correlation_id(
            run.id,
            target.id,
            attempt,
            TargetStage.VERIFYING,
        )
        yield TargetExecutionEvent(
            event_type="started",
            stage=TargetStage.VERIFYING,
            correlation_id=verifying_correlation,
            message="Verifying started.",
        )
        try:
            verify_result = await asyncio.to_thread(
                runtime.verify_target,
                target=target,
                release=release,
                snapshot=deploy_result.snapshot,
            )
        except AzureExecutionError as error:
            yield self._failed_event(
                correlation_id=verifying_correlation,
                stage=TargetStage.VERIFYING,
                error=error,
            )
            return

        yield TargetExecutionEvent(
            event_type="completed",
            stage=TargetStage.VERIFYING,
            correlation_id=verifying_correlation,
            message=(
                f"Health check passed ({verify_result.status_code}) on "
                f"{verify_result.health_url}; ready revision {verify_result.ready_revision}."
            ),
        )

        success_correlation = build_correlation_id(
            run.id,
            target.id,
            attempt,
            TargetStage.SUCCEEDED,
        )
        yield TargetExecutionEvent(
            event_type="started",
            stage=TargetStage.SUCCEEDED,
            correlation_id=success_correlation,
            message="Succeeded started.",
        )
        yield TargetExecutionEvent(
            event_type="completed",
            stage=TargetStage.SUCCEEDED,
            correlation_id=success_correlation,
            message="Target deployment succeeded.",
            terminal_state=TargetStage.SUCCEEDED,
        )

    @staticmethod
    def _failed_event(
        *,
        correlation_id: str,
        stage: TargetStage,
        error: AzureExecutionError,
    ) -> TargetExecutionEvent:
        return TargetExecutionEvent(
            event_type="completed",
            stage=stage,
            correlation_id=correlation_id,
            message=error.message,
            error=StructuredError(
                code=error.code,
                message=error.message,
                details=error.details,
            ),
            terminal_state=TargetStage.FAILED,
        )


class AzureSdkRuntime:
    def __init__(self, settings: AzureExecutorSettings):
        has_tenant_source = bool(
            normalize_tenant_hint(settings.tenant_id)
            or settings.tenant_by_subscription
        )
        if not (has_tenant_source and settings.client_id and settings.client_secret):
            raise AzureExecutionError(
                code="azure_executor_not_configured",
                message=(
                    "Azure execution mode requires MAPPO_AZURE_CLIENT_ID and "
                    "MAPPO_AZURE_CLIENT_SECRET plus tenant authority "
                    "(MAPPO_AZURE_TENANT_ID or MAPPO_AZURE_TENANT_BY_SUBSCRIPTION)."
                ),
            )

        try:
            from azure.identity import ClientSecretCredential
            from azure.mgmt.appcontainers import ContainerAppsAPIClient
            from azure.mgmt.appcontainers.models import ContainerApp
        except Exception as error:  # pragma: no cover - exercised by integration only
            raise AzureExecutionError(
                code="azure_sdk_not_available",
                message=(
                    "Azure SDK dependencies are unavailable. Install azure-identity and "
                    "azure-mgmt-appcontainers."
                ),
                details={"error": str(error)},
            ) from error

        self._client_secret_credential_type = ClientSecretCredential
        # Guarded above by credential validation.
        self._client_id = settings.client_id or ""
        self._client_secret = settings.client_secret or ""
        self._default_tenant_id = normalize_tenant_hint(settings.tenant_id)
        self._tenant_by_subscription = {
            key.strip(): normalize_tenant_hint(value) or ""
            for key, value in settings.tenant_by_subscription.items()
            if key.strip() != "" and normalize_tenant_hint(value) is not None
        }
        self._container_apps_client_type = ContainerAppsAPIClient
        self._container_app_model_type = ContainerApp
        self._credentials_by_tenant: dict[str, Any] = {}
        self._clients_by_subscription: dict[str, Any] = {}
        self._tenant_by_client_subscription: dict[str, str] = {}
        self._verify_timeout_seconds = max(settings.verify_timeout_seconds, 5.0)
        self._verify_poll_interval_seconds = max(settings.verify_poll_interval_seconds, 1.0)
        self._health_timeout_seconds = max(settings.health_timeout_seconds, 1.0)
        self._max_retry_attempts = max(1, settings.max_retry_attempts)
        self._retry_base_delay_seconds = max(0.25, settings.retry_base_delay_seconds)
        self._retry_max_delay_seconds = max(
            self._retry_base_delay_seconds,
            settings.retry_max_delay_seconds,
        )
        self._retry_jitter_seconds = max(0.0, settings.retry_jitter_seconds)
        self._quota_warning_headroom_ratio = min(
            1.0,
            max(0.0, settings.quota_warning_headroom_ratio),
        )
        self._quota_min_remaining_warning = max(0, settings.quota_min_remaining_warning)

    def preflight_targets(
        self,
        *,
        targets: list[Target],
        requested_concurrency: int,
    ) -> AzureQuotaPreflightResult:
        del requested_concurrency
        regions_by_subscription: dict[str, set[str]] = {}
        warnings: list[str] = []
        recommended_run_cap: int | None = None
        recommended_subscription_cap: int | None = None

        tenant_hint_by_subscription: dict[str, str] = {}

        for target in targets:
            region = normalize_location(target.tags.get("region"))
            if region is None:
                warnings.append(
                    "Target "
                    f"{target.id} has no region tag; skipped ACA quota preflight for that target."
                )
                continue
            resolved_tenant = resolve_tenant_for_subscription(
                subscription_id=target.subscription_id,
                target_tenant_hint=target.tenant_id,
                default_tenant_id=self._default_tenant_id,
                tenant_by_subscription=self._tenant_by_subscription,
            )
            if resolved_tenant is None:
                warnings.append(
                    "Target "
                    f"{target.id} has no resolvable tenant authority for subscription "
                    f"{target.subscription_id}."
                )
                continue
            tenant_hint_by_subscription[target.subscription_id] = resolved_tenant
            if target.subscription_id not in regions_by_subscription:
                regions_by_subscription[target.subscription_id] = set()
            regions_by_subscription[target.subscription_id].add(region)

        for subscription_id, regions in regions_by_subscription.items():
            tenant_hint = tenant_hint_by_subscription.get(subscription_id)
            if tenant_hint is None:
                warnings.append(
                    "Skipping ACA quota preflight for "
                    f"{subscription_id} because tenant authority is unresolved."
                )
                continue
            client = self._client(
                subscription_id=subscription_id,
                tenant_hint=tenant_hint,
            )
            for region in sorted(regions):
                region_value = str(region)

                def _list_usages(
                    *,
                    client: Any = client,
                    region: str = region_value,
                ) -> Any:
                    return client.usages.list(location=region)

                try:
                    usage_page = self._with_retries(
                        operation_name="aca-usages-list",
                        operation=_list_usages,
                    )
                    usage_items = list(usage_page)
                except Exception as error:
                    warnings.append(
                        "Unable to read ACA quota usage for "
                        f"{subscription_id}/{region}: {stringify_exception(error)}"
                    )
                    continue

                for usage_item in usage_items:
                    parsed = parse_usage_item(usage_item)
                    if parsed is None:
                        continue
                    usage_name, current, limit = parsed
                    if limit <= 0:
                        continue
                    remaining = limit - current
                    ratio = remaining / limit
                    if remaining <= 0:
                        warnings.append(
                            "ACA quota is fully consumed for "
                            f"{subscription_id}/{region} ({usage_name}: {current:.0f}/{limit:.0f})."
                        )
                        recommended_run_cap = 1
                        recommended_subscription_cap = 1
                    elif (
                        remaining <= self._quota_min_remaining_warning
                        or ratio <= self._quota_warning_headroom_ratio
                    ):
                        warnings.append(
                            "Low ACA quota headroom for "
                            f"{subscription_id}/{region} ({usage_name}: {current:.0f}/{limit:.0f})."
                        )
                        if (
                            recommended_subscription_cap is None
                            or recommended_subscription_cap > 1
                        ):
                            recommended_subscription_cap = 1

        return AzureQuotaPreflightResult(
            warnings=dedupe_strings_in_order(warnings),
            recommended_run_concurrency=recommended_run_cap,
            recommended_subscription_concurrency=recommended_subscription_cap,
        )

    def validate_target(self, target: Target) -> AzureContainerAppSnapshot:
        ref = parse_container_app_resource_id(target.managed_app_id)
        if ref.subscription_id != target.subscription_id:
            raise AzureExecutionError(
                code="azure_subscription_mismatch",
                message="Target subscription does not match managed app resource ID.",
                details={
                    "target_id": target.id,
                    "target_subscription_id": target.subscription_id,
                    "resource_subscription_id": ref.subscription_id,
                    "managed_app_id": target.managed_app_id,
                },
            )
        app = self._get_container_app(ref=ref, target=target)
        snapshot = self._snapshot_from_app(ref=ref, app=app, target=target)
        if not snapshot.current_image:
            raise AzureExecutionError(
                code="azure_container_image_missing",
                message="Container App has no deployable container image configured.",
                details={
                    "target_id": target.id,
                    "resource_group_name": ref.resource_group_name,
                    "container_app_name": ref.container_app_name,
                },
            )
        return snapshot

    def deploy_release(
        self,
        *,
        target: Target,
        release: Release,
        snapshot: AzureContainerAppSnapshot,
    ) -> AzureDeployResult:
        desired_image = resolve_desired_image(
            current_image=snapshot.current_image,
            parameter_defaults=release.parameter_defaults,
        )
        desired_feature_flag = resolve_desired_feature_flag(release.parameter_defaults)
        current_feature_flag = read_container_env_value(
            app=snapshot.raw_app,
            env_name="MAPPO_FEATURE_FLAG",
        )
        feature_flag_changed = (
            desired_feature_flag is not None and desired_feature_flag != current_feature_flag
        )

        if desired_image == snapshot.current_image and not feature_flag_changed:
            return AzureDeployResult(
                snapshot=snapshot,
                desired_image=desired_image,
                changed=False,
            )

        app_to_update = self._build_update_payload(
            snapshot.raw_app,
            desired_image=desired_image,
            desired_feature_flag=desired_feature_flag,
        )
        client = self._client(
            subscription_id=snapshot.resource_ref.subscription_id,
            tenant_hint=target.tenant_id,
        )
        try:
            poller = self._with_retries(
                operation_name="container-app-begin-update",
                operation=lambda: client.container_apps.begin_update(
                    resource_group_name=snapshot.resource_ref.resource_group_name,
                    container_app_name=snapshot.resource_ref.container_app_name,
                    container_app_envelope=app_to_update,
                ),
            )
            updated = self._with_retries(
                operation_name="container-app-update-result",
                operation=lambda: poller.result(timeout=self._verify_timeout_seconds),
            )
        except TypeError:
            updated = poller.result()
        except Exception as error:
            raise self._translate_exception(
                error=error,
                code="azure_deploy_failed",
                message="Azure Container App update failed.",
                details={
                    "target_id": target.id,
                    "resource_group_name": snapshot.resource_ref.resource_group_name,
                    "container_app_name": snapshot.resource_ref.container_app_name,
                    "desired_image": desired_image,
                    "desired_feature_flag": desired_feature_flag,
                },
            ) from error

        updated_snapshot = self._snapshot_from_app(
            ref=snapshot.resource_ref,
            app=updated,
            target=target,
        )
        return AzureDeployResult(
            snapshot=updated_snapshot,
            desired_image=desired_image,
            changed=True,
        )

    def verify_target(
        self,
        *,
        target: Target,
        release: Release,
        snapshot: AzureContainerAppSnapshot,
    ) -> AzureVerifyResult:
        deadline = time.monotonic() + self._verify_timeout_seconds
        last_snapshot = snapshot
        while time.monotonic() < deadline:
            app = self._get_container_app(ref=snapshot.resource_ref, target=target)
            last_snapshot = self._snapshot_from_app(
                ref=snapshot.resource_ref,
                app=app,
                target=target,
            )
            ready_revision = last_snapshot.latest_ready_revision_name
            latest_revision = last_snapshot.latest_revision_name
            if ready_revision and (latest_revision is None or ready_revision == latest_revision):
                health_url = build_health_url(last_snapshot=last_snapshot, release=release)
                status_code = self._probe_health(health_url=health_url)
                return AzureVerifyResult(
                    health_url=health_url,
                    status_code=status_code,
                    ready_revision=ready_revision,
                )
            time.sleep(self._verify_poll_interval_seconds)

        raise AzureExecutionError(
            code="azure_revision_not_ready",
            message="Timed out waiting for Container App revision readiness.",
            details={
                "target_id": target.id,
                "resource_group_name": snapshot.resource_ref.resource_group_name,
                "container_app_name": snapshot.resource_ref.container_app_name,
                "latest_revision_name": last_snapshot.latest_revision_name,
                "latest_ready_revision_name": last_snapshot.latest_ready_revision_name,
                "timeout_seconds": self._verify_timeout_seconds,
            },
        )

    def _client(
        self,
        *,
        subscription_id: str,
        tenant_hint: str | None = None,
    ) -> Any:
        existing = self._clients_by_subscription.get(subscription_id)
        if existing is not None:
            cached_tenant = self._tenant_by_client_subscription.get(subscription_id)
            requested_tenant = resolve_tenant_for_subscription(
                subscription_id=subscription_id,
                target_tenant_hint=tenant_hint,
                default_tenant_id=self._default_tenant_id,
                tenant_by_subscription=self._tenant_by_subscription,
            )
            if requested_tenant and cached_tenant and requested_tenant != cached_tenant:
                raise AzureExecutionError(
                    code="azure_subscription_tenant_conflict",
                    message=(
                        "Subscription tenant authority changed during runtime."
                    ),
                    details={
                        "subscription_id": subscription_id,
                        "cached_tenant_id": cached_tenant,
                        "requested_tenant_id": requested_tenant,
                    },
                )
            return existing

        tenant_id = resolve_tenant_for_subscription(
            subscription_id=subscription_id,
            target_tenant_hint=tenant_hint,
            default_tenant_id=self._default_tenant_id,
            tenant_by_subscription=self._tenant_by_subscription,
        )
        if tenant_id is None:
            raise AzureExecutionError(
                code="azure_tenant_unresolved",
                message=(
                    "No tenant authority found for subscription. "
                    "Set MAPPO_AZURE_TENANT_BY_SUBSCRIPTION or provide valid target tenant IDs."
                ),
                details={"subscription_id": subscription_id},
            )

        credential = self._credential_for_tenant(tenant_id=tenant_id)
        client = self._container_apps_client_type(
            credential=credential,
            subscription_id=subscription_id,
        )
        self._clients_by_subscription[subscription_id] = client
        self._tenant_by_client_subscription[subscription_id] = tenant_id
        return client

    def _credential_for_tenant(self, *, tenant_id: str) -> Any:
        existing = self._credentials_by_tenant.get(tenant_id)
        if existing is not None:
            return existing
        credential = self._client_secret_credential_type(
            tenant_id=tenant_id,
            client_id=self._client_id,
            client_secret=self._client_secret,
        )
        self._credentials_by_tenant[tenant_id] = credential
        return credential

    def _get_container_app(
        self,
        *,
        ref: ContainerAppResourceRef,
        target: Target,
    ) -> Any:
        client = self._client(
            subscription_id=ref.subscription_id,
            tenant_hint=target.tenant_id,
        )
        try:
            return self._with_retries(
                operation_name="container-app-get",
                operation=lambda: client.container_apps.get(
                    resource_group_name=ref.resource_group_name,
                    container_app_name=ref.container_app_name,
                ),
            )
        except Exception as error:
            raise self._translate_exception(
                error=error,
                code="azure_validation_failed",
                message="Unable to read Azure Container App state.",
                details={
                    "target_id": target.id,
                    "resource_group_name": ref.resource_group_name,
                    "container_app_name": ref.container_app_name,
                },
            ) from error

    @staticmethod
    def _snapshot_from_app(
        *,
        ref: ContainerAppResourceRef,
        app: Any,
        target: Target,
    ) -> AzureContainerAppSnapshot:
        template = getattr(app, "template", None)
        containers = list(getattr(template, "containers", []) or [])
        if not containers:
            raise AzureExecutionError(
                code="azure_container_template_missing",
                message="Container App template has no containers configured.",
                details={
                    "target_id": target.id,
                    "resource_group_name": ref.resource_group_name,
                    "container_app_name": ref.container_app_name,
                },
            )
        current_image = getattr(containers[0], "image", "") or ""
        return AzureContainerAppSnapshot(
            resource_ref=ref,
            current_image=current_image,
            latest_revision_name=getattr(app, "latest_revision_name", None),
            latest_ready_revision_name=getattr(app, "latest_ready_revision_name", None),
            latest_revision_fqdn=getattr(app, "latest_revision_fqdn", None),
            raw_app=app,
        )

    def _build_update_payload(
        self,
        app: Any,
        *,
        desired_image: str,
        desired_feature_flag: str | None = None,
    ) -> Any:
        location = getattr(app, "location", None)
        if not isinstance(location, str) or location.strip() == "":
            raise AzureExecutionError(
                code="azure_update_payload_invalid",
                message="Container App update payload is missing a valid location.",
            )

        template = copy.deepcopy(getattr(app, "template", None))
        containers = list(getattr(template, "containers", []) or [])
        if not containers:
            raise AzureExecutionError(
                code="azure_update_payload_invalid",
                message="Container App update payload has no containers.",
            )
        containers[0].image = desired_image
        if desired_feature_flag is not None:
            set_container_env_value(
                container=containers[0],
                env_name="MAPPO_FEATURE_FLAG",
                env_value=desired_feature_flag,
            )

        return self._container_app_model_type(
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

    def _probe_health(self, *, health_url: str) -> int:
        request = urllib.request.Request(
            url=health_url,
            method="GET",
            headers={"User-Agent": "mappo-health-check/1.0"},
        )
        try:
            with urllib.request.urlopen(request, timeout=self._health_timeout_seconds) as response:
                status_code = int(getattr(response, "status", response.getcode()))
        except urllib.error.HTTPError as error:
            raise AzureExecutionError(
                code="azure_health_check_failed",
                message=f"Health check failed with HTTP {error.code}.",
                details={"health_url": health_url, "status_code": error.code},
            ) from error
        except urllib.error.URLError as error:
            raise AzureExecutionError(
                code="azure_health_check_failed",
                message="Health check request failed to reach the target.",
                details={"health_url": health_url, "reason": str(error.reason)},
            ) from error

        if status_code < 200 or status_code >= 400:
            raise AzureExecutionError(
                code="azure_health_check_failed",
                message=f"Health check returned unexpected status {status_code}.",
                details={"health_url": health_url, "status_code": status_code},
            )
        return status_code

    def _with_retries(
        self,
        *,
        operation_name: str,
        operation: Callable[[], Any],
    ) -> Any:
        attempt = 0
        while True:
            try:
                return operation()
            except Exception as error:
                if not is_retryable_exception(error):
                    raise
                attempt += 1
                if attempt >= self._max_retry_attempts:
                    raise
                delay = compute_retry_delay_seconds(
                    error=error,
                    attempt=attempt,
                    base_delay_seconds=self._retry_base_delay_seconds,
                    max_delay_seconds=self._retry_max_delay_seconds,
                    jitter_seconds=self._retry_jitter_seconds,
                )
                print(
                    "azure-retry: "
                    f"operation={operation_name} attempt={attempt} "
                    f"delay={delay:.2f}s error={stringify_exception(error)}"
                )
                time.sleep(delay)

    @staticmethod
    def _translate_exception(
        *,
        error: Exception,
        code: str,
        message: str,
        details: dict[str, Any] | None = None,
    ) -> AzureExecutionError:
        from azure.core.exceptions import (
            ClientAuthenticationError,
            HttpResponseError,
            ResourceNotFoundError,
            ServiceRequestError,
        )

        if isinstance(error, AzureExecutionError):
            return error
        if isinstance(error, ClientAuthenticationError):
            return AzureExecutionError(
                code="azure_authentication_failed",
                message="Azure authentication failed for the configured service principal.",
                details={**(details or {}), "error": str(error)},
            )
        if isinstance(error, ResourceNotFoundError):
            return AzureExecutionError(
                code="azure_target_not_found",
                message="Azure target Container App was not found.",
                details={**(details or {}), "error": str(error)},
            )
        if isinstance(error, ServiceRequestError):
            return AzureExecutionError(
                code=code,
                message="Azure API request failed due to transport/network issues.",
                details={**(details or {}), "error": str(error)},
            )
        if isinstance(error, HttpResponseError):
            return AzureExecutionError(
                code=code,
                message=message,
                details={
                    **(details or {}),
                    "error": str(error),
                    "status_code": getattr(error, "status_code", None),
                },
            )
        return AzureExecutionError(
            code=code,
            message=message,
            details={**(details or {}), "error": str(error)},
        )


def create_azure_runtime(settings: AzureExecutorSettings) -> AzureRuntime:
    return AzureSdkRuntime(settings=settings)


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


def parse_container_app_resource_id(managed_app_id: str) -> ContainerAppResourceRef:
    parts = [part for part in managed_app_id.strip("/").split("/") if part]
    if len(parts) != 8:
        raise AzureExecutionError(
            code="azure_managed_app_id_invalid",
            message="Managed app resource ID is not a valid Container App resource path.",
            details={"managed_app_id": managed_app_id},
        )

    if parts[0].lower() != "subscriptions" or parts[2].lower() != "resourcegroups":
        raise AzureExecutionError(
            code="azure_managed_app_id_invalid",
            message="Managed app resource ID is missing subscription/resource-group segments.",
            details={"managed_app_id": managed_app_id},
        )
    if parts[4].lower() != "providers" or parts[5].lower() != "microsoft.app":
        raise AzureExecutionError(
            code="azure_managed_app_id_invalid",
            message="Managed app resource ID provider must be Microsoft.App.",
            details={"managed_app_id": managed_app_id},
        )
    if parts[6].lower() != "containerapps":
        raise AzureExecutionError(
            code="azure_managed_app_id_invalid",
            message="Managed app resource ID type must be containerApps.",
            details={"managed_app_id": managed_app_id},
        )

    return ContainerAppResourceRef(
        subscription_id=parts[1],
        resource_group_name=parts[3],
        container_app_name=parts[7],
    )


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


def build_health_url(
    *,
    last_snapshot: AzureContainerAppSnapshot,
    release: Release,
) -> str:
    health_path = (
        release.parameter_defaults.get("healthUrl")
        or release.parameter_defaults.get("health_url")
        or release.parameter_defaults.get("healthPath")
        or release.parameter_defaults.get("health_path")
        or "/"
    )
    normalized = health_path.strip()
    if normalized.startswith("http://") or normalized.startswith("https://"):
        return normalized

    fqdn = (last_snapshot.latest_revision_fqdn or "").strip()
    if not fqdn:
        raise AzureExecutionError(
            code="azure_fqdn_missing",
            message="Container App revision FQDN is unavailable for health verification.",
            details={
                "resource_group_name": last_snapshot.resource_ref.resource_group_name,
                "container_app_name": last_snapshot.resource_ref.container_app_name,
            },
        )
    path = normalized if normalized.startswith("/") else f"/{normalized}"
    return f"https://{fqdn}{path}"


def build_correlation_id(
    run_id: str,
    target_id: str,
    attempt: int,
    stage: TargetStage,
) -> str:
    key = f"{run_id}:{target_id}:{attempt}:{stage.value}"
    digest = hashlib.sha256(key.encode()).hexdigest()
    return f"corr-{digest[:16]}"
