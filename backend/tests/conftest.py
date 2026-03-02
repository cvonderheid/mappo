from __future__ import annotations

import asyncio
import os
import subprocess
from collections.abc import Generator
from pathlib import Path
from urllib.parse import urlsplit

import pytest
from fastapi.testclient import TestClient
from sqlalchemy import delete

from app.core.settings import get_settings
from app.db.generated.models import (
    ForwarderLogs,
    MarketplaceEvents,
    Releases,
    Runs,
    TargetRegistrations,
    Targets,
)
from app.db.session import create_engine_and_session_factory
from app.main import create_app
from tests.support.sample_data import seed_store

DEFAULT_DATABASE_URL = "postgresql+psycopg://mappo:mappo@localhost:5433/mappo"


def _default_database_url() -> str:
    return DEFAULT_DATABASE_URL


def _current_database_url() -> str:
    return os.getenv("MAPPO_DATABASE_URL") or os.getenv("DATABASE_URL") or _default_database_url()


def _reset_database(database_url: str) -> None:
    engine, session_factory = create_engine_and_session_factory(database_url)
    try:
        with session_factory() as session:
            session.execute(delete(ForwarderLogs))
            session.execute(delete(MarketplaceEvents))
            session.execute(delete(TargetRegistrations))
            session.execute(delete(Runs))
            session.execute(delete(Releases))
            session.execute(delete(Targets))
            session.commit()
    finally:
        engine.dispose()


@pytest.fixture(scope="session", autouse=True)
def _bootstrap_postgres() -> Generator[None, None, None]:
    repo_root = Path(__file__).resolve().parents[2]
    database_url = _current_database_url()
    parsed = urlsplit(database_url)
    host = parsed.hostname or "localhost"
    port = parsed.port or 5433
    user = parsed.username or "mappo"
    password = parsed.password or "mappo"
    env = dict(os.environ)
    env["MAPPO_DATABASE_URL"] = database_url
    env["DATABASE_URL"] = database_url
    env["PGHOST"] = host
    env["PGPORT"] = str(port)
    env["PGUSER"] = user
    env["PGPASSWORD"] = password
    os.environ.update(
        {
            "MAPPO_DATABASE_URL": database_url,
            "DATABASE_URL": database_url,
        }
    )
    subprocess.run(
        [str(repo_root / "backend" / "scripts" / "ensure_db.sh")],
        check=True,
        cwd=repo_root,
        env=env,
    )
    subprocess.run(
        [str(repo_root / "backend" / "scripts" / "flyway.sh"), "clean"],
        check=True,
        cwd=repo_root,
        env=env,
    )
    subprocess.run(
        [str(repo_root / "backend" / "scripts" / "flyway.sh"), "migrate"],
        check=True,
        cwd=repo_root,
        env=env,
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
        asyncio.run(seed_store(app.state.store))
        yield test_client
