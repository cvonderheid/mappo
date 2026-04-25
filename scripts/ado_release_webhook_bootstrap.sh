#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

AZURE_ENV_FILE="${ROOT_DIR}/.data/mappo-azure.env"
RUNTIME_ENV_FILE="${ROOT_DIR}/.data/mappo-runtime.env"
ADO_ENV_FILE="${ROOT_DIR}/.data/mappo-ado.env"
ORGANIZATION="${MAPPO_DEMO_ADO_ORGANIZATION:-}"
PROJECT="${MAPPO_DEMO_ADO_PROJECT:-}"
PIPELINE_ID=""
MAPPO_API_BASE_URL="${MAPPO_API_BASE_URL:-}"
ENDPOINT_ID="ado-pipeline-default"
MAPPO_PROJECT_ID="azure-appservice-ado-pipeline"
WEBHOOK_SECRET="${MAPPO_AZURE_DEVOPS_WEBHOOK_SECRET:-}"
RESOURCE_GROUP=""
BACKEND_APP_NAME=""
REPLACE_EXISTING=false
SKIP_HOOK=false
DRY_RUN=false

usage() {
  cat <<EOF
usage: $(basename "$0") [options]

Configure MAPPO's Azure DevOps release-readiness webhook secret in the hosted backend
and optionally create the corresponding Azure DevOps service hook.

Options:
  --azure-env-file <path>       Azure env file (default: .data/mappo-azure.env)
  --runtime-env-file <path>     Runtime env file (default: .data/mappo-runtime.env)
  --ado-env-file <path>         Azure DevOps env file (default: .data/mappo-ado.env)
  --organization <url|name>     Azure DevOps organization (or MAPPO_DEMO_ADO_ORGANIZATION)
  --project <name>              Azure DevOps project (or MAPPO_DEMO_ADO_PROJECT)
  --pipeline-id <id>            ADO release-readiness pipeline ID
  --mappo-api-base-url <url>    MAPPO API base URL (default: runtime env)
  --endpoint-id <id>            MAPPO release source ID (default: ado-pipeline-default)
  --mappo-project-id <id>       MAPPO project ID (default: azure-appservice-ado-pipeline)
  --webhook-secret <secret>     Explicit webhook secret (default: env file value or generate)
  --resource-group <name>       Backend Container App resource group
  --backend-app-name <name>     Backend Container App name
  --replace-existing            Recreate matching ADO service hook subscription
  --skip-hook                   Only configure MAPPO backend secret/env
  --dry-run                     Print actions only; do not mutate files, Azure, or Azure DevOps
  -h, --help                    Show help
EOF
}

upsert_env_var() {
  local file="$1"
  local key="$2"
  local value="$3"
  local escaped_value
  escaped_value="$(printf '%s' "${value}" | sed -e 's/[\/&]/\\&/g')"
  mkdir -p "$(dirname "${file}")"
  touch "${file}"
  if grep -qE "^[[:space:]]*(export[[:space:]]+)?${key}=" "${file}"; then
    sed -i.bak -E "s|^[[:space:]]*(export[[:space:]]+)?${key}=.*$|export ${key}=${escaped_value}|" "${file}"
    rm -f "${file}.bak"
  else
    printf '\nexport %s=%s\n' "${key}" "${value}" >>"${file}"
  fi
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --azure-env-file)
      AZURE_ENV_FILE="${2:-}"
      shift 2
      ;;
    --runtime-env-file)
      RUNTIME_ENV_FILE="${2:-}"
      shift 2
      ;;
    --ado-env-file)
      ADO_ENV_FILE="${2:-}"
      shift 2
      ;;
    --organization)
      ORGANIZATION="${2:-}"
      shift 2
      ;;
    --project)
      PROJECT="${2:-}"
      shift 2
      ;;
    --pipeline-id)
      PIPELINE_ID="${2:-}"
      shift 2
      ;;
    --mappo-api-base-url|--api-base-url)
      MAPPO_API_BASE_URL="${2:-}"
      shift 2
      ;;
    --endpoint-id)
      ENDPOINT_ID="${2:-}"
      shift 2
      ;;
    --mappo-project-id)
      MAPPO_PROJECT_ID="${2:-}"
      shift 2
      ;;
    --webhook-secret)
      WEBHOOK_SECRET="${2:-}"
      shift 2
      ;;
    --resource-group)
      RESOURCE_GROUP="${2:-}"
      shift 2
      ;;
    --backend-app-name)
      BACKEND_APP_NAME="${2:-}"
      shift 2
      ;;
    --replace-existing)
      REPLACE_EXISTING=true
      shift
      ;;
    --skip-hook)
      SKIP_HOOK=true
      shift
      ;;
    --dry-run)
      DRY_RUN=true
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "ado-release-webhook-bootstrap: unknown argument: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

if [[ ! -f "${AZURE_ENV_FILE}" ]]; then
  echo "ado-release-webhook-bootstrap: missing Azure env file: ${AZURE_ENV_FILE}" >&2
  exit 1
fi
if [[ ! -f "${RUNTIME_ENV_FILE}" ]]; then
  echo "ado-release-webhook-bootstrap: missing runtime env file: ${RUNTIME_ENV_FILE}" >&2
  exit 1
fi
if ! command -v az >/dev/null 2>&1; then
  echo "ado-release-webhook-bootstrap: Azure CLI is required." >&2
  exit 1
fi
if ! command -v openssl >/dev/null 2>&1; then
  echo "ado-release-webhook-bootstrap: openssl is required." >&2
  exit 1
fi

set -a
source "${AZURE_ENV_FILE}"
source "${RUNTIME_ENV_FILE}"
if [[ -f "${ADO_ENV_FILE}" ]]; then
  source "${ADO_ENV_FILE}"
fi
set +a

if [[ -z "${RESOURCE_GROUP}" ]]; then
  RESOURCE_GROUP="${MAPPO_RUNTIME_RESOURCE_GROUP:-}"
fi
if [[ -z "${BACKEND_APP_NAME}" ]]; then
  BACKEND_APP_NAME="${MAPPO_RUNTIME_BACKEND_APP:-}"
fi
if [[ -z "${MAPPO_API_BASE_URL}" ]]; then
  MAPPO_API_BASE_URL="${MAPPO_API_BASE_URL:-${MAPPO_RUNTIME_BACKEND_URL:-}}"
fi
if [[ -z "${WEBHOOK_SECRET}" ]]; then
  WEBHOOK_SECRET="${MAPPO_AZURE_DEVOPS_WEBHOOK_SECRET:-}"
fi
if [[ -z "${ORGANIZATION}" ]]; then
  ORGANIZATION="${MAPPO_DEMO_ADO_ORGANIZATION:-}"
fi
if [[ -z "${PROJECT}" ]]; then
  PROJECT="${MAPPO_DEMO_ADO_PROJECT:-}"
fi
if [[ -z "${WEBHOOK_SECRET}" ]]; then
  WEBHOOK_SECRET="$(openssl rand -hex 32)"
fi

if [[ -z "${ORGANIZATION}" || -z "${PROJECT}" ]]; then
  echo "ado-release-webhook-bootstrap: --organization and --project are required unless MAPPO_DEMO_ADO_* env vars are set." >&2
  exit 2
fi
if [[ -z "${RESOURCE_GROUP}" || -z "${BACKEND_APP_NAME}" || -z "${MAPPO_API_BASE_URL}" ]]; then
  echo "ado-release-webhook-bootstrap: runtime env is missing backend app/resource group/API URL." >&2
  exit 1
fi
if [[ "${SKIP_HOOK}" != "true" && -z "${PIPELINE_ID}" ]]; then
  echo "ado-release-webhook-bootstrap: --pipeline-id is required unless --skip-hook is set." >&2
  exit 2
fi

echo "ado-release-webhook-bootstrap: organization=${ORGANIZATION}"
echo "ado-release-webhook-bootstrap: project=${PROJECT}"
echo "ado-release-webhook-bootstrap: endpoint=${ENDPOINT_ID}"
echo "ado-release-webhook-bootstrap: backend_app=${BACKEND_APP_NAME}"

if [[ "${DRY_RUN}" != "true" ]]; then
  upsert_env_var "${ADO_ENV_FILE}" "MAPPO_AZURE_DEVOPS_WEBHOOK_SECRET" "${WEBHOOK_SECRET}"

  az containerapp secret set \
    --name "${BACKEND_APP_NAME}" \
    --resource-group "${RESOURCE_GROUP}" \
    --secrets "azure-devops-webhook-secret=${WEBHOOK_SECRET}" \
    --only-show-errors \
    >/dev/null

  az containerapp update \
    --name "${BACKEND_APP_NAME}" \
    --resource-group "${RESOURCE_GROUP}" \
    --set-env-vars "MAPPO_AZURE_DEVOPS_WEBHOOK_SECRET=secretref:azure-devops-webhook-secret" \
    --only-show-errors \
    >/dev/null

  echo "ado-release-webhook-bootstrap: configured backend Container App secret/env."
else
  echo "ado-release-webhook-bootstrap: dry-run; skipped env file and backend Container App update."
fi

if [[ "${SKIP_HOOK}" == "true" ]]; then
  echo "ado-release-webhook-bootstrap: skip-hook enabled; no Azure DevOps service hook configured."
  exit 0
fi

hook_args=(
  --organization "${ORGANIZATION}"
  --project "${PROJECT}"
  --pipeline-id "${PIPELINE_ID}"
  --mappo-api-base-url "${MAPPO_API_BASE_URL}"
  --endpoint-id "${ENDPOINT_ID}"
  --mappo-project-id "${MAPPO_PROJECT_ID}"
  --webhook-token "${WEBHOOK_SECRET}"
  --ado-env-file "${ADO_ENV_FILE}"
)
if [[ "${REPLACE_EXISTING}" == "true" ]]; then
  hook_args+=(--replace-existing)
fi
if [[ "${DRY_RUN}" == "true" ]]; then
  hook_args+=(--dry-run)
fi

"${ROOT_DIR}/scripts/ado_release_hook_configure.sh" "${hook_args[@]}"
