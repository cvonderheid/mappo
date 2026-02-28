from __future__ import annotations

import pytest
from pytest import MonkeyPatch

from app.core import settings as settings_module
from app.modules.execution import normalize_tenant_hint, resolve_tenant_for_subscription


def _load_settings() -> settings_module.Settings:
    settings_module.get_settings.cache_clear()
    try:
        return settings_module.get_settings()
    finally:
        settings_module.get_settings.cache_clear()


def test_resolve_tenant_prefers_subscription_mapping() -> None:
    tenant = resolve_tenant_for_subscription(
        subscription_id="sub-a",
        target_tenant_hint="11111111-1111-1111-1111-111111111111",
        default_tenant_id="22222222-2222-2222-2222-222222222222",
        tenant_by_subscription={
            "sub-a": "33333333-3333-3333-3333-333333333333",
        },
    )
    assert tenant == "33333333-3333-3333-3333-333333333333"


def test_resolve_tenant_uses_target_hint_when_mapped_value_missing() -> None:
    tenant = resolve_tenant_for_subscription(
        subscription_id="sub-a",
        target_tenant_hint="11111111-1111-1111-1111-111111111111",
        default_tenant_id="22222222-2222-2222-2222-222222222222",
        tenant_by_subscription={},
    )
    assert tenant == "11111111-1111-1111-1111-111111111111"


def test_resolve_tenant_ignores_placeholder_target_hint() -> None:
    tenant = resolve_tenant_for_subscription(
        subscription_id="sub-a",
        target_tenant_hint="tenant-002",
        default_tenant_id="22222222-2222-2222-2222-222222222222",
        tenant_by_subscription={},
    )
    assert tenant == "22222222-2222-2222-2222-222222222222"


def test_resolve_tenant_returns_none_without_any_sources() -> None:
    tenant = resolve_tenant_for_subscription(
        subscription_id="sub-a",
        target_tenant_hint="tenant-002",
        default_tenant_id=None,
        tenant_by_subscription={},
    )
    assert tenant is None


def test_normalize_tenant_hint_handles_placeholders() -> None:
    assert normalize_tenant_hint("tenant-001") is None
    assert normalize_tenant_hint("unknown-tenant") is None
    assert normalize_tenant_hint("  ") is None
    assert normalize_tenant_hint("contoso.onmicrosoft.com") == "contoso.onmicrosoft.com"


def test_settings_parses_subscription_tenant_map_json(monkeypatch: MonkeyPatch) -> None:
    monkeypatch.setenv(
        "MAPPO_AZURE_TENANT_BY_SUBSCRIPTION",
        '{"sub-a":"11111111-1111-1111-1111-111111111111"}',
    )
    settings = _load_settings()
    assert settings.azure_tenant_by_subscription == {
        "sub-a": "11111111-1111-1111-1111-111111111111"
    }


def test_settings_parses_subscription_tenant_map_csv(monkeypatch: MonkeyPatch) -> None:
    monkeypatch.setenv(
        "MAPPO_AZURE_TENANT_BY_SUBSCRIPTION",
        (
            "sub-a=11111111-1111-1111-1111-111111111111,"
            "sub-b:22222222-2222-2222-2222-222222222222"
        ),
    )
    settings = _load_settings()
    assert settings.azure_tenant_by_subscription == {
        "sub-a": "11111111-1111-1111-1111-111111111111",
        "sub-b": "22222222-2222-2222-2222-222222222222",
    }


def test_settings_rejects_invalid_subscription_tenant_map(
    monkeypatch: MonkeyPatch,
) -> None:
    monkeypatch.setenv("MAPPO_AZURE_TENANT_BY_SUBSCRIPTION", "sub-a")
    settings_module.get_settings.cache_clear()
    with pytest.raises(ValueError, match="subscription=tenant"):
        settings_module.get_settings()
    settings_module.get_settings.cache_clear()
