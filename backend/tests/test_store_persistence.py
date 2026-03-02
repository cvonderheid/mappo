from __future__ import annotations

import asyncio
import os
from datetime import UTC, datetime

from sqlalchemy import delete

from app.db.generated.models import MarketplaceEvents, Releases, Runs, TargetRegistrations, Targets
from app.db.session import create_engine_and_session_factory
from app.modules.control_plane import ControlPlaneStore
from app.modules.execution import ExecutionMode
from app.modules.schemas import (
    CreateRunRequest,
    MarketplaceEventIngestRequest,
    Release,
    RunStatus,
    StrategyMode,
    Target,
)
from tests.support.sample_data import sample_releases, seed_store

DEFAULT_DATABASE_URL = "postgresql+psycopg://mappo:mappo@localhost:5433/mappo"
TENANT_LIVE_A = "11111111-1111-1111-1111-111111111111"
SUBSCRIPTION_LIVE_A = "22222222-2222-2222-2222-222222222222"
TENANT_TAMPERED = "33333333-3333-3333-3333-333333333333"
SUBSCRIPTION_TAMPERED = "44444444-4444-4444-4444-444444444444"


def _database_url() -> str:
    return os.getenv("MAPPO_DATABASE_URL") or os.getenv("DATABASE_URL") or DEFAULT_DATABASE_URL


def _reset_database(database_url: str) -> None:
    engine, session_factory = create_engine_and_session_factory(database_url)
    try:
        with session_factory() as session:
            session.execute(delete(MarketplaceEvents))
            session.execute(delete(TargetRegistrations))
            session.execute(delete(Runs))
            session.execute(delete(Releases))
            session.execute(delete(Targets))
            session.commit()
    finally:
        engine.dispose()


def test_run_persists_across_store_restarts() -> None:
    database_url = _database_url()
    _reset_database(database_url)

    first_store = ControlPlaneStore(
        database_url=database_url,
        execution_mode=ExecutionMode.DEMO,
        stage_delay_seconds=0.01,
    )
    try:
        asyncio.run(seed_store(first_store))
        created = asyncio.run(
            first_store.create_run(
                CreateRunRequest(
                    release_id="rel-2026-02-25",
                    strategy_mode=StrategyMode.ALL_AT_ONCE,
                    target_ids=["target-01"],
                    concurrency=1,
                )
            )
        )
        assert created.status == RunStatus.RUNNING
    finally:
        asyncio.run(first_store.shutdown())

    second_store = ControlPlaneStore(
        database_url=database_url,
        execution_mode=ExecutionMode.DEMO,
        stage_delay_seconds=0.01,
    )
    try:
        summaries = asyncio.run(second_store.list_runs())
        assert any(summary.id == created.id for summary in summaries)

        persisted = asyncio.run(second_store.get_run(created.id))
        assert persisted.status == RunStatus.HALTED
    finally:
        asyncio.run(second_store.shutdown())
        _reset_database(database_url)


def test_replace_releases_overwrites_catalog() -> None:
    database_url = _database_url()
    _reset_database(database_url)
    store = ControlPlaneStore(
        database_url=database_url,
        execution_mode=ExecutionMode.DEMO,
        stage_delay_seconds=0.01,
    )
    try:
        asyncio.run(seed_store(store))

        replacement = Release(
            id="rel-2026-03-01",
            template_spec_id=sample_releases()[0].template_spec_id,
            template_spec_version="2026.03.01.1",
            parameter_defaults={"imageTag": "1.6.0"},
            release_notes="Replacement release set.",
            verification_hints=["Health endpoint returns 200"],
            created_at=datetime.now(tz=UTC),
        )
        asyncio.run(store.replace_releases([replacement]))

        releases = asyncio.run(store.list_releases())
        assert len(releases) == 1
        assert releases[0].id == "rel-2026-03-01"
    finally:
        asyncio.run(store.shutdown())
        _reset_database(database_url)


def test_replace_targets_can_clear_runs() -> None:
    database_url = _database_url()
    _reset_database(database_url)
    store = ControlPlaneStore(
        database_url=database_url,
        execution_mode=ExecutionMode.DEMO,
        stage_delay_seconds=0.01,
    )
    try:
        asyncio.run(seed_store(store))
        asyncio.run(
            store.create_run(
                CreateRunRequest(
                    release_id="rel-2026-02-25",
                    strategy_mode=StrategyMode.ALL_AT_ONCE,
                    target_ids=["target-01"],
                    concurrency=1,
                )
            )
        )
        assert len(asyncio.run(store.list_runs())) >= 1

        replacement_target = Target(
            id="target-live-01",
            tenant_id="55555555-5555-5555-5555-555555555555",
            subscription_id="66666666-6666-6666-6666-666666666666",
            managed_app_id=(
                "/subscriptions/66666666-6666-6666-6666-666666666666/resourceGroups/rg-target-live-01/"
                "providers/Microsoft.App/containerApps/target-live-01"
            ),
            tags={"ring": "canary", "region": "eastus", "tier": "gold", "environment": "demo"},
            last_deployed_release="2026.02.20.1",
            health_status="healthy",
            last_check_in_at=datetime.now(tz=UTC),
            simulated_failure_mode="none",
        )
        asyncio.run(store.replace_targets([replacement_target], clear_runs=True))

        targets = asyncio.run(store.list_targets())
        runs = asyncio.run(store.list_runs())
        assert len(targets) == 1
        assert targets[0].id == "target-live-01"
        assert len(runs) == 0
    finally:
        asyncio.run(store.shutdown())
        _reset_database(database_url)


def test_registration_metadata_derives_from_target_source() -> None:
    database_url = _database_url()
    _reset_database(database_url)

    first_store = ControlPlaneStore(
        database_url=database_url,
        execution_mode=ExecutionMode.DEMO,
        stage_delay_seconds=0.01,
    )
    try:
        asyncio.run(
            first_store.ingest_marketplace_event(
                MarketplaceEventIngestRequest(
                    event_id="evt-reconcile-001",
                    tenant_id=TENANT_LIVE_A,
                    subscription_id=SUBSCRIPTION_LIVE_A,
                    managed_application_id=(
                        f"/subscriptions/{SUBSCRIPTION_LIVE_A}/resourceGroups/rg-mappo-ma-apps-live/providers/"
                        "Microsoft.Solutions/applications/mappo-ma-target-live-01"
                    ),
                    managed_resource_group_id=(
                        f"/subscriptions/{SUBSCRIPTION_LIVE_A}/resourceGroups/rg-mappo-ma-mrg-live-01"
                    ),
                    container_app_resource_id=(
                        f"/subscriptions/{SUBSCRIPTION_LIVE_A}/resourceGroups/rg-mappo-ma-mrg-live-01/providers/"
                        "Microsoft.App/containerApps/ca-mappo-ma-target-live-01"
                    ),
                    customer_name="Contoso",
                    target_group="canary",
                    region="eastus",
                    environment="prod",
                    tier="gold",
                )
            )
        )

        target = asyncio.run(first_store.list_targets())[0]
        tampered = target.model_copy(
            update={
                "tenant_id": TENANT_TAMPERED,
                "subscription_id": SUBSCRIPTION_TAMPERED,
                "managed_app_id": (
                    f"/subscriptions/{SUBSCRIPTION_TAMPERED}/resourceGroups/rg-tampered/providers/"
                    "Microsoft.App/containerApps/ca-tampered"
                ),
                "customer_name": None,
                "tags": {"ring": "prod", "region": "westus", "environment": "dev", "tier": "basic"},
            }
        )
        asyncio.run(first_store.replace_targets([tampered]))

        projected_targets = asyncio.run(first_store.list_targets())
        assert len(projected_targets) == 1
        assert projected_targets[0].tenant_id == TENANT_TAMPERED
        assert projected_targets[0].subscription_id == SUBSCRIPTION_TAMPERED
        assert (
            projected_targets[0].managed_app_id
            == f"/subscriptions/{SUBSCRIPTION_TAMPERED}/resourceGroups/rg-tampered/providers/"
            "Microsoft.App/containerApps/ca-tampered"
        )
        assert projected_targets[0].customer_name is None
        assert projected_targets[0].tags["ring"] == "prod"
        assert projected_targets[0].tags["region"] == "westus"
        assert projected_targets[0].tags["environment"] == "dev"
        assert projected_targets[0].tags["tier"] == "basic"
        filtered_targets = asyncio.run(first_store.list_targets(tag_filters={"ring": "prod"}))
        assert [item.id for item in filtered_targets] == ["mappo-ma-target-live-01"]

        snapshot = asyncio.run(first_store.get_onboarding_snapshot())
        assert len(snapshot.registrations) == 1
        assert snapshot.registrations[0].tenant_id == TENANT_TAMPERED
        assert snapshot.registrations[0].subscription_id == SUBSCRIPTION_TAMPERED
        assert (
            snapshot.registrations[0].container_app_resource_id
            == f"/subscriptions/{SUBSCRIPTION_TAMPERED}/resourceGroups/rg-tampered/providers/"
            "Microsoft.App/containerApps/ca-tampered"
        )
        assert snapshot.registrations[0].customer_name is None
        assert snapshot.registrations[0].tags["ring"] == "prod"
    finally:
        asyncio.run(first_store.shutdown())

    second_store = ControlPlaneStore(
        database_url=database_url,
        execution_mode=ExecutionMode.DEMO,
        stage_delay_seconds=0.01,
    )
    try:
        targets = asyncio.run(second_store.list_targets())
        assert len(targets) == 1
        assert targets[0].id == "mappo-ma-target-live-01"
        assert targets[0].tenant_id == TENANT_TAMPERED
        assert targets[0].subscription_id == SUBSCRIPTION_TAMPERED
        assert (
            targets[0].managed_app_id
            == f"/subscriptions/{SUBSCRIPTION_TAMPERED}/resourceGroups/rg-tampered/providers/"
            "Microsoft.App/containerApps/ca-tampered"
        )
        assert targets[0].customer_name is None
        assert targets[0].tags["ring"] == "prod"
        assert targets[0].tags["region"] == "westus"
        assert targets[0].tags["environment"] == "dev"
        assert targets[0].tags["tier"] == "basic"

        snapshot = asyncio.run(second_store.get_onboarding_snapshot())
        assert len(snapshot.registrations) == 1
        assert snapshot.registrations[0].tenant_id == TENANT_TAMPERED
        assert snapshot.registrations[0].subscription_id == SUBSCRIPTION_TAMPERED
        assert (
            snapshot.registrations[0].container_app_resource_id
            == f"/subscriptions/{SUBSCRIPTION_TAMPERED}/resourceGroups/rg-tampered/providers/"
            "Microsoft.App/containerApps/ca-tampered"
        )
        assert snapshot.registrations[0].customer_name is None
        assert snapshot.registrations[0].tags["ring"] == "prod"
    finally:
        asyncio.run(second_store.shutdown())
        _reset_database(database_url)
