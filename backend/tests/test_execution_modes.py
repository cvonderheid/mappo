from __future__ import annotations

import asyncio
import os
import threading
import time

import pytest
from sqlalchemy import delete

from app.db.generated.models import Releases, Runs, Targets
from app.db.session import create_engine_and_session_factory
from app.domain.runtime import ControlPlaneRuntime
from app.modules import execution as execution_module
from app.modules.execution import (
    AzureDeployResult,
    AzureExecutionError,
    AzureExecutorSettings,
    AzureVerifyResult,
    ContainerAppResourceRef,
    ExecutionMode,
)
from app.modules.schemas import (
    CreateRunRequest,
    DeploymentMode,
    DeploymentRun,
    DeploymentScope,
    Release,
    RunStatus,
    Target,
    TargetStage,
)
from tests.support.sample_data import sample_releases, sample_targets, seed_store

DEFAULT_DATABASE_URL = "postgresql+psycopg://mappo:mappo@localhost:5433/mappo"
TERMINAL_STATUSES = {RunStatus.SUCCEEDED, RunStatus.FAILED, RunStatus.PARTIAL, RunStatus.HALTED}


def _database_url() -> str:
    return os.getenv("MAPPO_DATABASE_URL") or os.getenv("DATABASE_URL") or DEFAULT_DATABASE_URL


def _reset_database(database_url: str) -> None:
    engine, session_factory = create_engine_and_session_factory(database_url)
    try:
        with session_factory() as session:
            session.execute(delete(Runs))
            session.execute(delete(Releases))
            session.execute(delete(Targets))
            session.commit()
    finally:
        engine.dispose()


async def _wait_for_terminal(
    store: ControlPlaneRuntime,
    run_id: str,
    timeout_seconds: float = 2.0,
) -> RunStatus:
    deadline = time.monotonic() + timeout_seconds
    while time.monotonic() < deadline:
        run = await store.get_run(run_id)
        if run.status in TERMINAL_STATUSES:
            return run.status
        await asyncio.sleep(0.02)
    raise AssertionError(f"run did not become terminal within {timeout_seconds} seconds")


def _snapshot_for_target(target: Target) -> execution_module.AzureContainerAppSnapshot:
    return execution_module.AzureContainerAppSnapshot(
        resource_ref=ContainerAppResourceRef(
            subscription_id=target.subscription_id,
            resource_group_name="rg-target-01",
            container_app_name="ca-target-01",
        ),
        current_image="ghcr.io/example/mappo:1.4.2",
        latest_revision_name="ca-target-01--rev002",
        latest_ready_revision_name="ca-target-01--rev002",
        latest_revision_fqdn="ca-target-01.example.azurecontainerapps.io",
        raw_app=object(),
    )


class _SuccessRuntime:
    def validate_target(self, target: Target) -> execution_module.AzureContainerAppSnapshot:
        return _snapshot_for_target(target)

    def deploy_release(
        self,
        *,
        run: DeploymentRun,
        target: Target,
        release: Release,
        snapshot: execution_module.AzureContainerAppSnapshot,
        attempt: int,
    ) -> AzureDeployResult:
        del run
        del release
        del attempt
        updated_snapshot = execution_module.AzureContainerAppSnapshot(
            resource_ref=snapshot.resource_ref,
            current_image="ghcr.io/example/mappo:1.5.0",
            latest_revision_name="ca-target-01--rev003",
            latest_ready_revision_name="ca-target-01--rev003",
            latest_revision_fqdn=snapshot.latest_revision_fqdn,
            raw_app=object(),
        )
        return AzureDeployResult(
            snapshot=updated_snapshot,
            desired_image=updated_snapshot.current_image,
            changed=True,
        )

    def verify_target(
        self,
        *,
        target: Target,
        release: Release,
        snapshot: execution_module.AzureContainerAppSnapshot,
    ) -> AzureVerifyResult:
        del target
        del release
        del snapshot
        return AzureVerifyResult(
            health_url="https://ca-target-01.example.azurecontainerapps.io/health",
            status_code=200,
            ready_revision="ca-target-01--rev003",
        )


class _DeployFailureRuntime(_SuccessRuntime):
    def deploy_release(
        self,
        *,
        run: DeploymentRun,
        target: Target,
        release: Release,
        snapshot: execution_module.AzureContainerAppSnapshot,
        attempt: int,
    ) -> AzureDeployResult:
        del run
        del release
        del snapshot
        del attempt
        raise AzureExecutionError(
            code="azure_deploy_failed",
            message="Azure Container App update failed.",
            details={
                "target_id": target.id,
                "azure_error_code": "ContainerAppOperationError",
                "azure_error_message": "Container image could not be pulled.",
                "status_code": 400,
                "azure_request_id": "req-001",
                "azure_correlation_id": "corr-azure-001",
                "azure_error_details": [
                    {
                        "code": "MANIFEST_UNKNOWN",
                        "message": "manifest for ghcr.io/example/mappo:1.5.0 not found",
                    }
                ],
            },
        )


class _ConcurrencyTrackingRuntime(_SuccessRuntime):
    _lock = threading.Lock()
    _active = 0
    _max_active = 0

    @classmethod
    def reset(cls) -> None:
        with cls._lock:
            cls._active = 0
            cls._max_active = 0

    @classmethod
    def max_active(cls) -> int:
        with cls._lock:
            return cls._max_active

    def deploy_release(
        self,
        *,
        run: DeploymentRun,
        target: Target,
        release: Release,
        snapshot: execution_module.AzureContainerAppSnapshot,
        attempt: int,
    ) -> AzureDeployResult:
        cls = type(self)
        with cls._lock:
            cls._active += 1
            if cls._active > cls._max_active:
                cls._max_active = cls._active
        try:
            time.sleep(0.06)
            return super().deploy_release(
                run=run,
                target=target,
                release=release,
                snapshot=snapshot,
                attempt=attempt,
            )
        finally:
            with cls._lock:
                cls._active -= 1


class _TemplateSpecRuntime(_SuccessRuntime):
    def __init__(self) -> None:
        self.call_count = 0
        self.last_deployment_name: str | None = None

    def deploy_release(
        self,
        *,
        run: DeploymentRun,
        target: Target,
        release: Release,
        snapshot: execution_module.AzureContainerAppSnapshot,
        attempt: int,
    ) -> AzureDeployResult:
        self.call_count += 1
        assert release.deployment_mode == DeploymentMode.TEMPLATE_SPEC
        assert run.execution_mode == DeploymentMode.TEMPLATE_SPEC
        assert attempt == 1
        result = super().deploy_release(
            run=run,
            target=target,
            release=release,
            snapshot=snapshot,
            attempt=attempt,
        )
        self.last_deployment_name = "mappo-template-spec-test"
        return AzureDeployResult(
            snapshot=result.snapshot,
            desired_image=result.desired_image,
            changed=result.changed,
            metadata={
                "deployment_name": self.last_deployment_name,
                "deployment_scope": DeploymentScope.RESOURCE_GROUP.value,
                "template_spec_version_id": (
                    "/subscriptions/provider-sub/resourceGroups/mappo-rg/providers/"
                    "Microsoft.Resources/templateSpecs/mappo-managed-app/versions/2026.02.25.3"
                ),
            },
        )


def test_azure_mode_fails_without_credentials() -> None:
    database_url = _database_url()
    _reset_database(database_url)

    async def _scenario() -> None:
        store = ControlPlaneRuntime(
            database_url=database_url,
            execution_mode=ExecutionMode.AZURE,
            azure_settings=AzureExecutorSettings(),
            stage_delay_seconds=0.0,
        )
        try:
            await seed_store(store)
            run = await store.create_run(
                CreateRunRequest(
                    release_id="rel-2026-02-25",
                    target_ids=["target-01"],
                    concurrency=1,
                )
            )
            status = await _wait_for_terminal(store, run.id)
            assert status == RunStatus.FAILED

            run_detail = await store.get_run(run.id)
            target_record = next(
                record for record in run_detail.target_records if record.target_id == "target-01"
            )
            assert target_record.status == TargetStage.FAILED
            error_codes = [
                stage.error.code for stage in target_record.stages if stage.error is not None
            ]
            assert "azure_executor_not_configured" in error_codes
        finally:
            await store.shutdown()

    try:
        asyncio.run(_scenario())
    finally:
        _reset_database(database_url)


def test_azure_mode_succeeds_with_runtime_stub(monkeypatch: pytest.MonkeyPatch) -> None:
    database_url = _database_url()
    _reset_database(database_url)
    monkeypatch.setattr(
        execution_module,
        "create_azure_runtime",
        lambda _settings: _SuccessRuntime(),
    )

    async def _scenario() -> None:
        store = ControlPlaneRuntime(
            database_url=database_url,
            execution_mode=ExecutionMode.AZURE,
            azure_settings=AzureExecutorSettings(
                tenant_id="tenant-live",
                client_id="client-live",
                client_secret="secret-live",
            ),
            stage_delay_seconds=0.0,
        )
        try:
            await seed_store(store)
            run = await store.create_run(
                CreateRunRequest(
                    release_id="rel-2026-02-25",
                    target_ids=["target-01"],
                    concurrency=1,
                )
            )
            status = await _wait_for_terminal(store, run.id)
            assert status == RunStatus.SUCCEEDED

            run_detail = await store.get_run(run.id)
            target_record = next(
                record for record in run_detail.target_records if record.target_id == "target-01"
            )
            assert target_record.status == TargetStage.SUCCEEDED
            assert all(stage.error is None for stage in target_record.stages)
        finally:
            await store.shutdown()

    try:
        asyncio.run(_scenario())
    finally:
        _reset_database(database_url)


def test_azure_template_spec_mode_uses_release_execution_mode(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    database_url = _database_url()
    _reset_database(database_url)
    runtime_stub = _TemplateSpecRuntime()
    monkeypatch.setattr(
        execution_module,
        "create_azure_runtime",
        lambda _settings: runtime_stub,
    )

    async def _scenario() -> None:
        store = ControlPlaneRuntime(
            database_url=database_url,
            execution_mode=ExecutionMode.AZURE,
            azure_settings=AzureExecutorSettings(
                tenant_id="tenant-live",
                client_id="client-live",
                client_secret="secret-live",
                enable_quota_preflight=False,
            ),
            stage_delay_seconds=0.0,
        )
        try:
            releases = sample_releases()
            template_release = releases[1].model_copy(
                update={
                    "deployment_mode": DeploymentMode.TEMPLATE_SPEC,
                    "deployment_scope": DeploymentScope.RESOURCE_GROUP,
                    "template_spec_version_id": (
                        "/subscriptions/provider-sub/resourceGroups/mappo-rg/providers/"
                        "Microsoft.Resources/templateSpecs/mappo-managed-app/versions/2026.02.25.3"
                    ),
                },
                deep=True,
            )
            await store.replace_targets([sample_targets()[0]], clear_runs=True)
            await store.replace_releases([template_release])

            run = await store.create_run(
                CreateRunRequest(
                    release_id=template_release.id,
                    target_ids=["target-01"],
                    concurrency=1,
                )
            )
            status = await _wait_for_terminal(store, run.id)
            assert status == RunStatus.SUCCEEDED

            run_detail = await store.get_run(run.id)
            assert run_detail.execution_mode == DeploymentMode.TEMPLATE_SPEC
            target_record = run_detail.target_records[0]
            deploy_stage = next(
                stage for stage in target_record.stages if stage.stage == TargetStage.DEPLOYING
            )
            assert "Template Spec deployment completed" in deploy_stage.message
            assert runtime_stub.call_count == 1
        finally:
            await store.shutdown()

    try:
        asyncio.run(_scenario())
    finally:
        _reset_database(database_url)


def test_azure_mode_surfaces_deploy_failure(monkeypatch: pytest.MonkeyPatch) -> None:
    database_url = _database_url()
    _reset_database(database_url)
    monkeypatch.setattr(
        execution_module,
        "create_azure_runtime",
        lambda _settings: _DeployFailureRuntime(),
    )

    async def _scenario() -> None:
        store = ControlPlaneRuntime(
            database_url=database_url,
            execution_mode=ExecutionMode.AZURE,
            azure_settings=AzureExecutorSettings(
                tenant_id="tenant-live",
                client_id="client-live",
                client_secret="secret-live",
            ),
            stage_delay_seconds=0.0,
        )
        try:
            await seed_store(store)
            run = await store.create_run(
                CreateRunRequest(
                    release_id="rel-2026-02-25",
                    target_ids=["target-01"],
                    concurrency=1,
                )
            )
            status = await _wait_for_terminal(store, run.id)
            assert status == RunStatus.FAILED

            run_detail = await store.get_run(run.id)
            target_record = next(
                record for record in run_detail.target_records if record.target_id == "target-01"
            )
            assert target_record.status == TargetStage.FAILED
            error_codes = [
                stage.error.code for stage in target_record.stages if stage.error is not None
            ]
            assert "azure_deploy_failed" in error_codes
            log_messages = [event.message for event in target_record.logs]
            assert (
                "Azure error [ContainerAppOperationError]: "
                "Container image could not be pulled."
            ) in log_messages
            assert "Azure request id: req-001" in log_messages
            assert (
                "Azure detail [MANIFEST_UNKNOWN]: "
                "manifest for ghcr.io/example/mappo:1.5.0 not found"
            ) in log_messages
        finally:
            await store.shutdown()

    try:
        asyncio.run(_scenario())
    finally:
        _reset_database(database_url)


def test_azure_mode_applies_guardrail_concurrency_caps(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    database_url = _database_url()
    _reset_database(database_url)
    monkeypatch.setattr(
        execution_module,
        "create_azure_runtime",
        lambda _settings: _SuccessRuntime(),
    )

    async def _scenario() -> None:
        store = ControlPlaneRuntime(
            database_url=database_url,
            execution_mode=ExecutionMode.AZURE,
            azure_settings=AzureExecutorSettings(
                tenant_id="tenant-live",
                client_id="client-live",
                client_secret="secret-live",
                max_run_concurrency=2,
                max_subscription_concurrency=1,
                enable_quota_preflight=False,
            ),
            stage_delay_seconds=0.0,
        )
        try:
            await seed_store(store)
            run = await store.create_run(
                CreateRunRequest(
                    release_id="rel-2026-02-25",
                    target_ids=["target-01", "target-02", "target-03", "target-04"],
                    concurrency=6,
                )
            )
            assert run.concurrency == 2
            assert run.subscription_concurrency == 1
            assert len(run.guardrail_warnings) >= 2
        finally:
            await store.shutdown()

    try:
        asyncio.run(_scenario())
    finally:
        _reset_database(database_url)


def test_azure_mode_enforces_per_subscription_batching(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    database_url = _database_url()
    _reset_database(database_url)
    _ConcurrencyTrackingRuntime.reset()
    monkeypatch.setattr(
        execution_module,
        "create_azure_runtime",
        lambda _settings: _ConcurrencyTrackingRuntime(),
    )

    async def _scenario() -> None:
        store = ControlPlaneRuntime(
            database_url=database_url,
            execution_mode=ExecutionMode.AZURE,
            azure_settings=AzureExecutorSettings(
                tenant_id="tenant-live",
                client_id="client-live",
                client_secret="secret-live",
                max_run_concurrency=3,
                max_subscription_concurrency=1,
                enable_quota_preflight=False,
            ),
            stage_delay_seconds=0.0,
        )
        try:
            await seed_store(store)
            targets = await store.list_targets()
            patched_targets = []
            for index, target in enumerate(targets):
                if index < 3:
                    patched_targets.append(
                        target.model_copy(
                            update={"subscription_id": "55555555-5555-5555-5555-555555555555"},
                            deep=True,
                        )
                    )
                else:
                    patched_targets.append(target)
            await store.replace_targets(patched_targets, clear_runs=True)
            await store.replace_releases(sample_releases())

            run = await store.create_run(
                CreateRunRequest(
                    release_id="rel-2026-02-25",
                    target_ids=[
                        patched_targets[0].id,
                        patched_targets[1].id,
                        patched_targets[2].id,
                    ],
                    concurrency=3,
                )
            )
            status = await _wait_for_terminal(store, run.id)
            assert status == RunStatus.SUCCEEDED
            assert _ConcurrencyTrackingRuntime.max_active() == 1
        finally:
            await store.shutdown()

    try:
        asyncio.run(_scenario())
    finally:
        _reset_database(database_url)
