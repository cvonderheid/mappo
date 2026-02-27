from __future__ import annotations

import os
import subprocess
from collections.abc import Generator
from pathlib import Path

import pytest
from fastapi.testclient import TestClient
from sqlalchemy import delete

from app.core.settings import get_settings
from app.db.generated.models import Releases, Runs, Targets
from app.db.session import create_engine_and_session_factory
from app.main import create_app

DEFAULT_DATABASE_URL = "postgresql+psycopg://txero:txero@localhost:5432/mappo"


def _current_database_url() -> str:
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


@pytest.fixture(scope="session", autouse=True)
def _bootstrap_postgres() -> Generator[None, None, None]:
    repo_root = Path(__file__).resolve().parents[2]
    subprocess.run(
        [str(repo_root / "backend" / "scripts" / "ensure_db.sh")],
        check=True,
        cwd=repo_root,
    )
    subprocess.run(
        [str(repo_root / "backend" / "scripts" / "flyway.sh"), "migrate"],
        check=True,
        cwd=repo_root,
    )
    yield


@pytest.fixture
def client() -> Generator[TestClient, None, None]:
    os.environ["MAPPO_DATABASE_URL"] = _current_database_url()
    os.environ["MAPPO_RETENTION_DAYS"] = "90"
    os.environ["MAPPO_EXECUTION_MODE"] = "demo"
    _reset_database(os.environ["MAPPO_DATABASE_URL"])
    get_settings.cache_clear()
    app = create_app()
    with TestClient(app) as test_client:
        yield test_client
