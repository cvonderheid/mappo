from __future__ import annotations

import json
import re
from collections.abc import Callable
from typing import Any

from app.modules.execution_utils import (
    dedupe_strings_in_order,
    resolve_desired_image,
    to_non_empty_string,
)
from app.modules.schemas import DeploymentMode, DeploymentScope, Release


def deploy_template_spec_release(
    *,
    run_id: str,
    target_id: str,
    target_tenant_id: str,
    release: Release,
    snapshot: Any,
    attempt: int,
    verify_timeout_seconds: float,
    create_resource_client: Callable[[str, str | None], Any],
    with_retries: Callable[..., Any],
    translate_exception: Callable[..., Exception],
    get_container_app: Callable[[Any, Any], Any],
    snapshot_from_app: Callable[[Any, Any, Any], Any],
    azure_error_code: str,
    azure_error_message: str,
) -> tuple[Any, str, bool, dict[str, Any]]:
    template_spec_version_id = resolve_template_spec_version_id(release=release)
    deployment_scope = release.deployment_scope
    arm_mode = resolve_arm_mode(release=release)
    deployment_name = build_template_spec_deployment_name(
        run_id=run_id,
        target_id=target_id,
        attempt=attempt,
    )

    parameters_payload = build_template_spec_parameters_payload(release=release)
    deployment_properties: dict[str, Any] = {
        "mode": arm_mode,
        "templateLink": {"id": template_spec_version_id},
    }
    if parameters_payload:
        deployment_properties["parameters"] = parameters_payload
    deployment_payload: dict[str, Any] = {"properties": deployment_properties}

    resource_ref = snapshot.resource_ref
    resource_group_name = resource_ref.resource_group_name
    resource_client = create_resource_client(resource_ref.subscription_id, target_tenant_id)
    deployment_result: Any | None = None
    deployment_error: Exception | None = None
    try:
        if deployment_scope == DeploymentScope.RESOURCE_GROUP:
            poller = with_retries(
                operation_name="template-spec-begin-rg-deploy",
                operation=lambda: resource_client.deployments.begin_create_or_update(
                    resource_group_name=resource_group_name,
                    deployment_name=deployment_name,
                    parameters=deployment_payload,
                ),
            )
        else:

            def _begin_subscription_deployment() -> Any:
                return resource_client.deployments.begin_create_or_update_at_subscription_scope(
                    deployment_name=deployment_name,
                    parameters=deployment_payload,
                )

            poller = with_retries(
                operation_name="template-spec-begin-subscription-deploy",
                operation=_begin_subscription_deployment,
            )
        deployment_result = with_retries(
            operation_name="template-spec-deploy-result",
            operation=lambda: poller.result(timeout=verify_timeout_seconds),
        )
    except TypeError:
        deployment_result = poller.result()
    except Exception as error:
        deployment_error = error

    operation_summaries = collect_deployment_operation_summaries(
        resource_client=resource_client,
        deployment_scope=deployment_scope,
        resource_group_name=resource_group_name,
        deployment_name=deployment_name,
        with_retries=with_retries,
        failed_only=deployment_error is not None,
    )
    if deployment_error is not None:
        translated = translate_exception(
            error=deployment_error,
            code=azure_error_code,
            message=azure_error_message,
            details={
                "target_id": target_id,
                "resource_group_name": resource_group_name,
                "deployment_name": deployment_name,
                "deployment_scope": deployment_scope.value,
                "template_spec_version_id": template_spec_version_id,
                "azure_operation_summaries": operation_summaries,
            },
        )
        raise translated from deployment_error

    if deployment_result is None:
        raise RuntimeError("Template Spec deployment finished without a result payload.")

    refreshed_app = get_container_app(resource_ref, target_tenant_id)
    updated_snapshot = snapshot_from_app(resource_ref, refreshed_app, target_id)
    desired_image = resolve_desired_image(
        current_image=snapshot.current_image,
        parameter_defaults=release.parameter_defaults,
    )
    deployment_id = to_non_empty_string(getattr(deployment_result, "id", None))
    changed = updated_snapshot.current_image != snapshot.current_image
    metadata = {
        "deployment_name": deployment_name,
        "deployment_id": deployment_id,
        "deployment_scope": deployment_scope.value,
        "template_spec_version_id": template_spec_version_id,
        "operation_summaries": operation_summaries,
    }
    return updated_snapshot, desired_image, changed, metadata


def should_verify_after_deploy(*, release: Release) -> bool:
    raw = release.deployment_mode_settings.get("verify_after_deploy")
    if isinstance(raw, bool):
        return raw
    if isinstance(raw, str):
        normalized = raw.strip().lower()
        if normalized in {"false", "0", "no", "off"}:
            return False
        if normalized in {"true", "1", "yes", "on"}:
            return True
    return True


def build_deploy_completion_message(
    *,
    deployment_mode: DeploymentMode,
    desired_image: str,
    changed: bool,
    metadata: dict[str, Any],
) -> str:
    if deployment_mode == DeploymentMode.TEMPLATE_SPEC:
        deployment_name = to_non_empty_string(metadata.get("deployment_name"))
        scope_value = to_non_empty_string(metadata.get("deployment_scope"))
        template_spec_version_id = to_non_empty_string(metadata.get("template_spec_version_id"))
        scope_prefix = f"{scope_value} " if scope_value is not None else ""
        summary = (
            f"Template Spec deployment completed; {scope_prefix}deployment "
            f"{deployment_name or 'unknown'}."
        )
        if template_spec_version_id is not None:
            summary += f" templateSpecVersionId={template_spec_version_id}."
        if changed:
            summary += f" image now {desired_image}."
        else:
            summary += f" image unchanged ({desired_image})."
        return summary

    if changed:
        return f"Deployment completed; image now {desired_image}."
    return f"No image change requested; keeping {desired_image}."


def resolve_template_spec_version_id(*, release: Release) -> str:
    explicit_version_id = to_non_empty_string(release.template_spec_version_id)
    if explicit_version_id is not None:
        return explicit_version_id.rstrip("/")

    template_spec_id = to_non_empty_string(release.template_spec_id)
    template_spec_version = to_non_empty_string(release.template_spec_version)
    if template_spec_id is None or template_spec_version is None:
        raise ValueError(
            "Template Spec deployment requires template_spec_id + template_spec_version."
        )
    return f"{template_spec_id.rstrip('/')}/versions/{template_spec_version}"


def resolve_arm_mode(*, release: Release) -> str:
    configured_mode = release.deployment_mode_settings.get("arm_mode")
    if isinstance(configured_mode, str):
        normalized = configured_mode.strip().lower()
        if normalized == "incremental":
            return "Incremental"
        if normalized == "complete":
            return "Complete"
    return "Incremental"


def build_template_spec_parameters_payload(*, release: Release) -> dict[str, dict[str, Any]]:
    merged_parameters: dict[str, Any] = {}
    for key, value in release.parameter_defaults.items():
        normalized_key = key.strip()
        if normalized_key == "":
            continue
        merged_parameters[normalized_key] = value

    settings_parameters = release.deployment_mode_settings.get("parameters")
    if isinstance(settings_parameters, dict):
        for key, value in settings_parameters.items():
            if not isinstance(key, str):
                continue
            normalized_key = key.strip()
            if normalized_key == "":
                continue
            merged_parameters[normalized_key] = value

    return {key: {"value": value} for key, value in merged_parameters.items()}


def build_template_spec_deployment_name(
    *,
    run_id: str,
    target_id: str,
    attempt: int,
) -> str:
    def _sanitize(value: str) -> str:
        return re.sub(r"[^a-z0-9-]+", "-", value.lower()).strip("-")

    run_token = _sanitize(run_id).replace("run-", "", 1)[:12] or "run"
    target_token = _sanitize(target_id)[:20] or "target"
    base_name = f"mappo-{run_token}-{target_token}-a{max(1, attempt)}"
    if len(base_name) <= 64:
        return base_name
    return base_name[:64]


def collect_deployment_operation_summaries(
    *,
    resource_client: Any,
    deployment_scope: DeploymentScope,
    resource_group_name: str,
    deployment_name: str,
    with_retries: Callable[..., Any],
    failed_only: bool,
) -> list[str]:
    try:
        if deployment_scope == DeploymentScope.RESOURCE_GROUP:
            operations_page = with_retries(
                operation_name="template-spec-deployment-operations-list-rg",
                operation=lambda: resource_client.deployment_operations.list(
                    resource_group_name=resource_group_name,
                    deployment_name=deployment_name,
                ),
            )
        else:
            list_at_subscription_scope = getattr(
                resource_client.deployment_operations,
                "list_at_subscription_scope",
                None,
            )
            if not callable(list_at_subscription_scope):
                return []
            operations_page = with_retries(
                operation_name="template-spec-deployment-operations-list-subscription",
                operation=lambda: list_at_subscription_scope(
                    deployment_name=deployment_name,
                ),
            )
        raw_operations = list(operations_page)
    except Exception:
        return []

    summaries: list[str] = []
    for operation in raw_operations:
        summary = summarize_deployment_operation(operation=operation, failed_only=failed_only)
        if summary is not None:
            summaries.append(summary)
    return dedupe_strings_in_order(summaries)[:6]


def summarize_deployment_operation(
    *,
    operation: Any,
    failed_only: bool,
) -> str | None:
    properties = getattr(operation, "properties", None)
    if properties is None:
        return None

    provisioning_state = to_non_empty_string(getattr(properties, "provisioning_state", None))
    status_code = to_non_empty_string(getattr(properties, "status_code", None))
    if failed_only and provisioning_state is not None:
        normalized_state = provisioning_state.lower()
        if normalized_state not in {"failed", "canceled", "cancelled"}:
            return None

    target_resource = getattr(properties, "target_resource", None)
    target_type = to_non_empty_string(getattr(target_resource, "resource_type", None))
    target_name = to_non_empty_string(getattr(target_resource, "resource_name", None))
    target_segment = "unknown-resource"
    if target_type is not None and target_name is not None:
        target_segment = f"{target_type}/{target_name}"
    elif target_name is not None:
        target_segment = target_name
    elif target_type is not None:
        target_segment = target_type

    status_message = getattr(properties, "status_message", None)
    message_text: str | None = None
    if isinstance(status_message, dict):
        message_text = to_non_empty_string(status_message.get("message"))
        if message_text is None:
            message_text = to_non_empty_string(json.dumps(status_message))
    else:
        message_text = to_non_empty_string(status_message)

    summary_prefix = "ARM operation"
    if provisioning_state is not None:
        summary_prefix += f" [{provisioning_state}]"
    summary = f"{summary_prefix} {target_segment}"
    if status_code is not None:
        summary += f" ({status_code})"
    if message_text is not None:
        summary += f": {message_text}"
    return summary
