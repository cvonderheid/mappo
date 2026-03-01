#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CLIENT_ID=""
RESOURCE_GROUP=""
FRONTEND_APP_NAME=""
SUBSCRIPTION_ID=""
EASYAUTH_ENV_FILE="${ROOT_DIR}/.data/mappo-easyauth.env"
RUNTIME_ENV_FILE="${ROOT_DIR}/.data/mappo-runtime.env"
DELETE_ENV_FILE="true"
YES="false"

usage() {
  cat <<EOF
Usage: $(basename "$0") [options]

Delete EasyAuth app registration and optionally disable frontend Container App auth.

Options:
  --client-id <guid>              EasyAuth app registration client ID (default from env file)
  --resource-group <name>         Frontend app resource group (default from runtime env)
  --frontend-app-name <name>      Frontend Container App name (default from runtime env)
  --subscription-id <id>          Subscription for frontend app
  --easyauth-env-file <path>      EasyAuth env file (default: .data/mappo-easyauth.env)
  --runtime-env-file <path>       Runtime env file (default: .data/mappo-runtime.env)
  --delete-env-file <bool>        Delete EasyAuth env file after cleanup (default: true)
  --yes                           Do not prompt
  -h, --help                      Show help
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --client-id)
      CLIENT_ID="${2:-}"
      shift 2
      ;;
    --resource-group)
      RESOURCE_GROUP="${2:-}"
      shift 2
      ;;
    --frontend-app-name)
      FRONTEND_APP_NAME="${2:-}"
      shift 2
      ;;
    --subscription-id)
      SUBSCRIPTION_ID="${2:-}"
      shift 2
      ;;
    --easyauth-env-file)
      EASYAUTH_ENV_FILE="${2:-}"
      shift 2
      ;;
    --runtime-env-file)
      RUNTIME_ENV_FILE="${2:-}"
      shift 2
      ;;
    --delete-env-file)
      DELETE_ENV_FILE="${2:-}"
      shift 2
      ;;
    --yes)
      YES="true"
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "azure-cleanup-easyauth: unknown argument: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

if ! command -v az >/dev/null 2>&1; then
  echo "azure-cleanup-easyauth: Azure CLI is required." >&2
  exit 1
fi
if ! az account show --only-show-errors >/dev/null 2>&1; then
  echo "azure-cleanup-easyauth: no active Azure login. Run 'az login' first." >&2
  exit 1
fi

if [[ -f "${EASYAUTH_ENV_FILE}" ]]; then
  set -a
  # shellcheck disable=SC1090
  source "${EASYAUTH_ENV_FILE}"
  set +a
fi
if [[ -f "${RUNTIME_ENV_FILE}" ]]; then
  set -a
  # shellcheck disable=SC1090
  source "${RUNTIME_ENV_FILE}"
  set +a
fi

if [[ -z "${CLIENT_ID}" ]]; then
  CLIENT_ID="${MAPPO_EASYAUTH_CLIENT_ID:-}"
fi
if [[ -z "${RESOURCE_GROUP}" ]]; then
  RESOURCE_GROUP="${MAPPO_RUNTIME_RESOURCE_GROUP:-}"
fi
if [[ -z "${FRONTEND_APP_NAME}" ]]; then
  FRONTEND_APP_NAME="${MAPPO_RUNTIME_FRONTEND_APP:-}"
fi

if [[ -n "${SUBSCRIPTION_ID}" ]]; then
  az account set --subscription "${SUBSCRIPTION_ID}"
fi

if [[ "${YES}" != "true" ]]; then
  echo "azure-cleanup-easyauth: client_id=${CLIENT_ID:-<none>}"
  echo "azure-cleanup-easyauth: resource_group=${RESOURCE_GROUP:-<none>}"
  echo "azure-cleanup-easyauth: frontend_app=${FRONTEND_APP_NAME:-<none>}"
  read -r -p "Continue? [y/N]: " reply
  if [[ ! "${reply}" =~ ^[Yy]$ ]]; then
    echo "azure-cleanup-easyauth: cancelled."
    exit 0
  fi
fi

if [[ -n "${RESOURCE_GROUP}" && -n "${FRONTEND_APP_NAME}" ]]; then
  if az containerapp show --name "${FRONTEND_APP_NAME}" --resource-group "${RESOURCE_GROUP}" --only-show-errors >/dev/null 2>&1; then
    echo "azure-cleanup-easyauth: disabling frontend EasyAuth on ${FRONTEND_APP_NAME}"
    az containerapp auth update \
      --name "${FRONTEND_APP_NAME}" \
      --resource-group "${RESOURCE_GROUP}" \
      --enabled false \
      --only-show-errors \
      >/dev/null || true
  fi
fi

if [[ -n "${CLIENT_ID}" ]]; then
  if az ad app show --id "${CLIENT_ID}" --only-show-errors >/dev/null 2>&1; then
    az ad app delete --id "${CLIENT_ID}" --only-show-errors >/dev/null
    echo "azure-cleanup-easyauth: deleted app registration ${CLIENT_ID}"
  else
    echo "azure-cleanup-easyauth: app registration not found ${CLIENT_ID}"
  fi
else
  echo "azure-cleanup-easyauth: no client ID provided; skipped app registration delete."
fi

if [[ "${DELETE_ENV_FILE,,}" == "true" ]]; then
  if [[ -f "${EASYAUTH_ENV_FILE}" ]]; then
    rm -f "${EASYAUTH_ENV_FILE}"
    echo "azure-cleanup-easyauth: removed env file ${EASYAUTH_ENV_FILE}"
  fi
fi

echo "azure-cleanup-easyauth: complete."
