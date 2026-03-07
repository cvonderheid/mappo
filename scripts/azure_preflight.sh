#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
INVENTORY_PATH="${MAPPO_TARGET_INVENTORY_PATH:-${ROOT_DIR}/.data/mappo-target-inventory.json}"
ENV_FILE="${MAPPO_AZURE_ENV_FILE:-${ROOT_DIR}/.data/mappo-azure.env}"
EXPECTED_TARGET_COUNT="${MAPPO_PREFLIGHT_EXPECTED_TARGET_COUNT:-2}"
PREFLIGHT_MODE="${MAPPO_PREFLIGHT_MODE:-marketplace}"

fail_count=0
warn_count=0
strict_inventory_checks=false

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

inventory_issue() {
  local message="$1"
  if [[ "${strict_inventory_checks}" == "true" ]]; then
    fail "${message}"
  else
    warn "${message}"
  fi
}

case "${PREFLIGHT_MODE}" in
  marketplace)
    strict_inventory_checks=false
    ;;
  inventory)
    strict_inventory_checks=true
    ;;
  *)
    warn "Unknown MAPPO_PREFLIGHT_MODE='${PREFLIGHT_MODE}', defaulting to marketplace."
    PREFLIGHT_MODE="marketplace"
    strict_inventory_checks=false
    ;;
esac

echo "azure-preflight: checking production-like multi-tenant readiness (mode=${PREFLIGHT_MODE})"

if ! [[ "${EXPECTED_TARGET_COUNT}" =~ ^[0-9]+$ ]] || [[ "${EXPECTED_TARGET_COUNT}" -lt 1 ]]; then
  warn "Invalid MAPPO_PREFLIGHT_EXPECTED_TARGET_COUNT='${EXPECTED_TARGET_COUNT}'; defaulting to 2."
  EXPECTED_TARGET_COUNT=2
fi

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
  stats="$("${ROOT_DIR}/scripts/run_tooling.sh" \
    azure-script-support account-stats \
    --account-list-json "${account_list_json}")"
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

tenant_map_parse_error="$("${ROOT_DIR}/scripts/run_tooling.sh" \
  azure-script-support validate-tenant-map \
  --raw "${MAPPO_AZURE_TENANT_BY_SUBSCRIPTION:-}")"
if [[ -n "${tenant_map_parse_error}" ]]; then
  fail "MAPPO_AZURE_TENANT_BY_SUBSCRIPTION parse error: ${tenant_map_parse_error}"
elif [[ -n "${MAPPO_AZURE_TENANT_BY_SUBSCRIPTION:-}" ]]; then
  pass "MAPPO_AZURE_TENANT_BY_SUBSCRIPTION format looks valid."
elif [[ "${tenant_count}" -ge 2 ]]; then
  warn "MAPPO_AZURE_TENANT_BY_SUBSCRIPTION not set; cross-tenant runtime may fail for non-home subscriptions."
fi

if [[ -n "${MAPPO_MARKETPLACE_INGEST_TOKEN:-}" ]]; then
  pass "MAPPO_MARKETPLACE_INGEST_TOKEN is set (onboarding endpoint can be token-gated)."
else
  warn "MAPPO_MARKETPLACE_INGEST_TOKEN is not set; onboarding endpoint is currently unauthenticated."
fi

if [[ -f "${INVENTORY_PATH}" ]]; then
  pass "Target inventory file exists: ${INVENTORY_PATH}"
  inventory_stats="$("${ROOT_DIR}/scripts/run_tooling.sh" \
    azure-script-support inventory-stats \
    --inventory-path "${INVENTORY_PATH}")"
  target_count="$(echo "${inventory_stats}" | cut -d'|' -f1)"
  inventory_tenant_count="$(echo "${inventory_stats}" | cut -d'|' -f2)"
  inventory_subscription_count="$(echo "${inventory_stats}" | cut -d'|' -f3)"
  managed_meta_count="$(echo "${inventory_stats}" | cut -d'|' -f4)"
  invalid_target_resource_ids="$(echo "${inventory_stats}" | cut -d'|' -f5)"

  if [[ "${target_count}" -ge "${EXPECTED_TARGET_COUNT}" ]]; then
    pass "Inventory has ${target_count} targets."
  else
    warn "Inventory has ${target_count} targets; expected >=${EXPECTED_TARGET_COUNT} for this demo profile."
  fi

  if [[ "${inventory_tenant_count}" -ge 2 ]]; then
    pass "Inventory references ${inventory_tenant_count} tenant IDs."
  else
    inventory_issue "Inventory references ${inventory_tenant_count} tenant ID. Multi-tenant managed app demo requires 2+ tenant IDs."
  fi

  if [[ "${inventory_subscription_count}" -ge 2 ]]; then
    pass "Inventory references ${inventory_subscription_count} subscription IDs."
  else
    warn "Inventory references ${inventory_subscription_count} subscription ID."
  fi

  if [[ "${invalid_target_resource_ids}" -eq 0 ]]; then
    pass "All inventory managed_app_id values are valid Container App resource IDs."
  else
    inventory_issue "Inventory contains ${invalid_target_resource_ids} invalid managed_app_id value(s); expected /subscriptions/.../resourceGroups/.../providers/Microsoft.App/containerApps/..."
  fi

  if [[ "${managed_meta_count}" -eq "${target_count}" && "${target_count}" -gt 0 ]]; then
    pass "Inventory includes managed app metadata for all targets."
  elif [[ "${managed_meta_count}" -gt 0 ]]; then
    warn "Inventory includes managed app metadata for ${managed_meta_count}/${target_count} targets."
  else
    warn "Inventory has no managed app metadata. Re-export inventory from IaC and re-import targets."
  fi

  tenant_resolution_stats="$("${ROOT_DIR}/scripts/run_tooling.sh" \
    azure-script-support tenant-resolution-stats \
    --inventory-path "${INVENTORY_PATH}" \
    --raw-map "${MAPPO_AZURE_TENANT_BY_SUBSCRIPTION:-}")"
  unresolved_tenant_count="$(echo "${tenant_resolution_stats}" | cut -d'|' -f1)"
  unresolved_tenant_missing_count="$(echo "${tenant_resolution_stats}" | cut -d'|' -f2)"
  unresolved_tenant_sample="$(echo "${tenant_resolution_stats}" | cut -d'|' -f3)"
  unresolved_tenant_missing_sample="$(echo "${tenant_resolution_stats}" | cut -d'|' -f4)"
  tenant_map_parse_error="$(echo "${tenant_resolution_stats}" | cut -d'|' -f5)"

  if [[ -n "${tenant_map_parse_error}" ]]; then
    fail "MAPPO_AZURE_TENANT_BY_SUBSCRIPTION parse error: ${tenant_map_parse_error}"
  fi

  if [[ "${unresolved_tenant_count}" -eq 0 ]]; then
    pass "Inventory tenant IDs appear authoritative (GUID format)."
  else
    warn "Inventory has ${unresolved_tenant_count} subscription(s) with non-GUID tenant IDs."
    if [[ "${unresolved_tenant_missing_count}" -eq 0 ]]; then
      pass "MAPPO_AZURE_TENANT_BY_SUBSCRIPTION covers all unresolved subscription tenant mappings."
    else
      inventory_issue "Missing tenant mapping for ${unresolved_tenant_missing_count} subscription(s): ${unresolved_tenant_missing_sample:-${unresolved_tenant_sample}}"
    fi
  fi
else
  if [[ "${strict_inventory_checks}" == "true" ]]; then
    fail "Missing target inventory file at ${INVENTORY_PATH}. Export with 'cd infra/pulumi && pulumi stack output --stack <stack> mappoTargetInventory --json > ${INVENTORY_PATH}'."
  else
    pass "No inventory file found at ${INVENTORY_PATH}; marketplace mode expects webhook/event-driven target registration."
  fi
fi

echo "azure-preflight: ${fail_count} failure(s), ${warn_count} warning(s)"
if [[ "${fail_count}" -gt 0 ]]; then
  exit 1
fi
