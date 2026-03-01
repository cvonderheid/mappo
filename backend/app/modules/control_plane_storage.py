from __future__ import annotations

from datetime import datetime
from typing import Any

from sqlalchemy import delete, select

from app.db.generated.models import (
    MarketplaceEvents,
    Releases,
    Runs,
    TargetRegistrations,
    Targets,
)
from app.modules.schemas import (
    DeploymentRun,
    MarketplaceEventRecord,
    Release,
    Target,
    TargetRegistrationRecord,
)


def load_targets(session_factory: Any) -> dict[str, Target]:
    with session_factory() as session:
        rows = session.execute(select(Targets.payload_json)).scalars().all()
    targets: dict[str, Target] = {}
    for payload in rows:
        try:
            target = Target.model_validate(payload)
            targets[target.id] = target
        except Exception as error:
            print(f"failed to parse target payload: {error}")
    return targets


def load_target_registrations(session_factory: Any) -> dict[str, TargetRegistrationRecord]:
    with session_factory() as session:
        rows = session.execute(select(TargetRegistrations.payload_json)).scalars().all()
    registrations: dict[str, TargetRegistrationRecord] = {}
    for payload in rows:
        try:
            item = TargetRegistrationRecord.model_validate(payload)
            registrations[item.target_id] = item
        except Exception as error:
            print(f"failed to parse target registration payload: {error}")
    return registrations


def load_marketplace_events(session_factory: Any) -> dict[str, MarketplaceEventRecord]:
    with session_factory() as session:
        rows = (
            session.execute(
                select(MarketplaceEvents.payload_json).order_by(
                    MarketplaceEvents.created_at.asc()
                )
            )
            .scalars()
            .all()
        )
    events: dict[str, MarketplaceEventRecord] = {}
    for payload in rows:
        try:
            event = MarketplaceEventRecord.model_validate(payload)
            events[event.event_id] = event
        except Exception as error:
            print(f"failed to parse marketplace event payload: {error}")
    return events


def load_releases(session_factory: Any) -> dict[str, Release]:
    with session_factory() as session:
        rows = session.execute(select(Releases.payload_json)).scalars().all()
    releases: dict[str, Release] = {}
    for payload in rows:
        try:
            release = Release.model_validate(payload)
            releases[release.id] = release
        except Exception as error:
            print(f"failed to parse release payload: {error}")
    return releases


def load_runs(session_factory: Any) -> dict[str, DeploymentRun]:
    with session_factory() as session:
        rows = (
            session.execute(select(Runs.payload_json).order_by(Runs.created_at.asc()))
            .scalars()
            .all()
        )
    runs: dict[str, DeploymentRun] = {}
    for payload in rows:
        try:
            run = DeploymentRun.model_validate(payload)
            runs[run.id] = run
        except Exception as error:
            print(f"failed to parse run payload: {error}")
    return runs


def replace_targets(
    session_factory: Any,
    *,
    targets: list[Target],
    updated_at: datetime,
) -> None:
    with session_factory() as session:
        session.execute(delete(Targets))
        session.add_all(
            [
                Targets(
                    id=target.id,
                    payload_json=target.model_dump(mode="json"),
                    updated_at=updated_at,
                )
                for target in sorted(targets, key=lambda item: item.id)
            ]
        )
        session.commit()


def replace_releases(session_factory: Any, *, releases: list[Release]) -> None:
    with session_factory() as session:
        session.execute(delete(Releases))
        session.add_all(
            [
                Releases(
                    id=release.id,
                    payload_json=release.model_dump(mode="json"),
                    created_at=release.created_at,
                )
                for release in sorted(releases, key=lambda item: item.created_at)
            ]
        )
        session.commit()


def save_release(session_factory: Any, *, release: Release) -> None:
    with session_factory() as session:
        row = session.get(Releases, release.id)
        if row is None:
            session.add(
                Releases(
                    id=release.id,
                    payload_json=release.model_dump(mode="json"),
                    created_at=release.created_at,
                )
            )
        else:
            row.payload_json = release.model_dump(mode="json")
            row.created_at = release.created_at
        session.commit()


def save_target(
    session_factory: Any,
    *,
    target: Target,
    updated_at: datetime,
) -> None:
    with session_factory() as session:
        row = session.get(Targets, target.id)
        if row is None:
            session.add(
                Targets(
                    id=target.id,
                    payload_json=target.model_dump(mode="json"),
                    updated_at=updated_at,
                )
            )
        else:
            row.payload_json = target.model_dump(mode="json")
            row.updated_at = updated_at
        session.commit()


def save_target_registration(
    session_factory: Any,
    *,
    registration: TargetRegistrationRecord,
    updated_at: datetime,
) -> None:
    with session_factory() as session:
        row = session.get(TargetRegistrations, registration.target_id)
        if row is None:
            session.add(
                TargetRegistrations(
                    id=registration.target_id,
                    payload_json=registration.model_dump(mode="json"),
                    updated_at=updated_at,
                )
            )
        else:
            row.payload_json = registration.model_dump(mode="json")
            row.updated_at = updated_at
        session.commit()


def delete_target(session_factory: Any, *, target_id: str) -> None:
    with session_factory() as session:
        session.execute(delete(Targets).where(Targets.id == target_id))
        session.commit()


def delete_target_registration(session_factory: Any, *, target_id: str) -> None:
    with session_factory() as session:
        session.execute(
            delete(TargetRegistrations).where(TargetRegistrations.id == target_id)
        )
        session.commit()


def save_marketplace_event(session_factory: Any, *, event: MarketplaceEventRecord) -> None:
    with session_factory() as session:
        row = session.get(MarketplaceEvents, event.event_id)
        if row is None:
            session.add(
                MarketplaceEvents(
                    id=event.event_id,
                    payload_json=event.model_dump(mode="json"),
                    created_at=event.created_at,
                )
            )
        else:
            row.payload_json = event.model_dump(mode="json")
            row.created_at = event.created_at
        session.commit()


def save_run(session_factory: Any, *, run: DeploymentRun) -> None:
    with session_factory() as session:
        row = session.get(Runs, run.id)
        if row is None:
            session.add(
                Runs(
                    id=run.id,
                    payload_json=run.model_dump(mode="json"),
                    created_at=run.created_at,
                    updated_at=run.updated_at,
                )
            )
        else:
            row.payload_json = run.model_dump(mode="json")
            row.created_at = run.created_at
            row.updated_at = run.updated_at
        session.commit()


def delete_all_runs(session_factory: Any) -> None:
    with session_factory() as session:
        session.execute(delete(Runs))
        session.commit()


def delete_runs_by_ids(session_factory: Any, *, run_ids: list[str]) -> None:
    if not run_ids:
        return
    with session_factory() as session:
        session.execute(delete(Runs).where(Runs.id.in_(run_ids)))
        session.commit()
