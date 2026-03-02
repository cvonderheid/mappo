from __future__ import annotations

from datetime import UTC, datetime


class StoreError(Exception):
    def __init__(self, message: str):
        super().__init__(message)
        self.message = message


def utc_now() -> datetime:
    return datetime.now(tz=UTC)
