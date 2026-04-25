#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE_DEFAULT="${ROOT_DIR}/.data/mappo.env"

subscription_id=""
sp_name=""
env_file="${ENV_FILE_DEFAULT}"
role="Contributor"

usage() {
  cat <<EOF
Usage: $(basename "$0") [options]

Creates Azure service principal credentials for MAPPO and writes an env file.

Options:
  --subscription-id <id>   Subscription scope for role assignment (default: active az account)
  --sp-name <name>         Service principal display name (default: mappo-runtime-<timestamp>)
  --env-file <path>        Output env file path (default: ${ENV_FILE_DEFAULT})
  --role <role>            RBAC role to assign (default: Contributor)
  -h, --help               Show this help
EOF
}

upsert_env_var() {
  local file="$1"
  local key="$2"
  local value="$3"
  mkdir -p "$(dirname "${file}")"
  "${ROOT_DIR}/scripts/run_tooling.sh" \
    azure-script-support upsert-export-line \
    --env-file "${file}" \
    --key "${key}" \
    --value "${value}" \
    >/dev/null
  chmod 600 "${file}"
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --subscription-id)
      subscription_id="${2:-}"
      shift 2
      ;;
    --sp-name)
      sp_name="${2:-}"
      shift 2
      ;;
    --env-file)
      env_file="${2:-}"
      shift 2
      ;;
    --role)
      role="${2:-}"
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

if ! command -v az >/dev/null 2>&1; then
  echo "azure-auth-bootstrap: Azure CLI is required." >&2
  exit 1
fi

if ! az account show --only-show-errors >/dev/null 2>&1; then
  echo "azure-auth-bootstrap: no active Azure login. Run 'az login' first." >&2
  exit 1
fi

if [[ -z "${subscription_id}" ]]; then
  subscription_id="$(az account show --query id -o tsv)"
fi
tenant_id="$(az account show --query tenantId -o tsv)"

if [[ -z "${sp_name}" ]]; then
  sp_name="mappo-runtime-$(date +%Y%m%d%H%M%S)"
fi

mkdir -p "$(dirname "${env_file}")"

echo "azure-auth-bootstrap: creating service principal '${sp_name}'"
sp_json="$(az ad sp create-for-rbac \
  --name "${sp_name}" \
  --role "${role}" \
  --scopes "/subscriptions/${subscription_id}" \
  --query '{clientId:appId,clientSecret:password,tenantId:tenant}' \
  -o json)"
credential_row="$("${ROOT_DIR}/scripts/run_tooling.sh" \
  azure-script-support sp-credentials \
  --json "${sp_json}")"
IFS=$'\t' read -r client_id client_secret tenant_from_output <<< "${credential_row}"

if [[ -z "${client_id}" || -z "${client_secret}" ]]; then
  echo "azure-auth-bootstrap: failed to retrieve service principal credentials." >&2
  exit 1
fi

if [[ -z "${tenant_from_output}" ]]; then
  tenant_from_output="${tenant_id}"
fi

upsert_env_var "${env_file}" MAPPO_EXECUTION_MODE "azure"
upsert_env_var "${env_file}" MAPPO_AZURE_TENANT_ID "${tenant_from_output}"
upsert_env_var "${env_file}" MAPPO_AZURE_CLIENT_ID "${client_id}"
upsert_env_var "${env_file}" MAPPO_AZURE_CLIENT_SECRET "${client_secret}"

echo "azure-auth-bootstrap: wrote ${env_file}"
echo "azure-auth-bootstrap: service principal clientId=${client_id}"
echo "azure-auth-bootstrap: next step: source ${env_file}"
