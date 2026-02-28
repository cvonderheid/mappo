from __future__ import annotations

import os
import socket
from dataclasses import dataclass
from functools import lru_cache

from app.modules.execution import ExecutionMode


@dataclass(frozen=True)
class Settings:
    app_name: str = "MAPPO API"
    app_version: str = "0.1.0"
    api_prefix: str = "/api/v1"
    database_url: str = "postgresql+psycopg://mappo:mappo@localhost:5433/mappo"
    retention_days: int = 90
    execution_mode: ExecutionMode = ExecutionMode.AZURE
    azure_tenant_id: str | None = None
    azure_client_id: str | None = None
    azure_client_secret: str | None = None
    cors_origins: tuple[str, ...] = (
        "http://localhost:5173",
        "http://127.0.0.1:5173",
        "http://localhost:5174",
        "http://127.0.0.1:5174",
    )


def _port_is_open(host: str, port: int) -> bool:
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as sock:
        sock.settimeout(0.15)
        return sock.connect_ex((host, port)) == 0


def _default_database_url() -> str:
    if _port_is_open("127.0.0.1", 5433):
        return "postgresql+psycopg://mappo:mappo@localhost:5433/mappo"
    return "postgresql+psycopg://mappo:mappo@localhost:5432/mappo"


def _parse_int_env(value: str | None, *, default: int, minimum: int) -> int:
    if value is None or value.strip() == "":
        return default
    parsed = int(value)
    return max(parsed, minimum)


def _parse_execution_mode(value: str | None) -> ExecutionMode:
    if value is None or value.strip() == "":
        return ExecutionMode.AZURE
    normalized = value.strip().lower()
    try:
        return ExecutionMode(normalized)
    except ValueError as error:
        raise ValueError("MAPPO_EXECUTION_MODE must be one of: demo, azure") from error


@lru_cache(maxsize=1)
def get_settings() -> Settings:
    cors_raw = os.getenv(
        "MAPPO_CORS_ORIGINS",
        "http://localhost:5173,http://127.0.0.1:5173,http://localhost:5174,http://127.0.0.1:5174",
    )
    cors_origins = tuple(item.strip() for item in cors_raw.split(",") if item.strip())
    retention_days = _parse_int_env(os.getenv("MAPPO_RETENTION_DAYS"), default=90, minimum=1)
    database_url = (
        os.getenv("MAPPO_DATABASE_URL")
        or os.getenv("DATABASE_URL")
        or _default_database_url()
    )
    execution_mode = _parse_execution_mode(os.getenv("MAPPO_EXECUTION_MODE"))

    return Settings(
        app_name=os.getenv("MAPPO_APP_NAME", "MAPPO API"),
        app_version=os.getenv("MAPPO_APP_VERSION", "0.1.0"),
        api_prefix=os.getenv("MAPPO_API_PREFIX", "/api/v1"),
        database_url=database_url,
        retention_days=retention_days,
        execution_mode=execution_mode,
        azure_tenant_id=os.getenv("MAPPO_AZURE_TENANT_ID"),
        azure_client_id=os.getenv("MAPPO_AZURE_CLIENT_ID"),
        azure_client_secret=os.getenv("MAPPO_AZURE_CLIENT_SECRET"),
        cors_origins=cors_origins,
    )
