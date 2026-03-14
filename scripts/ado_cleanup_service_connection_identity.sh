#!/usr/bin/env bash
set -euo pipefail

ORGANIZATION=""
PROJECT=""
SERVICE_CONNECTION_NAME=""
SERVICE_CONNECTION_ID=""
SERVICE_HOOK_ID=""
SERVICE_HOOK_URL_CONTAINS="/api/v1/admin/releases/webhooks/ado"
SKIP_SERVICE_HOOK_CLEANUP="false"
RBAC_SUBSCRIPTIONS=""
APP_HOME_SUBSCRIPTION_ID=""
DELETE_APP_REGISTRATION="false"
REQUIRE_SERVICE_HOOK_CLEANUP="false"
ADO_PAT="${AZURE_DEVOPS_EXT_PAT:-}"
YES="false"

usage() {
  cat <<EOF
usage: $(basename "$0") [options]

Safely clean up Azure DevOps service-connection identity artifacts in this order:
1) service hook subscription(s)
2) service connection
3) Azure RBAC assignments for the service principal
4) tenant-local service principal(s)
5) optional app registration delete

By default this script is dry-run only. Pass --yes to execute.

Options:
  --organization <url|name>          ADO organization (e.g. https://dev.azure.com/pg123 or pg123)
  --project <name>                   ADO project name
  --service-connection-name <name>   Service connection name (default lookup key)
  --service-connection-id <id>       Service connection id (optional exact match)
  --service-hook-id <id>             Exact service hook subscription id to delete
  --service-hook-url-contains <text> Filter service hooks by consumer URL substring
                                      (default: /api/v1/admin/releases/webhooks/ado)
  --skip-service-hook-cleanup        Skip service hook deletion entirely
  --rbac-subscriptions "<csv>"       Subscriptions for RBAC/SP cleanup (default: endpoint subscription)
  --app-home-subscription-id <id>    Subscription context used for app registration delete
  --delete-app-registration <bool>   Delete Entra app registration (default: false)
  --require-service-hook-cleanup     Fail if service hook cleanup cannot run
  --ado-pat <value>                  ADO PAT for service-hook REST calls
                                      (default: AZURE_DEVOPS_EXT_PAT env)
  --yes                              Execute destructive operations
  -h, --help                         Show help
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

normalize_org() {
  local raw="${1:-}"
  raw="${raw%/}"
  if [[ "${raw}" == https://* ]]; then
    echo "${raw}"
    return
  fi
  if [[ "${raw}" == dev.azure.com/* ]]; then
    echo "https://${raw}"
    return
  fi
  echo "https://dev.azure.com/${raw}"
}

contains_token() {
  local haystack="${1:-}"
  local needle="${2:-}"
  [[ " ${haystack} " == *" ${needle} "* ]]
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --organization)
      ORGANIZATION="${2:-}"
      shift 2
      ;;
    --project)
      PROJECT="${2:-}"
      shift 2
      ;;
    --service-connection-name)
      SERVICE_CONNECTION_NAME="${2:-}"
      shift 2
      ;;
    --service-connection-id)
      SERVICE_CONNECTION_ID="${2:-}"
      shift 2
      ;;
    --service-hook-id)
      SERVICE_HOOK_ID="${2:-}"
      shift 2
      ;;
    --service-hook-url-contains)
      SERVICE_HOOK_URL_CONTAINS="${2:-}"
      shift 2
      ;;
    --skip-service-hook-cleanup)
      SKIP_SERVICE_HOOK_CLEANUP="true"
      shift 1
      ;;
    --rbac-subscriptions)
      RBAC_SUBSCRIPTIONS="${2:-}"
      shift 2
      ;;
    --app-home-subscription-id)
      APP_HOME_SUBSCRIPTION_ID="${2:-}"
      shift 2
      ;;
    --delete-app-registration)
      DELETE_APP_REGISTRATION="$(parse_bool "${2:-}")"
      if [[ "${DELETE_APP_REGISTRATION}" == "invalid" ]]; then
        echo "ado-cleanup-service-connection-identity: invalid boolean for --delete-app-registration: ${2:-}" >&2
        exit 2
      fi
      shift 2
      ;;
    --require-service-hook-cleanup)
      REQUIRE_SERVICE_HOOK_CLEANUP="true"
      shift 1
      ;;
    --ado-pat)
      ADO_PAT="${2:-}"
      shift 2
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
      echo "ado-cleanup-service-connection-identity: unknown argument: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

if [[ -z "${ORGANIZATION}" || -z "${PROJECT}" ]]; then
  echo "ado-cleanup-service-connection-identity: --organization and --project are required." >&2
  exit 2
fi
if [[ -z "${SERVICE_CONNECTION_NAME}" && -z "${SERVICE_CONNECTION_ID}" ]]; then
  echo "ado-cleanup-service-connection-identity: --service-connection-name or --service-connection-id is required." >&2
  exit 2
fi

ORGANIZATION="$(normalize_org "${ORGANIZATION}")"

if ! command -v az >/dev/null 2>&1; then
  echo "ado-cleanup-service-connection-identity: Azure CLI is required." >&2
  exit 2
fi
if ! command -v jq >/dev/null 2>&1; then
  echo "ado-cleanup-service-connection-identity: jq is required." >&2
  exit 2
fi
if ! command -v curl >/dev/null 2>&1; then
  echo "ado-cleanup-service-connection-identity: curl is required." >&2
  exit 2
fi
if ! az account show --only-show-errors >/dev/null 2>&1; then
  echo "ado-cleanup-service-connection-identity: no active Azure login context. Run 'az login' first." >&2
  exit 2
fi

ORIGINAL_SUBSCRIPTION_ID="$(az account show --query id -o tsv)"
cleanup() {
  az account set --subscription "${ORIGINAL_SUBSCRIPTION_ID}" >/dev/null 2>&1 || true
}
trap cleanup EXIT

ado_project_id="$(az devops project show --organization "${ORGANIZATION}" --project "${PROJECT}" --query id -o tsv 2>/dev/null || true)"

endpoints_json="$(az devops service-endpoint list \
  --organization "${ORGANIZATION}" \
  --project "${PROJECT}" \
  --only-show-errors \
  -o json)"

endpoint_selector='.'
if [[ -n "${SERVICE_CONNECTION_ID}" && -n "${SERVICE_CONNECTION_NAME}" ]]; then
  endpoint_selector='map(select(.id == $id and .name == $name))'
elif [[ -n "${SERVICE_CONNECTION_ID}" ]]; then
  endpoint_selector='map(select(.id == $id))'
else
  endpoint_selector='map(select(.name == $name))'
fi

endpoint_count="$(
  jq -r \
    --arg id "${SERVICE_CONNECTION_ID}" \
    --arg name "${SERVICE_CONNECTION_NAME}" \
    "${endpoint_selector} | length" <<< "${endpoints_json}"
)"
if [[ "${endpoint_count}" != "1" ]]; then
  echo "ado-cleanup-service-connection-identity: expected exactly one matching service connection, found ${endpoint_count}." >&2
  exit 1
fi

endpoint_json="$(
  jq -c \
    --arg id "${SERVICE_CONNECTION_ID}" \
    --arg name "${SERVICE_CONNECTION_NAME}" \
    "${endpoint_selector} | .[0]" <<< "${endpoints_json}"
)"

resolved_endpoint_id="$(jq -r '.id // empty' <<< "${endpoint_json}")"
resolved_endpoint_name="$(jq -r '.name // empty' <<< "${endpoint_json}")"
service_principal_client_id="$(jq -r '.authorization.parameters.serviceprincipalid // .authorization.parameters.servicePrincipalId // empty' <<< "${endpoint_json}")"
service_principal_tenant_id="$(jq -r '.authorization.parameters.tenantid // .authorization.parameters.tenantId // empty' <<< "${endpoint_json}")"
endpoint_subscription_id="$(jq -r '.data.subscriptionId // empty' <<< "${endpoint_json}")"

rbac_subscription_list=()
if [[ -n "${RBAC_SUBSCRIPTIONS}" ]]; then
  IFS=',' read -r -a rbac_raw <<< "${RBAC_SUBSCRIPTIONS}"
  for sub in "${rbac_raw[@]}"; do
    normalized="$(echo "${sub}" | xargs)"
    [[ -z "${normalized}" ]] && continue
    rbac_subscription_list+=("${normalized}")
  done
fi
if [[ ${#rbac_subscription_list[@]} -eq 0 && -n "${endpoint_subscription_id}" ]]; then
  rbac_subscription_list+=("${endpoint_subscription_id}")
fi

hook_ids=()
hook_lookup_state="skipped"
hook_lookup_error=""
if [[ "${SKIP_SERVICE_HOOK_CLEANUP}" != "true" ]]; then
  if [[ -z "${ADO_PAT}" ]]; then
    hook_lookup_state="missing-pat"
    hook_lookup_error="service-hook cleanup requires --ado-pat or AZURE_DEVOPS_EXT_PAT"
    if [[ "${REQUIRE_SERVICE_HOOK_CLEANUP}" == "true" ]]; then
      echo "ado-cleanup-service-connection-identity: ${hook_lookup_error}" >&2
      exit 1
    fi
  else
    hooks_url="${ORGANIZATION}/_apis/hooks/subscriptions?api-version=7.1-preview.1"
    if hooks_json="$(curl -fsS -u ":${ADO_PAT}" "${hooks_url}" 2>/dev/null)"; then
      hook_lookup_state="ok"
      if [[ -n "${SERVICE_HOOK_ID}" ]]; then
        while IFS= read -r value; do
          [[ -z "${value}" ]] && continue
          hook_ids+=("${value}")
        done < <(jq -r --arg id "${SERVICE_HOOK_ID}" '.value[]? | select(.id == $id) | .id' <<< "${hooks_json}")
      elif [[ -n "${SERVICE_HOOK_URL_CONTAINS}" ]]; then
        while IFS= read -r value; do
          [[ -z "${value}" ]] && continue
          hook_ids+=("${value}")
        done < <(jq -r \
          --arg text "${SERVICE_HOOK_URL_CONTAINS}" \
          --arg projectId "${ado_project_id}" \
          --arg projectName "${PROJECT}" \
          '.value[]?
           | select((.consumerInputs.url // "") | contains($text))
           | select(
               ($projectId == "")
               or (.publisherInputs.projectId // "" == $projectId)
               or (.publisherInputs.projectName // "" == $projectName)
               or (.publisherInputs.project // "" == $projectName)
             )
           | .id' <<< "${hooks_json}")
      fi
      if [[ "${REQUIRE_SERVICE_HOOK_CLEANUP}" == "true" && ${#hook_ids[@]} -eq 0 ]]; then
        echo "ado-cleanup-service-connection-identity: no matching service hook subscriptions found." >&2
        exit 1
      fi
    else
      hook_lookup_state="error"
      hook_lookup_error="failed to query service-hook subscriptions"
      if [[ "${REQUIRE_SERVICE_HOOK_CLEANUP}" == "true" ]]; then
        echo "ado-cleanup-service-connection-identity: ${hook_lookup_error}" >&2
        exit 1
      fi
    fi
  fi
fi

if [[ -z "${APP_HOME_SUBSCRIPTION_ID}" && ${#rbac_subscription_list[@]} -gt 0 ]]; then
  APP_HOME_SUBSCRIPTION_ID="${rbac_subscription_list[0]}"
fi

echo "ado-cleanup-service-connection-identity: organization=${ORGANIZATION}"
echo "ado-cleanup-service-connection-identity: project=${PROJECT}"
echo "ado-cleanup-service-connection-identity: service_connection_id=${resolved_endpoint_id}"
echo "ado-cleanup-service-connection-identity: service_connection_name=${resolved_endpoint_name}"
echo "ado-cleanup-service-connection-identity: service_principal_client_id=${service_principal_client_id:-<none>}"
echo "ado-cleanup-service-connection-identity: service_principal_tenant_id=${service_principal_tenant_id:-<none>}"
echo "ado-cleanup-service-connection-identity: service_hook_lookup=${hook_lookup_state}"
if [[ -n "${hook_lookup_error}" ]]; then
  echo "ado-cleanup-service-connection-identity: service_hook_lookup_note=${hook_lookup_error}"
fi
if [[ "${SKIP_SERVICE_HOOK_CLEANUP}" == "true" ]]; then
  echo "ado-cleanup-service-connection-identity: service_hook_cleanup=skipped-by-flag"
else
  echo "ado-cleanup-service-connection-identity: service_hooks_to_delete=${#hook_ids[@]}"
fi
if [[ ${#rbac_subscription_list[@]} -gt 0 ]]; then
  echo "ado-cleanup-service-connection-identity: rbac_subscriptions=${rbac_subscription_list[*]}"
else
  echo "ado-cleanup-service-connection-identity: rbac_subscriptions=<none>"
fi
echo "ado-cleanup-service-connection-identity: delete_app_registration=${DELETE_APP_REGISTRATION}"
echo "ado-cleanup-service-connection-identity: app_home_subscription_id=${APP_HOME_SUBSCRIPTION_ID:-<none>}"

if [[ "${YES}" != "true" ]]; then
  echo "ado-cleanup-service-connection-identity: dry-run only. Re-run with --yes to execute."
  exit 0
fi

deleted_hook_count=0
deleted_role_assignment_count=0
deleted_sp_count=0
deleted_app_count=0
warning_count=0

if [[ "${SKIP_SERVICE_HOOK_CLEANUP}" != "true" ]]; then
  if [[ "${hook_lookup_state}" == "ok" ]]; then
    for hook_id in "${hook_ids[@]}"; do
      delete_hook_url="${ORGANIZATION}/_apis/hooks/subscriptions/${hook_id}?api-version=7.1-preview.1"
      if curl -fsS -u ":${ADO_PAT}" -X DELETE "${delete_hook_url}" >/dev/null 2>&1; then
        deleted_hook_count=$((deleted_hook_count + 1))
        echo "ado-cleanup-service-connection-identity: deleted service hook ${hook_id}"
      else
        echo "ado-cleanup-service-connection-identity: warning: failed to delete service hook ${hook_id}" >&2
        warning_count=$((warning_count + 1))
      fi
    done
  elif [[ "${hook_lookup_state}" != "missing-pat" && "${hook_lookup_state}" != "skipped" ]]; then
    echo "ado-cleanup-service-connection-identity: warning: service-hook cleanup skipped due lookup error." >&2
    warning_count=$((warning_count + 1))
  fi
fi

if az devops service-endpoint delete \
  --id "${resolved_endpoint_id}" \
  --organization "${ORGANIZATION}" \
  --project "${PROJECT}" \
  --yes \
  --only-show-errors >/dev/null 2>&1; then
  echo "ado-cleanup-service-connection-identity: deleted service connection ${resolved_endpoint_name} (${resolved_endpoint_id})"
else
  echo "ado-cleanup-service-connection-identity: warning: failed to delete service connection ${resolved_endpoint_id}" >&2
  warning_count=$((warning_count + 1))
fi

if [[ -n "${service_principal_client_id}" ]]; then
  for sub in "${rbac_subscription_list[@]}"; do
    az account set --subscription "${sub}" >/dev/null 2>&1 || {
      echo "ado-cleanup-service-connection-identity: warning: unable to select subscription ${sub}" >&2
      warning_count=$((warning_count + 1))
      continue
    }
    assignment_ids="$(az role assignment list \
      --assignee "${service_principal_client_id}" \
      --scope "/subscriptions/${sub}" \
      --query '[].id' \
      -o tsv 2>/dev/null || true)"
    while IFS= read -r assignment_id; do
      [[ -z "${assignment_id}" ]] && continue
      if az role assignment delete --ids "${assignment_id}" >/dev/null 2>&1; then
        deleted_role_assignment_count=$((deleted_role_assignment_count + 1))
      else
        echo "ado-cleanup-service-connection-identity: warning: failed to delete role assignment ${assignment_id}" >&2
        warning_count=$((warning_count + 1))
      fi
    done <<< "${assignment_ids}"
  done

  processed_tenants=""
  for sub in "${rbac_subscription_list[@]}"; do
    tenant_id="$(az account show --subscription "${sub}" --query tenantId -o tsv 2>/dev/null || true)"
    [[ -z "${tenant_id}" ]] && continue
    if contains_token "${processed_tenants}" "${tenant_id}"; then
      continue
    fi
    processed_tenants="${processed_tenants} ${tenant_id}"
    az account set --subscription "${sub}" >/dev/null 2>&1 || continue
    if az ad sp show --id "${service_principal_client_id}" --only-show-errors >/dev/null 2>&1; then
      if az ad sp delete --id "${service_principal_client_id}" --only-show-errors >/dev/null 2>&1; then
        deleted_sp_count=$((deleted_sp_count + 1))
        echo "ado-cleanup-service-connection-identity: deleted tenant-local service principal in tenant ${tenant_id}"
      else
        echo "ado-cleanup-service-connection-identity: warning: failed to delete tenant-local service principal in tenant ${tenant_id}" >&2
        warning_count=$((warning_count + 1))
      fi
    fi
  done

  if [[ "${DELETE_APP_REGISTRATION}" == "true" ]]; then
    if [[ -z "${APP_HOME_SUBSCRIPTION_ID}" ]]; then
      echo "ado-cleanup-service-connection-identity: warning: cannot delete app registration without app home subscription context." >&2
      warning_count=$((warning_count + 1))
    else
      az account set --subscription "${APP_HOME_SUBSCRIPTION_ID}" >/dev/null 2>&1 || true
      az ad sp delete --id "${service_principal_client_id}" --only-show-errors >/dev/null 2>&1 || true
      if az ad app show --id "${service_principal_client_id}" --only-show-errors >/dev/null 2>&1; then
        if az ad app delete --id "${service_principal_client_id}" --only-show-errors >/dev/null 2>&1; then
          deleted_app_count=$((deleted_app_count + 1))
          echo "ado-cleanup-service-connection-identity: deleted app registration ${service_principal_client_id}"
        else
          echo "ado-cleanup-service-connection-identity: warning: failed to delete app registration ${service_principal_client_id}" >&2
          warning_count=$((warning_count + 1))
        fi
      fi
    fi
  fi
else
  echo "ado-cleanup-service-connection-identity: warning: service principal client id was not present on service connection; RBAC/SP/app cleanup skipped." >&2
  warning_count=$((warning_count + 1))
fi

echo "ado-cleanup-service-connection-identity: deleted_service_hooks=${deleted_hook_count} deleted_role_assignments=${deleted_role_assignment_count} deleted_service_principals=${deleted_sp_count} deleted_app_registrations=${deleted_app_count} warnings=${warning_count}"
echo "ado-cleanup-service-connection-identity: complete."
