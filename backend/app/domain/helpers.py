from __future__ import annotations

from uuid import UUID

from app.modules.schemas import (
    DeploymentRun,
    StructuredError,
    Target,
    TargetExecutionRecord,
    TargetRegistrationRecord,
    TargetStage,
    TargetStageRecord,
)


def find_open_stage_record(
    record: TargetExecutionRecord,
    stage: TargetStage,
) -> TargetStageRecord | None:
    for stage_record in reversed(record.stages):
        if stage_record.stage == stage and stage_record.ended_at is None:
            return stage_record
    return None


def build_error_log_lines(error: StructuredError) -> list[str]:
    details = error.details if isinstance(error.details, dict) else {}
    if not details:
        return []

    lines: list[str] = []
    azure_error_code = to_text(details.get("azure_error_code"))
    azure_error_message = to_text(details.get("azure_error_message"))
    if azure_error_code and azure_error_message:
        lines.append(f"Azure error [{azure_error_code}]: {azure_error_message}")
    elif azure_error_message:
        lines.append(f"Azure error: {azure_error_message}")
    elif azure_error_code:
        lines.append(f"Azure error code: {azure_error_code}")

    status_code = details.get("status_code")
    if isinstance(status_code, int):
        lines.append(f"Azure HTTP status: {status_code}")

    request_id = (
        to_text(details.get("azure_request_id"))
        or to_text(details.get("azure_arm_service_request_id"))
    )
    if request_id:
        lines.append(f"Azure request id: {request_id}")

    correlation_id = to_text(details.get("azure_correlation_id"))
    if correlation_id:
        lines.append(f"Azure correlation id: {correlation_id}")

    operation_id = to_text(details.get("azure_operation_id"))
    if operation_id:
        lines.append(f"Azure operation id: {operation_id}")

    deployment_name = to_text(details.get("deployment_name"))
    if deployment_name:
        lines.append(f"ARM deployment: {deployment_name}")

    deployment_scope = to_text(details.get("deployment_scope"))
    if deployment_scope:
        lines.append(f"ARM deployment scope: {deployment_scope}")

    template_spec_version_id = to_text(details.get("template_spec_version_id"))
    if template_spec_version_id:
        lines.append(f"Template Spec version id: {template_spec_version_id}")

    deployment_summaries = details.get("azure_operation_summaries")
    if isinstance(deployment_summaries, list):
        for entry in deployment_summaries[:5]:
            summary = to_text(entry)
            if summary:
                lines.append(summary)

    detail_entries = details.get("azure_error_details")
    if isinstance(detail_entries, list):
        for entry in detail_entries[:3]:
            if not isinstance(entry, dict):
                continue
            entry_code = to_text(entry.get("code"))
            entry_message = to_text(entry.get("message"))
            if entry_code and entry_message:
                lines.append(f"Azure detail [{entry_code}]: {entry_message}")
            elif entry_message:
                lines.append(f"Azure detail: {entry_message}")

    normalized: list[str] = []
    seen: set[str] = set()
    for line in lines:
        if line in seen:
            continue
        seen.add(line)
        normalized.append(line)
    return normalized


def to_text(value: object) -> str | None:
    if value is None:
        return None
    normalized = str(value).strip()
    if normalized == "":
        return None
    return normalized


def matches_tags(target_tags: dict[str, str], requested_tags: dict[str, str]) -> bool:
    for key, value in requested_tags.items():
        if target_tags.get(key) != value:
            return False
    return True


def count_targets(
    run: DeploymentRun,
    *,
    in_progress_states: set[TargetStage],
) -> dict[str, int]:
    counts = {"queued": 0, "in_progress": 0, "succeeded": 0, "failed": 0}
    for record in run.target_records.values():
        if record.status == TargetStage.QUEUED:
            counts["queued"] += 1
        elif record.status in in_progress_states:
            counts["in_progress"] += 1
        elif record.status == TargetStage.SUCCEEDED:
            counts["succeeded"] += 1
        elif record.status == TargetStage.FAILED:
            counts["failed"] += 1
    return counts


def is_guid(value: str) -> bool:
    try:
        UUID(value)
    except ValueError:
        return False
    return True


def project_registration_from_target(
    registration: TargetRegistrationRecord,
    target: Target | None,
) -> TargetRegistrationRecord:
    projected = registration.model_copy(deep=True)
    if target is None:
        return projected
    projected.tenant_id = target.tenant_id
    projected.subscription_id = target.subscription_id
    projected.container_app_resource_id = target.managed_app_id
    projected.customer_name = target.customer_name
    projected.tags = dict(target.tags)
    return projected
