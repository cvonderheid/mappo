from __future__ import annotations

import os
from dataclasses import dataclass
from functools import lru_cache


@dataclass(frozen=True)
class Settings:
    app_name: str = "MAPPO API"
    app_version: str = "0.1.0"
    api_prefix: str = "/api/v1"
    cors_origins: tuple[str, ...] = (
        "http://localhost:5173",
        "http://127.0.0.1:5173",
    )


@lru_cache(maxsize=1)
def get_settings() -> Settings:
    cors_raw = os.getenv("MAPPO_CORS_ORIGINS", "http://localhost:5173,http://127.0.0.1:5173")
    cors_origins = tuple(item.strip() for item in cors_raw.split(",") if item.strip())
    return Settings(
        app_name=os.getenv("MAPPO_APP_NAME", "MAPPO API"),
        app_version=os.getenv("MAPPO_APP_VERSION", "0.1.0"),
        api_prefix=os.getenv("MAPPO_API_PREFIX", "/api/v1"),
        cors_origins=cors_origins,
    )
