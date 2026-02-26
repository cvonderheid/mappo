from __future__ import annotations

import os
from collections.abc import Generator

from sqlalchemy import create_engine
from sqlalchemy.engine import Engine
from sqlalchemy.orm import Session, sessionmaker

DEFAULT_DATABASE_URL = (
    os.getenv("MAPPO_DATABASE_URL")
    or os.getenv("DATABASE_URL")
    or "postgresql+psycopg://txero:txero@localhost:5432/mappo"
)


def create_engine_and_session_factory(
    database_url: str | None = None,
) -> tuple[Engine, sessionmaker[Session]]:
    url = database_url or DEFAULT_DATABASE_URL
    engine = create_engine(url, future=True, pool_pre_ping=True)
    session_factory: sessionmaker[Session] = sessionmaker(
        bind=engine,
        autocommit=False,
        autoflush=False,
        future=True,
    )
    return engine, session_factory


_engine, _session_factory = create_engine_and_session_factory()


def get_db_session() -> Generator[Session, None, None]:
    session = _session_factory()
    try:
        yield session
    finally:
        session.close()
