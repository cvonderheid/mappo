from __future__ import annotations

from collections import defaultdict
from typing import Any

from sqlalchemy import delete, select

from app.db.generated.models import ReleaseParameterDefaults, Releases, ReleaseVerificationHints
from app.modules.schemas import DeploymentMode, DeploymentScope, Release


class ReleasesRepository:
    def __init__(self, session_factory: Any):
        self._session_factory = session_factory

    def load_releases(self) -> dict[str, Release]:
        with self._session_factory() as session:
            release_rows = session.execute(select(Releases)).scalars().all()
            release_ids = [row.id for row in release_rows]
            parameter_rows = (
                session.execute(
                    select(ReleaseParameterDefaults)
                    .where(ReleaseParameterDefaults.release_id.in_(release_ids))
                    .order_by(
                        ReleaseParameterDefaults.release_id.asc(),
                        ReleaseParameterDefaults.param_key.asc(),
                    )
                )
                .scalars()
                .all()
                if release_ids
                else []
            )
            hint_rows = (
                session.execute(
                    select(ReleaseVerificationHints)
                    .where(ReleaseVerificationHints.release_id.in_(release_ids))
                    .order_by(
                        ReleaseVerificationHints.release_id.asc(),
                        ReleaseVerificationHints.position.asc(),
                    )
                )
                .scalars()
                .all()
                if release_ids
                else []
            )

        params_by_release: dict[str, dict[str, str]] = defaultdict(dict)
        for row in parameter_rows:
            params_by_release[row.release_id][row.param_key] = row.param_value

        hints_by_release: dict[str, list[str]] = defaultdict(list)
        for row in hint_rows:
            hints_by_release[row.release_id].append(row.hint)

        releases: dict[str, Release] = {}
        for row in release_rows:
            release = Release(
                id=row.id,
                template_spec_id=row.template_spec_id,
                template_spec_version=row.template_spec_version,
                deployment_mode=(
                    row.deployment_mode
                    if isinstance(row.deployment_mode, DeploymentMode)
                    else DeploymentMode(row.deployment_mode)
                ),
                template_spec_version_id=row.template_spec_version_id,
                deployment_scope=(
                    row.deployment_scope
                    if isinstance(row.deployment_scope, DeploymentScope)
                    else DeploymentScope(row.deployment_scope)
                ),
                deployment_mode_settings=row.deployment_mode_settings or {},
                parameter_defaults=params_by_release.get(row.id, {}),
                release_notes=row.release_notes,
                verification_hints=hints_by_release.get(row.id, []),
                created_at=row.created_at,
            )
            releases[release.id] = release
        return releases

    def replace_releases(self, *, releases: list[Release]) -> None:
        with self._session_factory() as session:
            session.execute(delete(Releases))
            for release in sorted(releases, key=lambda item: item.created_at):
                session.add(
                    Releases(
                        id=release.id,
                        template_spec_id=release.template_spec_id,
                        template_spec_version=release.template_spec_version,
                        deployment_mode=release.deployment_mode.value,
                        template_spec_version_id=release.template_spec_version_id,
                        deployment_scope=release.deployment_scope.value,
                        deployment_mode_settings=release.deployment_mode_settings,
                        release_notes=release.release_notes,
                        created_at=release.created_at,
                    )
                )
                for key, value in sorted(release.parameter_defaults.items()):
                    session.add(
                        ReleaseParameterDefaults(
                            release_id=release.id,
                            param_key=key,
                            param_value=value,
                        )
                    )
                for position, hint in enumerate(release.verification_hints):
                    session.add(
                        ReleaseVerificationHints(
                            release_id=release.id,
                            position=position,
                            hint=hint,
                        )
                    )
            session.commit()

    def save_release(self, *, release: Release) -> None:
        with self._session_factory() as session:
            row = session.get(Releases, release.id)
            if row is None:
                session.add(
                    Releases(
                        id=release.id,
                        template_spec_id=release.template_spec_id,
                        template_spec_version=release.template_spec_version,
                        deployment_mode=release.deployment_mode.value,
                        template_spec_version_id=release.template_spec_version_id,
                        deployment_scope=release.deployment_scope.value,
                        deployment_mode_settings=release.deployment_mode_settings,
                        release_notes=release.release_notes,
                        created_at=release.created_at,
                    )
                )
            else:
                row.template_spec_id = release.template_spec_id
                row.template_spec_version = release.template_spec_version
                row.deployment_mode = release.deployment_mode.value
                row.template_spec_version_id = release.template_spec_version_id
                row.deployment_scope = release.deployment_scope.value
                row.deployment_mode_settings = release.deployment_mode_settings
                row.release_notes = release.release_notes
                row.created_at = release.created_at
            session.execute(
                delete(ReleaseParameterDefaults).where(
                    ReleaseParameterDefaults.release_id == release.id
                )
            )
            session.execute(
                delete(ReleaseVerificationHints).where(
                    ReleaseVerificationHints.release_id == release.id
                )
            )
            for key, value in sorted(release.parameter_defaults.items()):
                session.add(
                    ReleaseParameterDefaults(
                        release_id=release.id,
                        param_key=key,
                        param_value=value,
                    )
                )
            for position, hint in enumerate(release.verification_hints):
                session.add(
                    ReleaseVerificationHints(
                        release_id=release.id,
                        position=position,
                        hint=hint,
                    )
                )
            session.commit()
