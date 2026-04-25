#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ADO_ENV_FILE="${ROOT_DIR}/.data/mappo.env"
ORGANIZATION="${MAPPO_DEMO_ADO_ORGANIZATION:-}"
PROJECT="${MAPPO_DEMO_ADO_PROJECT:-}"
PIPELINE_ID=""
MAPPO_API_BASE_URL="${MAPPO_API_BASE_URL:-}"
ENDPOINT_ID="ado-pipeline-default"
PROJECT_ID="azure-appservice-ado-pipeline"
WEBHOOK_TOKEN="${MAPPO_AZURE_DEVOPS_WEBHOOK_SECRET:-}"
ADO_PAT="${AZURE_DEVOPS_EXT_PAT:-}"
REPLACE_EXISTING=false
DRY_RUN=false

usage() {
  cat <<EOF
usage: $(basename "$0") [options]

Create the Azure DevOps service hook that tells MAPPO an App Service demo release is ready.

Options:
  --organization <url|name>      Azure DevOps organization (or MAPPO_DEMO_ADO_ORGANIZATION)
  --project <name>               Azure DevOps project (or MAPPO_DEMO_ADO_PROJECT)
  --pipeline-id <id>             ADO release-readiness pipeline ID
  --mappo-api-base-url <url>     MAPPO API base URL (default: MAPPO_API_BASE_URL)
  --endpoint-id <id>             MAPPO release source ID (default: ado-pipeline-default)
  --mappo-project-id <id>        MAPPO project ID (default: azure-appservice-ado-pipeline)
  --webhook-token <value>        Webhook token expected by MAPPO (default: MAPPO_AZURE_DEVOPS_WEBHOOK_SECRET)
  --ado-pat <value>              Azure DevOps PAT (default: AZURE_DEVOPS_EXT_PAT or .data/mappo.env)
  --ado-env-file <path>          Optional env file to source first (default: .data/mappo.env)
  --replace-existing             Delete matching hook subscriptions before creating a new one
  --dry-run                      Print planned operations without calling Azure DevOps
  -h, --help                     Show help
EOF
}

normalize_org() {
  local raw="${1:-}"
  raw="${raw%/}"
  if [[ "${raw}" == https://* ]]; then
    echo "${raw}"
  elif [[ "${raw}" == dev.azure.com/* ]]; then
    echo "https://${raw}"
  else
    echo "https://dev.azure.com/${raw}"
  fi
}

urlencode() {
  jq -rn --arg value "$1" '$value|@uri'
}

curl_ado() {
  local method="$1"
  local url="$2"
  local body="${3:-}"
  if [[ "${DRY_RUN}" == "true" ]]; then
    echo "ado-release-hook-configure: ${method} ${url}" >&2
    if [[ -n "${body}" ]]; then
      echo "${body}" | jq 'if .consumerInputs.url then .consumerInputs.url |= sub("token=[^&]*"; "token=<redacted>") else . end' >&2
    fi
    echo '{}'
    return
  fi
  if [[ -n "${body}" ]]; then
    curl --fail-with-body --silent --show-error \
      -u ":${ADO_PAT}" \
      -X "${method}" \
      -H "content-type: application/json" \
      --data "${body}" \
      "${url}"
  else
    curl --fail-with-body --silent --show-error \
      -u ":${ADO_PAT}" \
      -X "${method}" \
      "${url}"
  fi
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
      PROJECT_ID="${2:-}"
      shift 2
      ;;
    --webhook-token)
      WEBHOOK_TOKEN="${2:-}"
      shift 2
      ;;
    --ado-pat)
      ADO_PAT="${2:-}"
      shift 2
      ;;
    --ado-env-file)
      ADO_ENV_FILE="${2:-}"
      shift 2
      ;;
    --replace-existing)
      REPLACE_EXISTING=true
      shift 1
      ;;
    --dry-run)
      DRY_RUN=true
      shift 1
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "ado-release-hook-configure: unknown argument: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

if [[ -f "${ADO_ENV_FILE}" ]]; then
  # shellcheck disable=SC1090
  source "${ADO_ENV_FILE}"
  ADO_PAT="${ADO_PAT:-${AZURE_DEVOPS_EXT_PAT:-}}"
  WEBHOOK_TOKEN="${WEBHOOK_TOKEN:-${MAPPO_AZURE_DEVOPS_WEBHOOK_SECRET:-}}"
  ORGANIZATION="${ORGANIZATION:-${MAPPO_DEMO_ADO_ORGANIZATION:-}}"
  PROJECT="${PROJECT:-${MAPPO_DEMO_ADO_PROJECT:-}}"
fi

if [[ -z "${ORGANIZATION}" || -z "${PROJECT}" ]]; then
  echo "ado-release-hook-configure: --organization and --project are required unless MAPPO_DEMO_ADO_* env vars are set." >&2
  exit 2
fi
ORGANIZATION="$(normalize_org "${ORGANIZATION}")"

if ! command -v jq >/dev/null 2>&1; then
  echo "ado-release-hook-configure: jq is required." >&2
  exit 1
fi
if ! command -v curl >/dev/null 2>&1; then
  echo "ado-release-hook-configure: curl is required." >&2
  exit 1
fi
if [[ -z "${PIPELINE_ID}" ]]; then
  echo "ado-release-hook-configure: --pipeline-id is required." >&2
  exit 2
fi
if [[ -z "${MAPPO_API_BASE_URL}" ]]; then
  echo "ado-release-hook-configure: --mappo-api-base-url (or MAPPO_API_BASE_URL) is required." >&2
  exit 2
fi
if [[ -z "${WEBHOOK_TOKEN}" ]]; then
  echo "ado-release-hook-configure: --webhook-token (or MAPPO_AZURE_DEVOPS_WEBHOOK_SECRET) is required." >&2
  exit 2
fi
if [[ "${DRY_RUN}" != "true" && -z "${ADO_PAT}" ]]; then
  echo "ado-release-hook-configure: --ado-pat or AZURE_DEVOPS_EXT_PAT is required." >&2
  exit 2
fi

webhook_url="${MAPPO_API_BASE_URL%/}/api/v1/release-ingest/endpoints/${ENDPOINT_ID}/webhooks/ado?projectId=$(urlencode "${PROJECT_ID}")&token=$(urlencode "${WEBHOOK_TOKEN}")"
project_json="$(curl_ado GET "${ORGANIZATION}/_apis/projects/$(urlencode "${PROJECT}")?api-version=7.1-preview.4")"
ado_project_id="$(jq -r '.id // empty' <<<"${project_json}")"
if [[ "${DRY_RUN}" == "true" ]]; then
  ado_project_id="00000000-0000-0000-0000-000000000000"
fi
if [[ -z "${ado_project_id}" ]]; then
  echo "ado-release-hook-configure: Azure DevOps project not found: ${PROJECT}" >&2
  exit 1
fi

hooks_url="${ORGANIZATION}/_apis/hooks/subscriptions?api-version=7.1"
hooks_json="$(curl_ado GET "${hooks_url}")"
matching_hook_ids="$(
  jq -r \
    --arg pipelineId "${PIPELINE_ID}" \
    --arg endpointPath "/api/v1/release-ingest/endpoints/${ENDPOINT_ID}/webhooks/ado" \
    '.value[]? | select(.publisherId == "pipelines")
      | select(.eventType == "ms.vss-pipelines.run-state-changed-event")
      | select((.publisherInputs.pipelineId // "") == $pipelineId)
      | select((.consumerInputs.url // "") | contains($endpointPath))
      | .id' <<<"${hooks_json}"
)"

if [[ -n "${matching_hook_ids}" && "${REPLACE_EXISTING}" != "true" ]]; then
  echo "ado-release-hook-configure: matching service hook already exists:"
  echo "${matching_hook_ids}"
  echo "ado-release-hook-configure: re-run with --replace-existing to recreate it."
  exit 0
fi

if [[ -n "${matching_hook_ids}" && "${REPLACE_EXISTING}" == "true" ]]; then
  while IFS= read -r hook_id; do
    [[ -z "${hook_id}" ]] && continue
    curl_ado DELETE "${ORGANIZATION}/_apis/hooks/subscriptions/${hook_id}?api-version=7.1" >/dev/null
    echo "ado-release-hook-configure: deleted existing hook ${hook_id}"
  done <<<"${matching_hook_ids}"
fi

body="$(jq -n \
  --arg projectId "${ado_project_id}" \
  --arg pipelineId "${PIPELINE_ID}" \
  --arg url "${webhook_url}" \
  '{
    publisherId: "pipelines",
    eventType: "ms.vss-pipelines.run-state-changed-event",
    resourceVersion: "5.1-preview.1",
    consumerId: "webHooks",
    consumerActionId: "httpRequest",
    publisherInputs: {
      projectId: $projectId,
      pipelineId: $pipelineId,
      runStateId: "Completed",
      runResultId: "Succeeded"
    },
    consumerInputs: {
      url: $url,
      resourceDetailsToSend: "all",
      messagesToSend: "all",
      detailedMessagesToSend: "all"
    }
  }')"

created="$(curl_ado POST "${hooks_url}" "${body}")"
created_id="$(jq -r '.id // "dry-run"' <<<"${created}")"
echo "ado-release-hook-configure: created service hook ${created_id}"
echo "ado-release-hook-configure: webhook_url=${MAPPO_API_BASE_URL%/}/api/v1/release-ingest/endpoints/${ENDPOINT_ID}/webhooks/ado?projectId=${PROJECT_ID}&token=<redacted>"
