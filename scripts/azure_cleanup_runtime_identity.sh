#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE_DEFAULT="${ROOT_DIR}/.data/mappo.env"

CLIENT_ID="${MAPPO_AZURE_CLIENT_ID:-}"
TARGET_SUBSCRIPTION_IDS=""
HOME_SUBSCRIPTION_ID=""
DELETE_APP_REGISTRATION="false"
DELETE_ENV_FILE=""
YES="false"

usage() {
  cat <<EOF
usage: $(basename "$0") --client-id <app-id> --target-subscriptions "<sub1,sub2,...>" [options]

Cleans up MAPPO runtime identity artifacts created for multi-tenant demos:
1) removes role assignments for the runtime service principal under each target subscription
2) deletes tenant-local enterprise app (service principal) objects in each target tenant
3) optionally deletes the home-tenant app registration itself

Options:
  --client-id <app-id>                 Runtime app/client ID (default: MAPPO_AZURE_CLIENT_ID from env)
  --target-subscriptions "<csv>"       Target subscriptions to clean
  --home-subscription-id <sub-id>      Home subscription for optional app-registration delete (default: current)
  --delete-app-registration <bool>     Delete app registration in home tenant (default: false)
  --delete-env-file [path]             Remove env file after cleanup (default path: ${ENV_FILE_DEFAULT})
  --yes                                Required confirmation to execute destructive changes
  -h, --help                           Show help
EOF
}

parse_bool() {
  local value="${1:-}"
  case "${value,,}" in
    1|true|yes|on) echo "true" ;;
    0|false|no|off) echo "false" ;;
    *) echo "invalid" ;;
  esac
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
    --delete-app-registration)
      DELETE_APP_REGISTRATION="$(parse_bool "${2:-}")"
      if [[ "${DELETE_APP_REGISTRATION}" == "invalid" ]]; then
        echo "azure-cleanup-runtime-identity: invalid boolean for --delete-app-registration: ${2:-}" >&2
        exit 2
      fi
      shift 2
      ;;
    --delete-env-file)
      if [[ $# -ge 2 && ! "${2:-}" =~ ^-- ]]; then
        DELETE_ENV_FILE="${2:-}"
        shift 2
      else
        DELETE_ENV_FILE="${ENV_FILE_DEFAULT}"
        shift 1
      fi
      ;;
    --yes)
      YES="true"
      shift 1
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "azure-cleanup-runtime-identity: unknown argument: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

if [[ "${YES}" != "true" ]]; then
  echo "azure-cleanup-runtime-identity: refusing to run without --yes." >&2
  usage >&2
  exit 2
fi

if [[ -z "${CLIENT_ID}" ]]; then
  echo "azure-cleanup-runtime-identity: --client-id (or MAPPO_AZURE_CLIENT_ID) is required." >&2
  exit 2
fi

if [[ -z "${TARGET_SUBSCRIPTION_IDS}" ]]; then
  echo "azure-cleanup-runtime-identity: --target-subscriptions is required." >&2
  exit 2
fi

if ! command -v az >/dev/null 2>&1; then
  echo "azure-cleanup-runtime-identity: Azure CLI is required." >&2
  exit 2
fi

if ! az account show --only-show-errors >/dev/null 2>&1; then
  echo "azure-cleanup-runtime-identity: no active Azure login context. Run 'az login' first." >&2
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
  echo "azure-cleanup-runtime-identity: no valid target subscriptions provided." >&2
  exit 2
fi

echo "azure-cleanup-runtime-identity: client_id=${CLIENT_ID}"
echo "azure-cleanup-runtime-identity: target_subscriptions=${TARGET_SUBS_NORMALIZED[*]}"
echo "azure-cleanup-runtime-identity: delete_app_registration=${DELETE_APP_REGISTRATION}"

deleted_assignment_count=0
deleted_sp_count=0
warning_count=0

for sub in "${TARGET_SUBS_NORMALIZED[@]}"; do
  echo "azure-cleanup-runtime-identity: processing subscription ${sub}"
  az account set --subscription "${sub}" >/dev/null

  sp_object_id="$(az ad sp show --id "${CLIENT_ID}" --query id -o tsv 2>/dev/null || true)"
  if [[ -z "${sp_object_id}" ]]; then
    echo "azure-cleanup-runtime-identity: no tenant-local service principal found in ${sub}; skipping."
    continue
  fi

  assignment_ids="$(az role assignment list \
    --assignee-object-id "${sp_object_id}" \
    --scope "/subscriptions/${sub}" \
    --query '[].id' -o tsv 2>/dev/null || true)"

  while IFS= read -r assignment_id; do
    [[ -z "${assignment_id}" ]] && continue
    if az role assignment delete --ids "${assignment_id}" >/dev/null 2>&1; then
      deleted_assignment_count=$((deleted_assignment_count + 1))
    else
      echo "azure-cleanup-runtime-identity: warning: failed to delete role assignment ${assignment_id}" >&2
      warning_count=$((warning_count + 1))
    fi
  done <<< "${assignment_ids}"

  if az ad sp delete --id "${CLIENT_ID}" >/dev/null 2>&1; then
    deleted_sp_count=$((deleted_sp_count + 1))
    echo "azure-cleanup-runtime-identity: deleted tenant-local service principal in ${sub}"
  else
    echo "azure-cleanup-runtime-identity: warning: failed to delete tenant-local service principal in ${sub}" >&2
    warning_count=$((warning_count + 1))
  fi
done

if [[ "${DELETE_APP_REGISTRATION}" == "true" ]]; then
  echo "azure-cleanup-runtime-identity: deleting home-tenant app registration (subscription ${HOME_SUBSCRIPTION_ID})"
  az account set --subscription "${HOME_SUBSCRIPTION_ID}" >/dev/null

  # Clean up home-tenant SP if it still exists.
  az ad sp delete --id "${CLIENT_ID}" >/dev/null 2>&1 || true

  if az ad app delete --id "${CLIENT_ID}" >/dev/null 2>&1; then
    echo "azure-cleanup-runtime-identity: deleted app registration ${CLIENT_ID}"
  else
    echo "azure-cleanup-runtime-identity: warning: failed to delete app registration ${CLIENT_ID}" >&2
    warning_count=$((warning_count + 1))
  fi
fi

if [[ -n "${DELETE_ENV_FILE}" ]]; then
  if [[ -f "${DELETE_ENV_FILE}" ]]; then
    rm -f "${DELETE_ENV_FILE}"
    echo "azure-cleanup-runtime-identity: removed env file ${DELETE_ENV_FILE}"
  else
    echo "azure-cleanup-runtime-identity: env file not found, skipped: ${DELETE_ENV_FILE}"
  fi
fi

echo "azure-cleanup-runtime-identity: deleted_role_assignments=${deleted_assignment_count} deleted_service_principals=${deleted_sp_count} warnings=${warning_count}"
echo "azure-cleanup-runtime-identity: complete."
