#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="${ROOT_DIR}/.data/mappo-azure.env"
SUBSCRIPTION_IDS=""

usage() {
  cat <<'EOF'
usage: azure_tenant_map.sh --subscriptions "<sub1,sub2,...>" [--env-file /path/to/env]

Builds MAPPO_AZURE_TENANT_BY_SUBSCRIPTION JSON from the current Azure CLI account context
and writes/updates the export line in the target env file.
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --subscriptions)
      SUBSCRIPTION_IDS="${2:-}"
      shift 2
      ;;
    --env-file)
      ENV_FILE="${2:-}"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "azure-tenant-map: unknown argument: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

if [[ -z "${SUBSCRIPTION_IDS}" ]]; then
  echo "azure-tenant-map: --subscriptions is required." >&2
  usage >&2
  exit 2
fi

if ! command -v az >/dev/null 2>&1; then
  echo "azure-tenant-map: Azure CLI is required." >&2
  exit 2
fi

if ! az account show --only-show-errors >/dev/null 2>&1; then
  echo "azure-tenant-map: no active Azure login. Run 'az login' first." >&2
  exit 2
fi

account_list_json="$(az account list --all -o json)"
tenant_map_json="$("${ROOT_DIR}/scripts/run_tooling.sh" \
  azure-script-support tenant-map-json \
  --subscriptions "${SUBSCRIPTION_IDS}" \
  --account-list-json "${account_list_json}")"

mkdir -p "$(dirname "${ENV_FILE}")"
"${ROOT_DIR}/scripts/run_tooling.sh" \
  azure-script-support upsert-export-line \
  --env-file "${ENV_FILE}" \
  --key MAPPO_AZURE_TENANT_BY_SUBSCRIPTION \
  --value "${tenant_map_json}" \
  >/dev/null

echo "azure-tenant-map: wrote ${ENV_FILE}"
echo "export MAPPO_AZURE_TENANT_BY_SUBSCRIPTION='${tenant_map_json}'"
