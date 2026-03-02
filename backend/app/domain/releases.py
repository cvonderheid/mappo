from __future__ import annotations

import asyncio
from uuid import uuid4

from app.domain.common import StoreError, utc_now
from app.modules.schemas import CreateReleaseRequest, Release


class ReleasesDomainMixin:
    _lock: asyncio.Lock
    _releases: dict[str, Release]

    def _replace_releases_locked(self) -> None: ...
    def _save_release_locked(self, release: Release) -> None: ...

    async def list_releases(self) -> list[Release]:
        async with self._lock:
            releases = [release.model_copy(deep=True) for release in self._releases.values()]
        return sorted(releases, key=lambda release: release.created_at, reverse=True)

    async def replace_releases(self, releases: list[Release]) -> None:
        by_id: dict[str, Release] = {}
        for release in releases:
            if release.id in by_id:
                raise StoreError(f"duplicate release id in import payload: {release.id}")
            by_id[release.id] = release

        async with self._lock:
            self._releases = by_id
            self._replace_releases_locked()

    async def create_release(
        self,
        request: CreateReleaseRequest,
    ) -> Release:
        release_id = f"rel-{uuid4().hex[:10]}"
        release = Release(
            id=release_id,
            template_spec_id=request.template_spec_id,
            template_spec_version=request.template_spec_version,
            parameter_defaults=request.parameter_defaults,
            release_notes=request.release_notes,
            verification_hints=request.verification_hints,
            created_at=utc_now(),
        )
        async with self._lock:
            self._releases[release.id] = release
            self._save_release_locked(release)
        return release.model_copy(deep=True)
