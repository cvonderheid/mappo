#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="${MAPPO_AZURE_ENV_FILE:-${ROOT_DIR}/.data/mappo-azure.env}"

DEFAULT_ROLE_DEFINITION_ID="b24988ac-6180-42a0-ab88-20f7382dd24c" # Contributor

customer_subscription_id=""
provider_tenant_id=""
provider_principal_object_id=""
provider_client_id=""
definition_name="mappo-lighthouse-delegation"
description="MAPPO provider delegation for managed deployments"
role_definition_id="${DEFAULT_ROLE_DEFINITION_ID}"

usage() {
  cat <<EOF
Usage: $(basename "$0") [options]

Create Azure Lighthouse delegation from a customer subscription to MAPPO provider principal.

Options:
  --customer-subscription-id <id>      Customer subscription to delegate (default: active az account)
  --provider-tenant-id <id>            Provider tenant ID (default: MAPPO_AZURE_TENANT_ID from env file)
  --provider-principal-object-id <id>  Provider principal object ID (preferred)
  --provider-client-id <id>            Provider app/client ID (used to resolve object ID if object ID not supplied)
  --definition-name <name>             Lighthouse offer/definition name
  --description <text>                 Definition description
  --role-definition-id <guid>          Customer role definition GUID (default: Contributor)
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
    --description)
      description="${2:-}"
      shift 2
      ;;
    --role-definition-id)
      role_definition_id="${2:-}"
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
  echo "lighthouse-delegate-customer: Azure CLI is required." >&2
  exit 1
fi

if ! az account show --only-show-errors >/dev/null 2>&1; then
  echo "lighthouse-delegate-customer: no active Azure login. Run 'az login' first." >&2
  exit 1
fi

if [[ -z "${customer_subscription_id}" ]]; then
  customer_subscription_id="$(az account show --query id -o tsv)"
fi

if [[ -z "${provider_tenant_id}" ]]; then
  provider_tenant_id="${MAPPO_AZURE_TENANT_ID:-}"
fi

if [[ -z "${provider_client_id}" ]]; then
  provider_client_id="${MAPPO_AZURE_CLIENT_ID:-}"
fi

if [[ -z "${provider_principal_object_id}" ]]; then
  if [[ -z "${provider_client_id}" ]]; then
    echo "lighthouse-delegate-customer: missing provider principal object ID and provider client ID." >&2
    exit 1
  fi
  provider_principal_object_id="$(az ad sp show --id "${provider_client_id}" --query id -o tsv)"
fi

if [[ -z "${provider_tenant_id}" ]]; then
  echo "lighthouse-delegate-customer: provider tenant ID is required." >&2
  exit 1
fi

if [[ -z "${provider_principal_object_id}" ]]; then
  echo "lighthouse-delegate-customer: provider principal object ID is required." >&2
  exit 1
fi

definition_id="$(python3 - <<'PY' "${customer_subscription_id}" "${provider_tenant_id}" "${provider_principal_object_id}" "${definition_name}"
import sys
import uuid

key = f"mappo:lighthouse:def:{sys.argv[1]}:{sys.argv[2]}:{sys.argv[3]}:{sys.argv[4]}"
print(uuid.uuid5(uuid.NAMESPACE_URL, key))
PY
)"
assignment_id="$(python3 - <<'PY' "${definition_id}"
import sys
import uuid

key = f"mappo:lighthouse:assignment:{sys.argv[1]}"
print(uuid.uuid5(uuid.NAMESPACE_URL, key))
PY
)"

echo "lighthouse-delegate-customer: customer_subscription_id=${customer_subscription_id}"
echo "lighthouse-delegate-customer: provider_tenant_id=${provider_tenant_id}"
echo "lighthouse-delegate-customer: provider_principal_object_id=${provider_principal_object_id}"
echo "lighthouse-delegate-customer: definition_id=${definition_id}"
echo "lighthouse-delegate-customer: assignment_id=${assignment_id}"

definition_exists="false"
if az managedservices definition show \
  --definition "${definition_id}" \
  --subscription "${customer_subscription_id}" \
  --only-show-errors >/dev/null 2>&1; then
  definition_exists="true"
fi

if [[ "${definition_exists}" == "true" ]]; then
  echo "lighthouse-delegate-customer: definition already exists; skipping create."
else
  az managedservices definition create \
    --subscription "${customer_subscription_id}" \
    --definition-id "${definition_id}" \
    --name "${definition_name}" \
    --description "${description}" \
    --tenant-id "${provider_tenant_id}" \
    --principal-id "${provider_principal_object_id}" \
    --role-definition-id "${role_definition_id}" \
    --only-show-errors \
    -o none
  echo "lighthouse-delegate-customer: created registration definition."
fi

assignment_exists="false"
if az managedservices assignment show \
  --assignment "${assignment_id}" \
  --subscription "${customer_subscription_id}" \
  --only-show-errors >/dev/null 2>&1; then
  assignment_exists="true"
fi

if [[ "${assignment_exists}" == "true" ]]; then
  echo "lighthouse-delegate-customer: assignment already exists; skipping create."
else
  definition_resource_id="/subscriptions/${customer_subscription_id}/providers/Microsoft.ManagedServices/registrationDefinitions/${definition_id}"
  az managedservices assignment create \
    --subscription "${customer_subscription_id}" \
    --assignment-id "${assignment_id}" \
    --definition "${definition_resource_id}" \
    --only-show-errors \
    -o none
  echo "lighthouse-delegate-customer: created registration assignment."
fi

echo "lighthouse-delegate-customer: completed."
