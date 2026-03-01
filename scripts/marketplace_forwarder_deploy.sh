#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RESOURCE_GROUP=""
LOCATION="eastus"
FUNCTION_APP_NAME=""
STORAGE_ACCOUNT_NAME=""
RUNTIME="python"
RUNTIME_VERSION="3.11"
FUNCTIONS_VERSION="4"
SUBSCRIPTION_ID=""
PACKAGE_ZIP="${ROOT_DIR}/.data/marketplace-forwarder-function.zip"
MAPPO_INGEST_ENDPOINT="${MAPPO_INGEST_ENDPOINT:-}"
MAPPO_API_BASE_URL="${MAPPO_API_BASE_URL:-}"
MAPPO_INGEST_TOKEN="${MAPPO_MARKETPLACE_INGEST_TOKEN:-${MAPPO_INGEST_TOKEN:-}}"
TIMEOUT_SECONDS="15"
RUNTIME_ENV_FILE="${ROOT_DIR}/.data/mappo-runtime.env"

usage() {
  cat <<EOF
Usage: $(basename "$0") [options]

Provision and deploy the MAPPO Marketplace Forwarder Azure Function App.

Options:
  --resource-group <name>       Azure resource group (required)
  --location <region>           Azure region (default: eastus)
  --function-app-name <name>    Function App name (required, globally unique)
  --storage-account <name>      Storage account name (default: auto-derived)
  --subscription-id <id>        Optional subscription context override
  --package-zip <path>          Function zip artifact (default: .data/marketplace-forwarder-function.zip)
  --mappo-ingest-endpoint <url> Full MAPPO ingest endpoint (/api/v1/admin/onboarding/events)
  --mappo-api-base-url <url>    MAPPO API base URL (used when endpoint is omitted)
  --mappo-ingest-token <token>  x-mappo-ingest-token forwarded to MAPPO
  --timeout-seconds <seconds>   Forwarding timeout (default: 15)
  --runtime-env-file <path>     Optional runtime env file fallback (default: .data/mappo-runtime.env)
  -h, --help                    Show help
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --resource-group)
      RESOURCE_GROUP="${2:-}"
      shift 2
      ;;
    --location)
      LOCATION="${2:-}"
      shift 2
      ;;
    --function-app-name)
      FUNCTION_APP_NAME="${2:-}"
      shift 2
      ;;
    --storage-account)
      STORAGE_ACCOUNT_NAME="${2:-}"
      shift 2
      ;;
    --subscription-id)
      SUBSCRIPTION_ID="${2:-}"
      shift 2
      ;;
    --package-zip)
      PACKAGE_ZIP="${2:-}"
      shift 2
      ;;
    --mappo-ingest-endpoint)
      MAPPO_INGEST_ENDPOINT="${2:-}"
      shift 2
      ;;
    --mappo-api-base-url)
      MAPPO_API_BASE_URL="${2:-}"
      shift 2
      ;;
    --mappo-ingest-token)
      MAPPO_INGEST_TOKEN="${2:-}"
      shift 2
      ;;
    --timeout-seconds)
      TIMEOUT_SECONDS="${2:-}"
      shift 2
      ;;
    --runtime-env-file)
      RUNTIME_ENV_FILE="${2:-}"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "marketplace-forwarder-deploy: unknown argument: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

if [[ -z "${MAPPO_INGEST_ENDPOINT}" && -z "${MAPPO_API_BASE_URL}" && -f "${RUNTIME_ENV_FILE}" ]]; then
  set -a
  source "${RUNTIME_ENV_FILE}"
  set +a
  MAPPO_API_BASE_URL="${MAPPO_API_BASE_URL:-${MAPPO_RUNTIME_BACKEND_URL:-}}"
fi

if [[ -z "${RESOURCE_GROUP}" || -z "${FUNCTION_APP_NAME}" ]]; then
  echo "marketplace-forwarder-deploy: --resource-group and --function-app-name are required." >&2
  usage >&2
  exit 2
fi

if [[ ! -f "${PACKAGE_ZIP}" ]]; then
  echo "marketplace-forwarder-deploy: package zip not found: ${PACKAGE_ZIP}" >&2
  echo "Run: ./scripts/marketplace_forwarder_package.sh" >&2
  exit 1
fi

if [[ -z "${MAPPO_INGEST_ENDPOINT}" && -z "${MAPPO_API_BASE_URL}" ]]; then
  echo "marketplace-forwarder-deploy: set --mappo-ingest-endpoint or --mappo-api-base-url." >&2
  exit 1
fi

if ! command -v az >/dev/null 2>&1; then
  echo "marketplace-forwarder-deploy: Azure CLI is required." >&2
  exit 1
fi

if ! az account show --only-show-errors >/dev/null 2>&1; then
  echo "marketplace-forwarder-deploy: no active Azure login. Run 'az login' first." >&2
  exit 1
fi

if [[ -n "${SUBSCRIPTION_ID}" ]]; then
  az account set --subscription "${SUBSCRIPTION_ID}"
fi

az provider register --namespace Microsoft.Storage --wait --only-show-errors >/dev/null
az provider register --namespace Microsoft.Web --wait --only-show-errors >/dev/null

if [[ -z "${STORAGE_ACCOUNT_NAME}" ]]; then
  suffix="$(python3 - <<'PY'
import random
import string
print("".join(random.choice(string.ascii_lowercase + string.digits) for _ in range(6)))
PY
)"
  STORAGE_ACCOUNT_NAME="$(printf '%s' "st${FUNCTION_APP_NAME//-/}${suffix}" | tr '[:upper:]' '[:lower:]' | cut -c1-24)"
fi

echo "marketplace-forwarder-deploy: resource_group=${RESOURCE_GROUP}"
echo "marketplace-forwarder-deploy: location=${LOCATION}"
echo "marketplace-forwarder-deploy: function_app=${FUNCTION_APP_NAME}"
echo "marketplace-forwarder-deploy: storage_account=${STORAGE_ACCOUNT_NAME}"

az group create \
  --name "${RESOURCE_GROUP}" \
  --location "${LOCATION}" \
  --only-show-errors \
  >/dev/null

if ! az storage account show --name "${STORAGE_ACCOUNT_NAME}" --resource-group "${RESOURCE_GROUP}" --only-show-errors >/dev/null 2>&1; then
  az storage account create \
    --name "${STORAGE_ACCOUNT_NAME}" \
    --resource-group "${RESOURCE_GROUP}" \
    --location "${LOCATION}" \
    --sku Standard_LRS \
    --kind StorageV2 \
    --only-show-errors \
    >/dev/null
fi

if ! az functionapp show --name "${FUNCTION_APP_NAME}" --resource-group "${RESOURCE_GROUP}" --only-show-errors >/dev/null 2>&1; then
  az functionapp create \
    --name "${FUNCTION_APP_NAME}" \
    --resource-group "${RESOURCE_GROUP}" \
    --consumption-plan-location "${LOCATION}" \
    --storage-account "${STORAGE_ACCOUNT_NAME}" \
    --functions-version "${FUNCTIONS_VERSION}" \
    --runtime "${RUNTIME}" \
    --runtime-version "${RUNTIME_VERSION}" \
    --os-type Linux \
    --only-show-errors \
    >/dev/null
fi

if [[ -n "${MAPPO_INGEST_ENDPOINT}" ]]; then
  ingest_endpoint_setting="${MAPPO_INGEST_ENDPOINT}"
else
  ingest_endpoint_setting=""
fi

az functionapp config appsettings set \
  --name "${FUNCTION_APP_NAME}" \
  --resource-group "${RESOURCE_GROUP}" \
  --settings \
    MAPPO_INGEST_ENDPOINT="${ingest_endpoint_setting}" \
    MAPPO_API_BASE_URL="${MAPPO_API_BASE_URL}" \
    MAPPO_INGEST_TOKEN="${MAPPO_INGEST_TOKEN}" \
    MAPPO_INGEST_TIMEOUT_SECONDS="${TIMEOUT_SECONDS}" \
  --only-show-errors \
  >/dev/null

az functionapp deployment source config-zip \
  --name "${FUNCTION_APP_NAME}" \
  --resource-group "${RESOURCE_GROUP}" \
  --src "${PACKAGE_ZIP}" \
  --only-show-errors \
  >/dev/null

function_url="https://${FUNCTION_APP_NAME}.azurewebsites.net/api/marketplace/events"
function_key="$(az functionapp keys list \
  --name "${FUNCTION_APP_NAME}" \
  --resource-group "${RESOURCE_GROUP}" \
  --query functionKeys.default \
  -o tsv \
  --only-show-errors)"

echo "marketplace-forwarder-deploy: deployment complete."
echo "marketplace-forwarder-deploy: function_url=${function_url}"
if [[ -n "${function_key}" ]]; then
  echo "marketplace-forwarder-deploy: webhook_url=${function_url}?code=${function_key}"
else
  echo "marketplace-forwarder-deploy: function key not available yet; retry keys list in ~30s."
fi
