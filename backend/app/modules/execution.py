from __future__ import annotations

import asyncio
import time
from collections.abc import AsyncIterator, Callable
from dataclasses import dataclass, field
from enum import StrEnum
from typing import Any, Literal, Protocol

from app.modules.azure_runtime_helpers import (
    build_container_app_update_payload,
    parse_container_app_resource_id_parts,
    probe_health_url,
    translate_azure_exception,
)
from app.modules.azure_runtime_helpers import (
    build_health_url as build_health_url_from_helpers,
)
from app.modules.execution_utils import (
    build_correlation_id,
    compute_retry_delay_seconds,
    dedupe_strings_in_order,
    is_retryable_exception,
    normalize_location,
    parse_usage_item,
    read_container_env_value,
    resolve_desired_data_model_version,
    resolve_desired_feature_flag,
    resolve_desired_image,
    resolve_desired_software_version,
    stringify_exception,
)
from app.modules.execution_utils import (
    normalize_tenant_hint as normalize_tenant_hint,
)
from app.modules.execution_utils import (
    resolve_tenant_for_subscription as resolve_tenant_for_subscription,
)
from app.modules.schemas import (
    DeploymentMode,
    DeploymentRun,
    Release,
    StructuredError,
    Target,
    TargetStage,
)
from app.modules.template_spec_runtime import (
    build_deploy_completion_message,
    deploy_template_spec_release,
    should_verify_after_deploy,
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
    metadata: dict[str, Any] = field(default_factory=dict)


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
        run: DeploymentRun,
        target: Target,
        release: Release,
        snapshot: AzureContainerAppSnapshot,
        attempt: int,
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
            message="Finalizing success state.",
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
                run=run,
                target=target,
                release=release,
                snapshot=snapshot,
                attempt=attempt,
            )
        except AzureExecutionError as error:
            yield self._failed_event(
                correlation_id=deploying_correlation,
                stage=TargetStage.DEPLOYING,
                error=error,
            )
            return

        deployment_message = build_deploy_completion_message(
            deployment_mode=release.deployment_mode,
            desired_image=deploy_result.desired_image,
            changed=deploy_result.changed,
            metadata=deploy_result.metadata,
        )
        yield TargetExecutionEvent(
            event_type="completed",
            stage=TargetStage.DEPLOYING,
            correlation_id=deploying_correlation,
            message=deployment_message,
        )

        if not should_verify_after_deploy(release=release):
            skipped_verify_correlation = build_correlation_id(
                run.id,
                target.id,
                attempt,
                TargetStage.VERIFYING,
            )
            yield TargetExecutionEvent(
                event_type="started",
                stage=TargetStage.VERIFYING,
                correlation_id=skipped_verify_correlation,
                message="Verifying started.",
            )
            yield TargetExecutionEvent(
                event_type="completed",
                stage=TargetStage.VERIFYING,
                correlation_id=skipped_verify_correlation,
                message="Verification skipped by release settings.",
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
                message="Finalizing success state.",
            )
            yield TargetExecutionEvent(
                event_type="completed",
                stage=TargetStage.SUCCEEDED,
                correlation_id=success_correlation,
                message="Target deployment succeeded.",
                terminal_state=TargetStage.SUCCEEDED,
            )
            return

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
            message="Finalizing success state.",
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
            from azure.mgmt.resource import ResourceManagementClient
        except Exception as error:  # pragma: no cover - exercised by integration only
            raise AzureExecutionError(
                code="azure_sdk_not_available",
                message=(
                    "Azure SDK dependencies are unavailable. Install azure-identity and "
                    "azure-mgmt-appcontainers plus azure-mgmt-resource."
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
        self._resource_client_type = ResourceManagementClient
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
        snapshot = self._snapshot_from_app(ref=ref, app=app, target_id=target.id)
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
        run: DeploymentRun,
        target: Target,
        release: Release,
        snapshot: AzureContainerAppSnapshot,
        attempt: int,
    ) -> AzureDeployResult:
        if release.deployment_mode == DeploymentMode.TEMPLATE_SPEC:
            return self._deploy_release_template_spec(
                run=run,
                target=target,
                release=release,
                snapshot=snapshot,
                attempt=attempt,
            )
        return self._deploy_release_container_patch(
            target=target,
            release=release,
            snapshot=snapshot,
        )

    def _deploy_release_container_patch(
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
        desired_data_model_version = resolve_desired_data_model_version(
            release.parameter_defaults
        )
        desired_software_version = resolve_desired_software_version(
            parameter_defaults=release.parameter_defaults,
            fallback_version=release.template_spec_version,
        )
        current_feature_flag = read_container_env_value(
            app=snapshot.raw_app,
            env_name="MAPPO_FEATURE_FLAG",
        )
        current_data_model_version = read_container_env_value(
            app=snapshot.raw_app,
            env_name="MAPPO_DATA_MODEL_VERSION",
        )
        if current_data_model_version is None:
            current_data_model_version = current_feature_flag
        current_software_version = read_container_env_value(
            app=snapshot.raw_app,
            env_name="MAPPO_SOFTWARE_VERSION",
        )
        feature_flag_changed = (
            desired_feature_flag is not None and desired_feature_flag != current_feature_flag
        )
        data_model_changed = (
            desired_data_model_version is not None
            and desired_data_model_version != current_data_model_version
        )
        software_version_changed = (
            desired_software_version is not None
            and desired_software_version != current_software_version
        )

        if (
            desired_image == snapshot.current_image
            and not feature_flag_changed
            and not data_model_changed
            and not software_version_changed
        ):
            return AzureDeployResult(
                snapshot=snapshot,
                desired_image=desired_image,
                changed=False,
            )

        app_to_update = self._build_update_payload(
            snapshot.raw_app,
            desired_image=desired_image,
            desired_feature_flag=desired_feature_flag,
            desired_data_model_version=desired_data_model_version,
            desired_software_version=desired_software_version,
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
                    "desired_data_model_version": desired_data_model_version,
                    "desired_software_version": desired_software_version,
                },
            ) from error

        updated_snapshot = self._snapshot_from_app(
            ref=snapshot.resource_ref,
            app=updated,
            target_id=target.id,
        )
        return AzureDeployResult(
            snapshot=updated_snapshot,
            desired_image=desired_image,
            changed=True,
        )

    def _deploy_release_template_spec(
        self,
        *,
        run: DeploymentRun,
        target: Target,
        release: Release,
        snapshot: AzureContainerAppSnapshot,
        attempt: int,
    ) -> AzureDeployResult:
        def _create_resource_client(subscription_id: str, tenant_hint: str | None) -> Any:
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
            return self._resource_client_type(
                credential=credential,
                subscription_id=subscription_id,
            )

        def _get_container_app_for_template_spec(
            ref: ContainerAppResourceRef,
            tenant_hint: str,
        ) -> Any:
            return self._get_container_app(
                ref=ref,
                tenant_hint=tenant_hint,
                target_id="template-spec-target",
            )

        def _snapshot_from_app_for_template_spec(
            ref: ContainerAppResourceRef,
            app: Any,
            target_id: str,
        ) -> AzureContainerAppSnapshot:
            return self._snapshot_from_app(ref=ref, app=app, target_id=target_id)

        try:
            (
                updated_snapshot,
                desired_image,
                changed,
                metadata,
            ) = deploy_template_spec_release(
                run_id=run.id,
                target_id=target.id,
                target_tenant_id=target.tenant_id,
                release=release,
                snapshot=snapshot,
                attempt=attempt,
                verify_timeout_seconds=self._verify_timeout_seconds,
                create_resource_client=_create_resource_client,
                with_retries=self._with_retries,
                translate_exception=self._translate_exception,
                get_container_app=_get_container_app_for_template_spec,
                snapshot_from_app=_snapshot_from_app_for_template_spec,
                azure_error_code="azure_template_spec_deploy_failed",
                azure_error_message="Template Spec deployment failed.",
            )
        except ValueError as error:
            raise AzureExecutionError(
                code="template_spec_reference_invalid",
                message=str(error),
                details={"target_id": target.id, "release_id": release.id},
            ) from error
        except RuntimeError as error:
            raise AzureExecutionError(
                code="azure_template_spec_deploy_failed",
                message=str(error),
                details={"target_id": target.id, "release_id": release.id},
            ) from error

        return AzureDeployResult(
            snapshot=updated_snapshot,
            desired_image=desired_image,
            changed=changed,
            metadata=metadata,
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
                target_id=target.id,
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
        target: Target | None = None,
        tenant_hint: str | None = None,
        target_id: str | None = None,
    ) -> Any:
        resolved_tenant_hint = tenant_hint
        if resolved_tenant_hint is None and target is not None:
            resolved_tenant_hint = target.tenant_id
        resolved_target_id = target_id
        if resolved_target_id is None and target is not None:
            resolved_target_id = target.id
        if resolved_target_id is None:
            resolved_target_id = "unknown-target"
        client = self._client(
            subscription_id=ref.subscription_id,
            tenant_hint=resolved_tenant_hint,
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
                    "target_id": resolved_target_id,
                    "resource_group_name": ref.resource_group_name,
                    "container_app_name": ref.container_app_name,
                },
            ) from error

    @staticmethod
    def _snapshot_from_app(
        *,
        ref: ContainerAppResourceRef,
        app: Any,
        target_id: str,
    ) -> AzureContainerAppSnapshot:
        template = getattr(app, "template", None)
        containers = list(getattr(template, "containers", []) or [])
        if not containers:
            raise AzureExecutionError(
                code="azure_container_template_missing",
                message="Container App template has no containers configured.",
                details={
                    "target_id": target_id,
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
        desired_data_model_version: str | None = None,
        desired_software_version: str | None = None,
    ) -> Any:
        return build_container_app_update_payload(
            app=app,
            desired_image=desired_image,
            desired_feature_flag=desired_feature_flag,
            desired_data_model_version=desired_data_model_version,
            desired_software_version=desired_software_version,
            container_app_model_type=self._container_app_model_type,
            error_factory=lambda code, message, details: AzureExecutionError(
                code=code,
                message=message,
                details=details,
            ),
        )

    def _probe_health(self, *, health_url: str) -> int:
        return probe_health_url(
            health_url=health_url,
            timeout_seconds=self._health_timeout_seconds,
            error_factory=lambda code, message, details: AzureExecutionError(
                code=code,
                message=message,
                details=details,
            ),
        )

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
        if isinstance(error, AzureExecutionError):
            return error
        translated = translate_azure_exception(
            error=error,
            code=code,
            message=message,
            details=details,
            error_factory=lambda error_code, error_message, error_details: AzureExecutionError(
                code=error_code,
                message=error_message,
                details=error_details,
            ),
        )
        if isinstance(translated, AzureExecutionError):
            return translated
        return AzureExecutionError(
            code=code,
            message=str(translated),
            details=details,
        )


def create_azure_runtime(settings: AzureExecutorSettings) -> AzureRuntime:
    return AzureSdkRuntime(settings=settings)


def parse_container_app_resource_id(managed_app_id: str) -> ContainerAppResourceRef:
    try:
        subscription_id, resource_group_name, container_app_name = (
            parse_container_app_resource_id_parts(managed_app_id)
        )
    except ValueError as error:
        raise AzureExecutionError(
            code="azure_managed_app_id_invalid",
            message=str(error),
            details={"managed_app_id": managed_app_id},
        ) from error
    return ContainerAppResourceRef(
        subscription_id=subscription_id,
        resource_group_name=resource_group_name,
        container_app_name=container_app_name,
    )


def build_health_url(
    *,
    last_snapshot: AzureContainerAppSnapshot,
    release: Release,
) -> str:
    try:
        return build_health_url_from_helpers(
            latest_revision_fqdn=last_snapshot.latest_revision_fqdn,
            parameter_defaults=release.parameter_defaults,
            resource_group_name=last_snapshot.resource_ref.resource_group_name,
            container_app_name=last_snapshot.resource_ref.container_app_name,
        )
    except ValueError as error:
        raise AzureExecutionError(
            code="azure_fqdn_missing",
            message=str(error),
            details={
                "resource_group_name": last_snapshot.resource_ref.resource_group_name,
                "container_app_name": last_snapshot.resource_ref.container_app_name,
            },
        ) from error
