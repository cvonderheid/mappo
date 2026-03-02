from __future__ import annotations

from uuid import UUID


def require_guid(value: str, *, field_name: str) -> UUID:
    try:
        return UUID(value)
    except ValueError as error:
        raise ValueError(f"{field_name} must be a valid GUID: {value}") from error


def optional_guid(value: str | None, *, field_name: str) -> UUID | None:
    if value is None:
        return None
    return require_guid(value, field_name=field_name)
