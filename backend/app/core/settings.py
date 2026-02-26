from __future__ import annotations

import os
from dataclasses import dataclass
from functools import lru_cache


@dataclass(frozen=True)
class Settings:
    app_name: str = "MAPPO API"
    app_version: str = "0.1.0"
    api_prefix: str = "/api/v1"
    database_url: str = "postgresql+psycopg://txero:txero@localhost:5432/mappo"
    retention_days: int = 90
    cors_origins: tuple[str, ...] = (
        "http://localhost:5173",
        "http://127.0.0.1:5173",
        "http://localhost:5174",
        "http://127.0.0.1:5174",
    )


def _parse_int_env(value: str | None, *, default: int, minimum: int) -> int:
    if value is None or value.strip() == "":
        return default
    parsed = int(value)
    return max(parsed, minimum)


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
        or "postgresql+psycopg://txero:txero@localhost:5432/mappo"
    )

    return Settings(
        app_name=os.getenv("MAPPO_APP_NAME", "MAPPO API"),
        app_version=os.getenv("MAPPO_APP_VERSION", "0.1.0"),
        api_prefix=os.getenv("MAPPO_API_PREFIX", "/api/v1"),
        database_url=database_url,
        retention_days=retention_days,
        cors_origins=cors_origins,
    )
