#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RESOURCE_GROUP=""
SUBSCRIPTION_ID=""
RUNTIME_ENV_FILE="${ROOT_DIR}/.data/mappo-runtime.env"
WAIT_FOR_DELETE="false"

usage() {
  cat <<EOF
Usage: $(basename "$0") [options]

Delete MAPPO runtime ACA resource group created by runtime_aca_deploy.sh.

Options:
  --resource-group <name>      Runtime resource group to delete
  --subscription-id <id>       Subscription context override
  --runtime-env-file <path>    Read defaults from runtime env file (default: .data/mappo-runtime.env)
  --wait <bool>                true|false wait for full deletion (default: false)
  -h, --help                   Show help
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --resource-group)
      RESOURCE_GROUP="${2:-}"
      shift 2
      ;;
    --subscription-id)
      SUBSCRIPTION_ID="${2:-}"
      shift 2
      ;;
    --runtime-env-file)
      RUNTIME_ENV_FILE="${2:-}"
      shift 2
      ;;
    --wait)
      WAIT_FOR_DELETE="${2:-}"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "runtime-aca-destroy: unknown argument: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

if [[ -f "${RUNTIME_ENV_FILE}" ]]; then
  set -a
  source "${RUNTIME_ENV_FILE}"
  set +a
  if [[ -z "${RESOURCE_GROUP}" ]]; then
    RESOURCE_GROUP="${MAPPO_RUNTIME_RESOURCE_GROUP:-}"
  fi
  if [[ -z "${SUBSCRIPTION_ID}" ]]; then
    SUBSCRIPTION_ID="${MAPPO_RUNTIME_SUBSCRIPTION_ID:-}"
  fi
fi

if [[ -z "${RESOURCE_GROUP}" ]]; then
  echo "runtime-aca-destroy: resource group is required." >&2
  usage >&2
  exit 2
fi

if ! command -v az >/dev/null 2>&1; then
  echo "runtime-aca-destroy: Azure CLI is required." >&2
  exit 1
fi
if ! az account show --only-show-errors >/dev/null 2>&1; then
  echo "runtime-aca-destroy: no active Azure login context. Run 'az login' first." >&2
  exit 1
fi

if [[ -n "${SUBSCRIPTION_ID}" ]]; then
  az account set --subscription "${SUBSCRIPTION_ID}"
fi

if ! az group show --name "${RESOURCE_GROUP}" --only-show-errors >/dev/null 2>&1; then
  echo "runtime-aca-destroy: resource group does not exist, nothing to delete: ${RESOURCE_GROUP}"
  exit 0
fi

echo "runtime-aca-destroy: deleting resource_group=${RESOURCE_GROUP}"
delete_args=(--name "${RESOURCE_GROUP}" --yes --only-show-errors)
if [[ "${WAIT_FOR_DELETE}" != "true" ]]; then
  delete_args+=(--no-wait)
fi
az group delete "${delete_args[@]}"
echo "runtime-aca-destroy: delete submitted."
