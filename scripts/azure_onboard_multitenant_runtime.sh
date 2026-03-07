#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
INVENTORY_FILE="${ROOT_DIR}/.data/mappo-target-inventory.json"
CLIENT_ID=""
HOME_SUBSCRIPTION_ID=""
TARGET_SUBSCRIPTION_IDS=""
READER_ROLE="Reader"
CONTRIBUTOR_ROLE="Contributor"

usage() {
  cat <<'EOF'
usage: azure_onboard_multitenant_runtime.sh --client-id <app-id> --target-subscriptions "<sub1,sub2,...>" [options]

Ensures a MAPPO runtime app registration can authenticate and operate across multiple tenants/subscriptions:
1) sets app sign-in audience to AzureADMultipleOrgs
2) creates service-principal object in each target tenant
3) assigns Reader at subscription scope (validation/quota checks)
4) assigns Contributor on managed target resource groups from inventory (deploy/update)

Options:
  --client-id <app-id>                 App registration client ID (MAPPO_AZURE_CLIENT_ID)
  --target-subscriptions "<csv>"       Subscription IDs to onboard
  --home-subscription-id <sub-id>      Subscription in app-registration home tenant (default: current)
  --inventory-file <path>              Inventory JSON path (default: .data/mappo-target-inventory.json)
  --reader-role <role>                 Subscription-level role (default: Reader)
  --contributor-role <role>            Resource-group role (default: Contributor)
  -h, --help                           Show help
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --client-id)
      CLIENT_ID="${2:-}"
      shift 2
      ;;
    --target-subscriptions)
      TARGET_SUBSCRIPTION_IDS="${2:-}"
      shift 2
      ;;
    --home-subscription-id)
      HOME_SUBSCRIPTION_ID="${2:-}"
      shift 2
      ;;
    --inventory-file)
      INVENTORY_FILE="${2:-}"
      shift 2
      ;;
    --reader-role)
      READER_ROLE="${2:-}"
      shift 2
      ;;
    --contributor-role)
      CONTRIBUTOR_ROLE="${2:-}"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "azure-onboard-multitenant-runtime: unknown argument: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

if [[ -z "${CLIENT_ID}" ]]; then
  echo "azure-onboard-multitenant-runtime: --client-id is required." >&2
  exit 2
fi
if [[ -z "${TARGET_SUBSCRIPTION_IDS}" ]]; then
  echo "azure-onboard-multitenant-runtime: --target-subscriptions is required." >&2
  exit 2
fi

if ! command -v az >/dev/null 2>&1; then
  echo "azure-onboard-multitenant-runtime: Azure CLI is required." >&2
  exit 2
fi
if ! az account show --only-show-errors >/dev/null 2>&1; then
  echo "azure-onboard-multitenant-runtime: no active Azure login. Run 'az login' first." >&2
  exit 2
fi

if [[ ! -f "${INVENTORY_FILE}" ]]; then
  echo "azure-onboard-multitenant-runtime: inventory file not found: ${INVENTORY_FILE}" >&2
  exit 2
fi

ORIGINAL_SUBSCRIPTION_ID="$(az account show --query id -o tsv)"
if [[ -z "${HOME_SUBSCRIPTION_ID}" ]]; then
  HOME_SUBSCRIPTION_ID="${ORIGINAL_SUBSCRIPTION_ID}"
fi

cleanup() {
  az account set --subscription "${ORIGINAL_SUBSCRIPTION_ID}" >/dev/null 2>&1 || true
}
trap cleanup EXIT

IFS=',' read -r -a TARGET_SUBS <<< "${TARGET_SUBSCRIPTION_IDS}"
TARGET_SUBS_NORMALIZED=()
for sub in "${TARGET_SUBS[@]}"; do
  normalized="$(echo "${sub}" | xargs)"
  if [[ -n "${normalized}" ]]; then
    TARGET_SUBS_NORMALIZED+=("${normalized}")
  fi
done
if [[ ${#TARGET_SUBS_NORMALIZED[@]} -eq 0 ]]; then
  echo "azure-onboard-multitenant-runtime: no valid target subscriptions provided." >&2
  exit 2
fi

echo "azure-onboard-multitenant-runtime: using app ${CLIENT_ID}"
echo "azure-onboard-multitenant-runtime: home subscription ${HOME_SUBSCRIPTION_ID}"

# Ensure app is multi-tenant in its home tenant.
az account set --subscription "${HOME_SUBSCRIPTION_ID}" >/dev/null
az ad app update --id "${CLIENT_ID}" --sign-in-audience AzureADMultipleOrgs >/dev/null
echo "azure-onboard-multitenant-runtime: app signInAudience set to AzureADMultipleOrgs"

ACCOUNT_LIST_JSON="$(az account list --all -o json)"

for sub in "${TARGET_SUBS_NORMALIZED[@]}"; do
  tenant_id="$("${ROOT_DIR}/scripts/run_tooling.sh" \
    azure-script-support subscription-tenant-id \
    --account-list-json "${ACCOUNT_LIST_JSON}" \
    --subscription-id "${sub}")"
  if [[ -z "${tenant_id}" ]]; then
    echo "azure-onboard-multitenant-runtime: subscription not found in az context: ${sub}" >&2
    exit 1
  fi

  echo "azure-onboard-multitenant-runtime: onboarding ${sub} (tenant ${tenant_id})"
  az account set --subscription "${sub}" >/dev/null

  if ! az ad sp show --id "${CLIENT_ID}" --query id -o tsv >/dev/null 2>&1; then
    az ad sp create --id "${CLIENT_ID}" >/dev/null
  fi
  sp_object_id="$(az ad sp show --id "${CLIENT_ID}" --query id -o tsv)"
  if [[ -z "${sp_object_id}" ]]; then
    echo "azure-onboard-multitenant-runtime: failed to resolve service principal object ID in ${tenant_id}" >&2
    exit 1
  fi

  if [[ "$(az role assignment list \
    --assignee-object-id "${sp_object_id}" \
    --scope "/subscriptions/${sub}" \
    --role "${READER_ROLE}" \
    --query 'length(@)' -o tsv)" == "0" ]]; then
    az role assignment create \
      --assignee-object-id "${sp_object_id}" \
      --assignee-principal-type ServicePrincipal \
      --role "${READER_ROLE}" \
      --scope "/subscriptions/${sub}" >/dev/null
  fi

  RG_SCOPES_RAW="$("${ROOT_DIR}/scripts/run_tooling.sh" \
    azure-script-support inventory-rg-scopes \
    --inventory-file "${INVENTORY_FILE}" \
    --subscription-id "${sub}")"

  while IFS= read -r scope; do
    [[ -z "${scope}" ]] && continue
    if [[ "$(az role assignment list \
      --assignee-object-id "${sp_object_id}" \
      --scope "${scope}" \
      --role "${CONTRIBUTOR_ROLE}" \
      --query 'length(@)' -o tsv)" == "0" ]]; then
      az role assignment create \
        --assignee-object-id "${sp_object_id}" \
        --assignee-principal-type ServicePrincipal \
        --role "${CONTRIBUTOR_ROLE}" \
        --scope "${scope}" >/dev/null
    fi
  done <<< "${RG_SCOPES_RAW}"

  echo "azure-onboard-multitenant-runtime: onboarding complete for ${sub}"
done

echo "azure-onboard-multitenant-runtime: complete."
