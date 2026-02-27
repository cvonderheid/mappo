from __future__ import annotations

import asyncio
import hashlib
from collections.abc import AsyncIterator
from dataclasses import dataclass
from enum import StrEnum
from typing import Literal, Protocol

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


@dataclass(frozen=True)
class TargetExecutionEvent:
    event_type: ExecutionEventType
    stage: TargetStage
    correlation_id: str
    message: str
    error: StructuredError | None = None
    terminal_state: TargetStage | None = None


class TargetExecutor(Protocol):
    def execute_target(
        self,
        *,
        run: DeploymentRun,
        target: Target,
        release: Release,
        attempt: int,
    ) -> AsyncIterator[TargetExecutionEvent]:
        ...


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
    def __init__(self, *, azure_settings: AzureExecutorSettings):
        self._settings = azure_settings

    async def execute_target(
        self,
        *,
        run: DeploymentRun,
        target: Target,
        release: Release,
        attempt: int,
    ) -> AsyncIterator[TargetExecutionEvent]:
        validating_correlation = build_correlation_id(
            run.id, target.id, attempt, TargetStage.VALIDATING
        )
        yield TargetExecutionEvent(
            event_type="started",
            stage=TargetStage.VALIDATING,
            correlation_id=validating_correlation,
            message="Validating started.",
        )

        if not self._credentials_configured():
            yield TargetExecutionEvent(
                event_type="completed",
                stage=TargetStage.VALIDATING,
                correlation_id=validating_correlation,
                message="Azure execution credentials are missing.",
                error=StructuredError(
                    code="azure_executor_not_configured",
                    message=(
                        "Azure execution mode requires MAPPO_AZURE_TENANT_ID, "
                        "MAPPO_AZURE_CLIENT_ID, and MAPPO_AZURE_CLIENT_SECRET."
                    ),
                    details={
                        "target_id": target.id,
                        "subscription_id": target.subscription_id,
                        "tenant_id": target.tenant_id,
                    },
                ),
                terminal_state=TargetStage.FAILED,
            )
            return

        yield TargetExecutionEvent(
            event_type="completed",
            stage=TargetStage.VALIDATING,
            correlation_id=validating_correlation,
            message="Validating completed.",
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
        yield TargetExecutionEvent(
            event_type="completed",
            stage=TargetStage.DEPLOYING,
            correlation_id=deploying_correlation,
            message="Azure execution adapter scaffold is not wired to ARM yet.",
            error=StructuredError(
                code="azure_executor_not_implemented",
                message=(
                    "Azure execution adapter boundary is configured, but live ARM "
                    "deployment operations are not implemented yet."
                ),
                details={
                    "target_id": target.id,
                    "subscription_id": target.subscription_id,
                    "release_id": release.id,
                },
            ),
            terminal_state=TargetStage.FAILED,
        )

    def _credentials_configured(self) -> bool:
        return bool(
            self._settings.tenant_id and self._settings.client_id and self._settings.client_secret
        )


def build_correlation_id(
    run_id: str,
    target_id: str,
    attempt: int,
    stage: TargetStage,
) -> str:
    key = f"{run_id}:{target_id}:{attempt}:{stage.value}"
    digest = hashlib.sha256(key.encode()).hexdigest()
    return f"corr-{digest[:16]}"
