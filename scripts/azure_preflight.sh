#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
INVENTORY_PATH="${ROOT_DIR}/.data/mappo-target-inventory.json"
ENV_FILE="${MAPPO_AZURE_ENV_FILE:-${ROOT_DIR}/.data/mappo-azure.env}"

fail_count=0
warn_count=0

pass() {
  echo "PASS: $1"
}

warn() {
  echo "WARN: $1"
  warn_count=$((warn_count + 1))
}

fail() {
  echo "FAIL: $1"
  fail_count=$((fail_count + 1))
}

echo "azure-preflight: checking production-like multi-tenant readiness"

if [[ -f "${ENV_FILE}" ]]; then
  # shellcheck source=/dev/null
  source "${ENV_FILE}"
  pass "Loaded local Azure env file: ${ENV_FILE}"
else
  warn "Local Azure env file not found: ${ENV_FILE}"
fi

if ! command -v az >/dev/null 2>&1; then
  fail "Azure CLI is not installed. Install with 'brew install azure-cli' (macOS)."
else
  pass "Azure CLI is installed."
fi

if command -v az >/dev/null 2>&1; then
  if az account show --only-show-errors >/dev/null 2>&1; then
    account_json="$(az account show --query '{subscriptionId:id,tenantId:tenantId,name:name}' -o json)"
    pass "Azure login is active."
    echo "INFO: active account: ${account_json}"
  else
    fail "No active Azure login context. Run 'az login' and 'az account set --subscription <id>'."
  fi
fi

tenant_count=0
subscription_count=0
if command -v az >/dev/null 2>&1 && az account show --only-show-errors >/dev/null 2>&1; then
  account_list_json="$(az account list --all -o json 2>/dev/null || echo "[]")"
  stats="$(python3 - <<'PY' "${account_list_json}"
import json
import sys

rows = json.loads(sys.argv[1])
if not isinstance(rows, list):
    print("0|0")
    raise SystemExit(0)

subs = len(rows)
tenant_ids = {str(row.get("tenantId", "")) for row in rows if isinstance(row, dict)}
tenant_ids.discard("")
print(f"{len(tenant_ids)}|{subs}")
PY
)"
  tenant_count="$(echo "${stats}" | cut -d'|' -f1)"
  subscription_count="$(echo "${stats}" | cut -d'|' -f2)"
fi

if [[ "${tenant_count}" -ge 2 ]]; then
  pass "Azure context spans multiple tenants (${tenant_count})."
else
  warn "Azure context currently shows ${tenant_count} tenant. Multi-tenant managed app demos usually span 2+ tenants."
fi

if [[ "${subscription_count}" -ge 2 ]]; then
  pass "Azure context spans multiple subscriptions (${subscription_count})."
else
  warn "Only ${subscription_count} subscription detected. You can still demo, but target isolation is limited."
fi

if [[ -n "${MAPPO_AZURE_TENANT_ID:-}" && -n "${MAPPO_AZURE_CLIENT_ID:-}" && -n "${MAPPO_AZURE_CLIENT_SECRET:-}" ]]; then
  pass "MAPPO Azure service principal env vars are set."
else
  fail "Missing MAPPO_AZURE_TENANT_ID / MAPPO_AZURE_CLIENT_ID / MAPPO_AZURE_CLIENT_SECRET."
fi

if [[ -f "${INVENTORY_PATH}" ]]; then
  pass "Target inventory file exists: ${INVENTORY_PATH}"
  inventory_stats="$(python3 - <<'PY' "${INVENTORY_PATH}"
import json
import re
import sys
from pathlib import Path

path = Path(sys.argv[1])
payload = json.loads(path.read_text(encoding="utf-8"))
if not isinstance(payload, list):
    print("invalid")
    raise SystemExit(1)

targets = len(payload)
tenant_ids = {str(row.get("tenant_id", "")) for row in payload if isinstance(row, dict)}
subscription_ids = {str(row.get("subscription_id", "")) for row in payload if isinstance(row, dict)}
tenant_ids.discard("")
subscription_ids.discard("")

managed_meta_count = 0
invalid_target_resource_ids = 0
pattern = re.compile(
    r"^/subscriptions/[^/]+/resourceGroups/[^/]+/providers/Microsoft\.App/containerApps/[^/]+$",
    re.IGNORECASE,
)

for row in payload:
    if not isinstance(row, dict):
        continue
    metadata = row.get("metadata")
    if isinstance(metadata, dict):
        managed_application_id = str(metadata.get("managed_application_id", "")).strip()
        managed_resource_group_id = str(metadata.get("managed_resource_group_id", "")).strip()
        if managed_application_id and managed_resource_group_id:
            managed_meta_count += 1

    target_resource_id = str(row.get("managed_app_id", "")).strip()
    if not pattern.match(target_resource_id):
        invalid_target_resource_ids += 1

print(
    f"{targets}|{len(tenant_ids)}|{len(subscription_ids)}|"
    f"{managed_meta_count}|{invalid_target_resource_ids}"
)
PY
)"
  target_count="$(echo "${inventory_stats}" | cut -d'|' -f1)"
  inventory_tenant_count="$(echo "${inventory_stats}" | cut -d'|' -f2)"
  inventory_subscription_count="$(echo "${inventory_stats}" | cut -d'|' -f3)"
  managed_meta_count="$(echo "${inventory_stats}" | cut -d'|' -f4)"
  invalid_target_resource_ids="$(echo "${inventory_stats}" | cut -d'|' -f5)"

  if [[ "${target_count}" -ge 10 ]]; then
    pass "Inventory has ${target_count} targets."
  else
    warn "Inventory has ${target_count} targets; expected ~10 for demo."
  fi

  if [[ "${inventory_tenant_count}" -ge 2 ]]; then
    pass "Inventory references ${inventory_tenant_count} tenant IDs."
  else
    fail "Inventory references ${inventory_tenant_count} tenant ID. Multi-tenant managed app demo requires 2+ tenant IDs."
  fi

  if [[ "${inventory_subscription_count}" -ge 2 ]]; then
    pass "Inventory references ${inventory_subscription_count} subscription IDs."
  else
    warn "Inventory references ${inventory_subscription_count} subscription ID."
  fi

  if [[ "${invalid_target_resource_ids}" -eq 0 ]]; then
    pass "All inventory managed_app_id values are valid Container App resource IDs."
  else
    fail "Inventory contains ${invalid_target_resource_ids} invalid managed_app_id value(s); expected /subscriptions/.../resourceGroups/.../providers/Microsoft.App/containerApps/..."
  fi

  if [[ "${managed_meta_count}" -eq "${target_count}" && "${target_count}" -gt 0 ]]; then
    pass "Inventory includes managed app metadata for all targets."
  elif [[ "${managed_meta_count}" -gt 0 ]]; then
    warn "Inventory includes managed app metadata for ${managed_meta_count}/${target_count} targets."
  else
    warn "Inventory has no managed app metadata. Run 'make managed-app-discover-targets' for marketplace-style inventory."
  fi
else
  fail "Missing target inventory file at ${INVENTORY_PATH}. Run 'make managed-app-discover-targets'."
fi

echo "azure-preflight: ${fail_count} failure(s), ${warn_count} warning(s)"
if [[ "${fail_count}" -gt 0 ]]; then
  exit 1
fi
