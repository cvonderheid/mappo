from __future__ import annotations

from datetime import datetime
from enum import StrEnum
from typing import Any

from pydantic import BaseModel, Field


class TargetStage(StrEnum):
    QUEUED = "QUEUED"
    VALIDATING = "VALIDATING"
    DEPLOYING = "DEPLOYING"
    VERIFYING = "VERIFYING"
    SUCCEEDED = "SUCCEEDED"
    FAILED = "FAILED"


class RunStatus(StrEnum):
    RUNNING = "running"
    SUCCEEDED = "succeeded"
    FAILED = "failed"
    PARTIAL = "partial"
    HALTED = "halted"


class StrategyMode(StrEnum):
    ALL_AT_ONCE = "all_at_once"
    WAVES = "waves"


class StructuredError(BaseModel):
    code: str
    message: str
    details: dict[str, Any] | None = None


class TargetLogEvent(BaseModel):
    timestamp: datetime
    level: str
    stage: TargetStage
    message: str
    correlation_id: str


class TargetStageRecord(BaseModel):
    stage: TargetStage
    started_at: datetime
    ended_at: datetime | None = None
    message: str = ""
    error: StructuredError | None = None
    correlation_id: str
    portal_link: str


class TargetExecutionRecord(BaseModel):
    target_id: str
    subscription_id: str
    tenant_id: str
    status: TargetStage = TargetStage.QUEUED
    attempt: int = 0
    updated_at: datetime
    stages: list[TargetStageRecord] = Field(default_factory=list)
    logs: list[TargetLogEvent] = Field(default_factory=list)


class StopPolicy(BaseModel):
    max_failure_count: int | None = Field(default=None, ge=1)
    max_failure_rate: float | None = Field(default=None, ge=0.0, le=1.0)


class Target(BaseModel):
    id: str
    tenant_id: str
    subscription_id: str
    managed_app_id: str
    tags: dict[str, str]
    last_deployed_release: str
    health_status: str
    last_check_in_at: datetime
    simulated_failure_mode: str = "none"


class Release(BaseModel):
    id: str
    template_spec_id: str
    template_spec_version: str
    parameter_defaults: dict[str, str]
    release_notes: str
    verification_hints: list[str] = Field(default_factory=list)
    created_at: datetime


class DeploymentRun(BaseModel):
    id: str
    release_id: str
    strategy_mode: StrategyMode
    wave_tag: str
    wave_order: list[str]
    concurrency: int
    subscription_concurrency: int = 1
    stop_policy: StopPolicy
    target_ids: list[str]
    status: RunStatus
    halt_reason: str | None = None
    guardrail_warnings: list[str] = Field(default_factory=list)
    created_at: datetime
    started_at: datetime | None = None
    ended_at: datetime | None = None
    updated_at: datetime
    target_records: dict[str, TargetExecutionRecord]


class CreateReleaseRequest(BaseModel):
    template_spec_id: str
    template_spec_version: str
    parameter_defaults: dict[str, str] = Field(default_factory=dict)
    release_notes: str = ""
    verification_hints: list[str] = Field(default_factory=list)


class CreateRunRequest(BaseModel):
    release_id: str
    strategy_mode: StrategyMode = StrategyMode.ALL_AT_ONCE
    wave_tag: str = "ring"
    wave_order: list[str] = Field(default_factory=lambda: ["canary", "prod"])
    concurrency: int = Field(default=3, ge=1, le=25)
    stop_policy: StopPolicy = Field(default_factory=StopPolicy)
    target_ids: list[str] | None = None
    target_tags: dict[str, str] = Field(default_factory=dict)


class MarketplaceEventStatus(StrEnum):
    APPLIED = "applied"
    DUPLICATE = "duplicate"
    REJECTED = "rejected"


class MarketplaceEventIngestRequest(BaseModel):
    event_id: str
    event_type: str = "subscription_purchased"
    event_time: datetime | None = None
    tenant_id: str
    subscription_id: str
    managed_application_id: str | None = None
    managed_resource_group_id: str | None = None
    container_app_resource_id: str | None = None
    container_app_name: str | None = None
    target_id: str | None = None
    display_name: str | None = None
    customer_name: str | None = None
    target_group: str = "prod"
    region: str | None = None
    environment: str = "prod"
    tier: str = "standard"
    tags: dict[str, str] = Field(default_factory=dict)
    metadata: dict[str, Any] = Field(default_factory=dict)
    health_status: str = "registered"
    last_deployed_release: str = "unknown"


class MarketplaceEventIngestResponse(BaseModel):
    event_id: str
    status: MarketplaceEventStatus
    message: str
    target_id: str | None = None


class TargetRegistrationRecord(BaseModel):
    target_id: str
    tenant_id: str
    subscription_id: str
    managed_application_id: str | None = None
    managed_resource_group_id: str
    container_app_resource_id: str
    display_name: str
    customer_name: str | None = None
    tags: dict[str, str] = Field(default_factory=dict)
    metadata: dict[str, Any] = Field(default_factory=dict)
    last_event_id: str | None = None
    created_at: datetime
    updated_at: datetime


class UpdateTargetRegistrationRequest(BaseModel):
    display_name: str | None = None
    customer_name: str | None = None
    managed_application_id: str | None = None
    managed_resource_group_id: str | None = None
    container_app_resource_id: str | None = None
    target_group: str | None = None
    region: str | None = None
    environment: str | None = None
    tier: str | None = None
    tags: dict[str, str] | None = None
    metadata: dict[str, Any] | None = None
    health_status: str | None = None
    last_deployed_release: str | None = None


class DeleteTargetRegistrationResponse(BaseModel):
    target_id: str
    deleted: bool


class MarketplaceEventRecord(BaseModel):
    event_id: str
    event_type: str
    status: MarketplaceEventStatus
    message: str
    target_id: str | None = None
    tenant_id: str
    subscription_id: str
    payload: dict[str, Any] = Field(default_factory=dict)
    created_at: datetime
    processed_at: datetime | None = None


class AdminOnboardingSnapshotResponse(BaseModel):
    registrations: list[TargetRegistrationRecord] = Field(default_factory=list)
    events: list[MarketplaceEventRecord] = Field(default_factory=list)


class RunSummary(BaseModel):
    id: str
    release_id: str
    status: RunStatus
    strategy_mode: StrategyMode
    created_at: datetime
    started_at: datetime | None
    ended_at: datetime | None
    subscription_concurrency: int = 1
    total_targets: int
    succeeded_targets: int
    failed_targets: int
    in_progress_targets: int
    queued_targets: int
    halt_reason: str | None = None
    guardrail_warnings: list[str] = Field(default_factory=list)


class RunDetail(BaseModel):
    id: str
    release_id: str
    status: RunStatus
    strategy_mode: StrategyMode
    wave_tag: str
    wave_order: list[str]
    concurrency: int
    subscription_concurrency: int = 1
    stop_policy: StopPolicy
    created_at: datetime
    started_at: datetime | None
    ended_at: datetime | None
    updated_at: datetime
    halt_reason: str | None = None
    guardrail_warnings: list[str] = Field(default_factory=list)
    target_records: list[TargetExecutionRecord]


class HealthResponse(BaseModel):
    status: str
