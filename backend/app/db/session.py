from __future__ import annotations

import os
from collections.abc import Generator
from typing import cast

from sqlalchemy import create_engine
from sqlalchemy.engine import Engine
from sqlalchemy.orm import Session, sessionmaker
from starlette.requests import Request


def _default_database_url() -> str:
    return "postgresql+psycopg://mappo:mappo@localhost:5433/mappo"


DEFAULT_DATABASE_URL = (
    os.getenv("MAPPO_DATABASE_URL")
    or os.getenv("DATABASE_URL")
    or _default_database_url()
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


def get_db_session(request: Request) -> Generator[Session, None, None]:
    session_factory = cast(sessionmaker[Session], request.app.state.db_session_factory)
    session = session_factory()
    try:
        yield session
    finally:
        session.close()
