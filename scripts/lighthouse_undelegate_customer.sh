#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="${MAPPO_AZURE_ENV_FILE:-${ROOT_DIR}/.data/mappo-azure.env}"

customer_subscription_id=""
provider_tenant_id=""
provider_principal_object_id=""
provider_client_id=""
definition_name="mappo-lighthouse-delegation"
definition_id=""
assignment_id=""

usage() {
  cat <<EOF
Usage: $(basename "$0") [options]

Remove Azure Lighthouse delegation from a customer subscription.

Options:
  --customer-subscription-id <id>      Customer subscription to clean up (default: active az account)
  --provider-tenant-id <id>            Provider tenant ID (default: MAPPO_AZURE_TENANT_ID from env file)
  --provider-principal-object-id <id>  Provider principal object ID (preferred)
  --provider-client-id <id>            Provider app/client ID (used to resolve object ID if object ID not supplied)
  --definition-name <name>             Lighthouse offer/definition name (default: mappo-lighthouse-delegation)
  --definition-id <guid>               Explicit registration definition ID override
  --assignment-id <guid>               Explicit registration assignment ID override
  -h, --help                           Show help
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --customer-subscription-id)
      customer_subscription_id="${2:-}"
      shift 2
      ;;
    --provider-tenant-id)
      provider_tenant_id="${2:-}"
      shift 2
      ;;
    --provider-principal-object-id)
      provider_principal_object_id="${2:-}"
      shift 2
      ;;
    --provider-client-id)
      provider_client_id="${2:-}"
      shift 2
      ;;
    --definition-name)
      definition_name="${2:-}"
      shift 2
      ;;
    --definition-id)
      definition_id="${2:-}"
      shift 2
      ;;
    --assignment-id)
      assignment_id="${2:-}"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

if [[ -f "${ENV_FILE}" ]]; then
  # shellcheck source=/dev/null
  source "${ENV_FILE}"
fi

if ! command -v az >/dev/null 2>&1; then
  echo "lighthouse-undelegate-customer: Azure CLI is required." >&2
  exit 1
fi

if ! az account show --only-show-errors >/dev/null 2>&1; then
  echo "lighthouse-undelegate-customer: no active Azure login. Run 'az login' first." >&2
  exit 1
fi

if [[ -z "${customer_subscription_id}" ]]; then
  customer_subscription_id="$(az account show --query id -o tsv)"
fi

if [[ -z "${definition_id}" ]]; then
  if [[ -z "${provider_tenant_id}" ]]; then
    provider_tenant_id="${MAPPO_AZURE_TENANT_ID:-}"
  fi

  if [[ -z "${provider_client_id}" ]]; then
    provider_client_id="${MAPPO_AZURE_CLIENT_ID:-}"
  fi

  if [[ -z "${provider_principal_object_id}" ]]; then
    if [[ -z "${provider_client_id}" ]]; then
      echo "lighthouse-undelegate-customer: missing provider principal object ID and provider client ID." >&2
      echo "lighthouse-undelegate-customer: pass --definition-id/--assignment-id directly or provide provider identity args." >&2
      exit 1
    fi
    provider_principal_object_id="$(az ad sp show --id "${provider_client_id}" --query id -o tsv)"
  fi

  if [[ -z "${provider_tenant_id}" ]]; then
    echo "lighthouse-undelegate-customer: provider tenant ID is required." >&2
    exit 1
  fi

  if [[ -z "${provider_principal_object_id}" ]]; then
    echo "lighthouse-undelegate-customer: provider principal object ID is required." >&2
    exit 1
  fi

  definition_id="$(python3 - <<'PY' "${customer_subscription_id}" "${provider_tenant_id}" "${provider_principal_object_id}" "${definition_name}"
import sys
import uuid

key = f"mappo:lighthouse:def:{sys.argv[1]}:{sys.argv[2]}:{sys.argv[3]}:{sys.argv[4]}"
print(uuid.uuid5(uuid.NAMESPACE_URL, key))
PY
)"
fi

if [[ -z "${assignment_id}" ]]; then
  assignment_id="$(python3 - <<'PY' "${definition_id}"
import sys
import uuid

key = f"mappo:lighthouse:assignment:{sys.argv[1]}"
print(uuid.uuid5(uuid.NAMESPACE_URL, key))
PY
)"
fi

echo "lighthouse-undelegate-customer: customer_subscription_id=${customer_subscription_id}"
echo "lighthouse-undelegate-customer: definition_id=${definition_id}"
echo "lighthouse-undelegate-customer: assignment_id=${assignment_id}"

assignment_exists="false"
if az managedservices assignment show \
  --assignment "${assignment_id}" \
  --subscription "${customer_subscription_id}" \
  --only-show-errors >/dev/null 2>&1; then
  assignment_exists="true"
fi

if [[ "${assignment_exists}" == "true" ]]; then
  az managedservices assignment delete \
    --assignment "${assignment_id}" \
    --subscription "${customer_subscription_id}" \
    --yes \
    --only-show-errors \
    -o none
  echo "lighthouse-undelegate-customer: deleted registration assignment."
else
  echo "lighthouse-undelegate-customer: assignment not found; skipping delete."
fi

definition_exists="false"
if az managedservices definition show \
  --definition "${definition_id}" \
  --subscription "${customer_subscription_id}" \
  --only-show-errors >/dev/null 2>&1; then
  definition_exists="true"
fi

if [[ "${definition_exists}" == "true" ]]; then
  az managedservices definition delete \
    --definition "${definition_id}" \
    --subscription "${customer_subscription_id}" \
    --yes \
    --only-show-errors \
    -o none
  echo "lighthouse-undelegate-customer: deleted registration definition."
else
  echo "lighthouse-undelegate-customer: definition not found; skipping delete."
fi

echo "lighthouse-undelegate-customer: completed."
