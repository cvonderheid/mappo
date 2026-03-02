from __future__ import annotations

from typing import cast

from fastapi import Depends, Request
from sqlalchemy.orm import Session

from app.db.session import get_db_session
from app.domain.runtime import ControlPlaneRuntime
from app.repositories.admin_repository import AdminRepository
from app.services.admin_service import AdminService
from app.services.releases_service import ReleasesService
from app.services.runs_service import RunsService
from app.services.targets_service import TargetsService


def get_store(request: Request) -> ControlPlaneRuntime:
    return cast(ControlPlaneRuntime, request.app.state.store)


def get_targets_service(request: Request) -> TargetsService:
    return TargetsService(get_store(request))


def get_releases_service(request: Request) -> ReleasesService:
    return ReleasesService(get_store(request))


def get_runs_service(request: Request) -> RunsService:
    return RunsService(get_store(request))


def get_admin_service(
    request: Request,
    db_session: Session = Depends(get_db_session),
) -> AdminService:
    store = get_store(request)
    return AdminService(store, AdminRepository(session=db_session))
